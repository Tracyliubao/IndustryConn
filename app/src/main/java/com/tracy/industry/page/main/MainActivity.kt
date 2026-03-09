package com.tracy.industry.page.main

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.tracy.industry.util.BLEManager
import com.tracy.industry.R
import com.tracy.industry.base.BaseBindingActivityKt
import com.tracy.industry.base.BaseBindingAdapterKt
import com.tracy.industry.databinding.ActivityMainBinding
import com.tracy.industry.page.adapter.BluetoothAdapter
import com.tracy.industry.service.ForegroundService
import com.tracy.industry.ui.theme.generateViewModel
import com.tracy.industry.ui.theme.showMessage
import com.tracy.industry.util.ConfParams
import com.tracy.industry.util.DebugLog
import com.tracy.industry.util.IndustrialConfigManager
import com.tracy.industry.util.IndustrialTimeUtils
import com.tracy.industry.util.SerialPortUtil

class MainActivity : BaseBindingActivityKt<ActivityMainBinding, MainViewModel>() {

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    private val IMPORT_FILE_REQUEST_CODE = 1002
    private val REQUEST_BLE_PERMISSIONS = 1003

    // 工业设备常见串口路径（测试用，实际根据设备调整）
    private val serialPortPath = "/dev/ttyS1"
    private lateinit var serialPortUtil: SerialPortUtil
    private val TEST_HEX_COMMAND = "01030200648439"

    private var foreService: ForegroundService? = null

    private lateinit var deviceAdapter: BluetoothAdapter
    private var deviceList = mutableListOf<BluetoothDevice>()

    private val bleManager by lazy {
        BLEManager.getInstance(this)
    }

    override fun createViewModel(): MainViewModel = generateViewModel(MainViewModel::class.java)

    override fun getLayoutResource(): Int = R.layout.activity_main

    override fun onSubCreate() {
        initPermission()

        initListener()

        initObserver()
    }

    private fun initListener(){
        mBinding.tvInsert.setOnClickListener {
            mModel.insertDevice()
        }

        mBinding.tvName.setOnClickListener {
            mModel.queryDevice()
        }

        mBinding.tvExport.setOnClickListener {
            // 先导出
            val file = IndustrialConfigManager.getInstance().exportConfigToJson()
            // 再保存
            val result = IndustrialConfigManager.getInstance().saveConfigToFile(file)
            if (result){
                showMessage("导出成功")
            }
            else {
                showMessage("导出失败")
            }
        }

        mBinding.tvImport.setOnClickListener {
            // 打开系统文件选择器，选择JSON配置文件
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json" // 只显示JSON文件
            }
            startActivityForResult(intent, IMPORT_FILE_REQUEST_CODE)
        }

        mBinding.tvCrash.setOnClickListener {
            val str: String? = null
            str!!.length
        }

        mBinding.tvStopCollect.setOnClickListener {
            IndustrialTimeUtils.stopCollectTask()
            ConfParams.getMMKVInstance().encode(ConfParams.KEY_COLLECT_RUNNING, false)
        }

        // 配置串口数据
        // 2. 初始化串口工具类（配置9600波特率，工业默认参数）
        serialPortUtil = SerialPortUtil(
            portPath = serialPortPath,
            baudRate = 9600,
            dataBits = 8,
            stopBits = 1,
            parity = 0
        )

        mBinding.tvSerial.setOnClickListener {
            val isOpen = serialPortUtil.openSerialPort()
            mBinding.tvSerial.text = if (isOpen) "已打开" else "未开启"
        }

        mBinding.tvCloseSerial.setOnClickListener {
            serialPortUtil.closeSerialPort()
            showMessage("已关闭")
        }

        mBinding.tvCheckSerial.setOnClickListener {
            val portConfig = serialPortUtil.getPortConfig()
            mBinding.tvCheckSerial.text = portConfig
        }

        mBinding.tvSend.setOnClickListener {
            val message = serialPortUtil.sendHexCommand(TEST_HEX_COMMAND)
            mBinding.tvSend.text = message
        }

        mBinding.tvReceive.setOnClickListener {
            serialPortUtil.receiveHexData(true) { hexData ->
                runOnUiThread {
                    if (hexData != null) {
                        val temperature = serialPortUtil.parseTemperatureFromHex(hexData)
                        val parseResult = if (temperature != null) {
                            "解析成功：$temperature℃"
                        } else {
                            "解析失败"
                        }
                        mBinding.tvReceive.text = parseResult
                    }
                }
            }
        }

        mBinding.tvRead.setOnClickListener {
            val regValues = serialPortUtil.readHoldingRegisters(deviceAddr = 1, startReg = 0, regCount = 1)
            if (regValues.isNotEmpty()) {
                // 工业场景：温度值=寄存器值/10
                val temperature = regValues[0] / 10.0
                mBinding.tvRead.text = "寄存器原始值：${regValues[0]}，解析温度值：${temperature}℃"
            } else {
                mBinding.tvRead.text = "读寄存器失败"
            }
        }

        mBinding.tvWrite.setOnClickListener {
            val success = serialPortUtil.writeSingleRegister(deviceAddr = 1, regAddr = 1, value = 100)
            mBinding.tvWrite.text = if (success) {
                "寄存器地址：1写入值：100"
            } else {
                "写寄存器失败"
            }
        }

        mBinding.tvState.postDelayed(kotlinx.coroutines.Runnable {
            mBinding.tvState.text = foreService?.getTemperature()
        }, 1000)

        mBinding.tvScan.setOnClickListener {
            if (bleManager.checkBLEAvailable()) {
                // 检查权限
                if (bleManager.checkBLEPermissions()) {
                    // 权限已获取，开始扫描
                    startBLEScan()
                } else {
                    // 申请权限
                    val need = bleManager.getNeedRequestPermissions()
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.toTypedArray() ?: emptyArray()
                    if (need.isEmpty()) {
                        startBLEScan()
                    } else {
                        try {
                            ActivityCompat.requestPermissions(
                                this,
                                need,
                                REQUEST_BLE_PERMISSIONS
                            )
                        } catch (_: IllegalArgumentException) {
                            showMessage("权限列表为空或包含非法项，已跳过并开始扫描")
                            startBLEScan()
                        }
                    }
                }
            } else {
                // 打开蓝牙设置
                bleManager.openBluetoothSettings()
                showMessage("请先开启蓝牙")
            }
        }
    }

    private fun initObserver(){
        mModel.deviceData.observe(this){
            it?.apply {
                mBinding.tvName.text = this.deviceName
            }
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            DebugLog.e("LoginService bind success")
            foreService = (service as ForegroundService.MyBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            DebugLog.e()
        }
    }

    /**
     * 开始准备数据
     */
    private fun prepareData(){

        //创建基础目录
        mModel.createDir()

        // 启动前台Service
        startForegroundService()
    }

    // 启动前台Service的方法
    private fun startForegroundService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        // Android O+ 启动前台Service要用startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bindService(Intent(this, ForegroundService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    private fun startBLEScan() {
        bleManager.startScan(
            filterName = null, // 移除名称过滤，扫周边所有蓝牙设备
            callback = object : BLEManager.BLEScanCallback {
                override fun onDeviceFound(device: android.bluetooth.BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
                    // 更新UI（必须在主线程）
                    runOnUiThread {
                        val name = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                    device.name ?: "未知"
                                } else {
                                    "未知"
                                }
                            } else {
                                device.name ?: "未知"
                            }
                        } catch (_: SecurityException) {
                            "未知"
                        }
                        val addr = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                    device.address
                                } else {
                                    "未知"
                                }
                            } else {
                                device.address
                            }
                        } catch (_: SecurityException) {
                            "未知"
                        }
                        val deviceInfo = "名称：$name\n地址：$addr\n信号：$rssi dBm\n\n"
                        deviceList.add(device)
                    }
                }

                override fun onScanStart() {
                    DebugLog.e("扫描中...")
                }

                override fun onScanStop() {
                    if (deviceList.size > 0){
                        deviceAdapter = BluetoothAdapter()
                        mBinding.rvDeviceList.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
                        deviceAdapter.list = deviceList
                        mBinding.rvDeviceList.adapter = deviceAdapter
                        deviceAdapter.listenerClick = object : BaseBindingAdapterKt.OnItemClickListener<BluetoothDevice>{
                            override fun onClickItem(view: View, position: Int, data: BluetoothDevice) {
                                // 连接设备
                                bleManager.connectDevice(data, object : BLEManager.BLEConnectCallback{
                                    override fun onConnectedState(message: String) {
                                        showMessage(message)
//                                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }
                        }
                    }
                }

                override fun onScanError(errorCode: Int) {
                    DebugLog.e("扫描失败：$errorCode")
                }
            }
        )
    }

    private fun initPermission(){
        // 1. 检查并申请所有必要权限
        val permissionsToRequest = mutableListOf<String>()

        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 检查存储读取权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 检查存储写入权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // 有需要申请的权限
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // 所有权限都已授予
            prepareData()
        }
    }

    /**
     * 处理权限申请结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户授予权限，启动Service
                startForegroundService()
            } else {
                // 用户拒绝权限，通知无法显示，Service也无法正常运行
                showMessage("用户拒绝了通知权限，前台Service无法显示常驻通知")
            }
        }
        else if (requestCode == REQUEST_BLE_PERMISSIONS){
            val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                // 权限授予，开始扫描
                startBLEScan()
            } else {
                showMessage("BLE权限被拒绝，无法扫描设备")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 将URI转为文件路径（适配Android 10+分区存储）
                val filePath = getFilePathFromUri(uri)
                if (filePath.isNullOrEmpty()) {
                    showMessage("无法获取文件路径")
                    return
                }

                // 调用导入方法
                val importResult = IndustrialConfigManager.getInstance().importConfigFromFile(filePath)
                when (importResult) {
                    is IndustrialConfigManager.ImportResult.Success -> {
                        showMessage("导入成功")
                        // 刷新UI，显示导入后的配置
                        refreshConfigUI()
                    }
                    else -> {
                        showMessage("导入失败")
                    }
                }
            }
        }
    }

    // URI转文件路径（关键：适配Android 10+）
    private fun getFilePathFromUri(uri: Uri): String? {
        if ("file".equals(uri.scheme, true)) {
            return uri.path
        }
        if (!"content".equals(uri.scheme, true)) {
            return null
        }
        val resolver = contentResolver
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
        } ?: "import.json"
        val dir = java.io.File(com.tracy.industry.util.ConfParams.DIR_CONFIG)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val outFile = java.io.File(dir, name)
        resolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: return null
        return outFile.absolutePath
    }

    // 刷新配置UI（示例：显示导入后的IP、端口等）
    private fun refreshConfigUI() {
        mBinding.tvContent.text = "${IndustrialConfigManager.getInstance().getDeviceIp()}\t" +
                "${IndustrialConfigManager.getInstance().getDevicePort()}\t" +
                "${IndustrialConfigManager.getInstance().getPlcType()}"
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortUtil.closeSerialPort()
        // 结束后台服务
        stopService(Intent(this, ForegroundService::class.java))
        unbindService(serviceConnection)
        // 停止扫描，释放资源
        bleManager.stopScan()
    }

}
