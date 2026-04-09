package com.tracy.industry.page.main

import android.Manifest
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tracy.industry.util.BLEManager
import com.tracy.industry.R
import com.tracy.industry.base.BaseBindingActivityKt
import com.tracy.industry.databinding.ActivityMainBinding
import com.tracy.industry.page.adapter.BluetoothAdapter
import com.tracy.industry.page.serial.SerialPortActivity
import com.tracy.industry.page.store.StoreActivity
import com.tracy.industry.socket.UdpSimpleManager
import com.tracy.industry.ui.theme.generateViewModel
import com.tracy.industry.ui.theme.showMessage

class MainActivity : BaseBindingActivityKt<ActivityMainBinding, MainViewModel>() {

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_BLE_PERMISSIONS = 1003

    private lateinit var deviceAdapter: BluetoothAdapter
    private var isDeviceConnected = false

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var udpManager: UdpSimpleManager

    private val bleManager by lazy {
        BLEManager.getInstance()
    }

    override fun createViewModel(): MainViewModel = generateViewModel(MainViewModel::class.java)

    override fun getLayoutResource(): Int = R.layout.activity_main

    override fun onSubCreate() {
        initPermission()

        initListener()

    }

    private fun initListener(){

        mBinding.tvStore.setOnClickListener { startActivity(Intent(this, StoreActivity::class.java)) }

        mBinding.tvSerial.setOnClickListener { startActivity(Intent(this, SerialPortActivity::class.java)) }

        mBinding.tvScan.setOnClickListener {
            if (bleManager.checkBLEAvailable()) {
                // 检查权限
                if (bleManager.checkBLEPermissions()) {
                    // 权限已获取，开始扫描
                    mBinding.tvScan.text = "权限已获取，开始扫描"
                } else {
                    // 申请权限
                    val need = bleManager.getNeedRequestPermissions()
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.toTypedArray() ?: emptyArray()
                    if (need.isEmpty()) {
                        mBinding.tvScan.text = "权限已获取，开始扫描"
                    } else {
                        try {
                            ActivityCompat.requestPermissions(
                                this,
                                need,
                                REQUEST_BLE_PERMISSIONS
                            )
                        } catch (_: IllegalArgumentException) {
                            mBinding.tvScan.text = "权限未获取"
                        }
                    }
                }
            } else {
                // 打开蓝牙设置
                mBinding.tvScan.text = "蓝牙未开启"
            }

            updateDeviceList()

        }

        mBinding.tvWriteChar.setOnClickListener {
            val data = "123456"
            writeCharacteristic?.let { char ->
                // 字符串转字节数组（工业场景可替换为16进制/自定义格式）
                val message = bleManager.writeCharacteristic(char, data.toByteArray())
                mBinding.tvWriteChar.text = message
            } ?: run {
                showMessage("未找到可写特征值")
            }
        }

        mBinding.tvUdp.setOnClickListener {
            udpManager = UdpSimpleManager()
            udpManager.init(object : UdpSimpleManager.UdpCallback{
                override fun onReceive(message: String) {
                    mBinding.tvUdpContent.text = message
                }

            })
            udpManager.sendText("Hello")
        }

//        mBinding.tvTcp.postDelayed({ mBinding.tvTcp.text = "长连接状态:${foreService?.isSocketConnected()}" }, 10000)
    }



    /**
     * 开始准备数据
     */
    private fun prepareData(){
        //创建基础目录
        mModel.createDir()
    }

    private fun updateDeviceList() {
//        val deviceScanCompleted = foreService?.isDeviceScanCompleted() ?: false
//        val deviceList = foreService?.getBLEDeviceList()
//        if (deviceScanCompleted){
//            deviceList?.apply {
//                if (this.size > 0){
//                    deviceAdapter = BluetoothAdapter()
//                    mBinding.rvDeviceList.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
//                    deviceAdapter.list = this
//                    mBinding.rvDeviceList.adapter = deviceAdapter
//                    deviceAdapter.listenerClick = object : BaseBindingAdapterKt.OnItemClickListener<BluetoothDevice>{
//                        override fun onClickItem(view: View, position: Int, data: BluetoothDevice) {
//                            // 连接设备
//                            bleManager.connectDevice(data, object : BLEManager.BLEConnectCallback{
//                                override fun onConnectedState(isConn: Boolean) {
//                                    runOnUiThread {
//                                        if (isConn){
//                                            mBinding.tvDeviceInfo.text = "已连接"
//                                            foreService?.uploadDeviceData("数据上传")
//                                        }
//                                        else{
//                                            mBinding.tvDeviceInfo.text = "未连接"
//                                        }
//                                    }
//                                }
//
//                                override fun onServicesFound(services: List<BluetoothGattService>) {
//
//                                }
//
//                                override fun onCharacteristicData(
//                                    uuid: String,
//                                    data: ByteArray
//                                ) {
//
//                                }
//                            })
//                        }
//                    }
//                }
//            }
//        }
    }

    /**
     * 检测电池优化：若开启，引导用户关闭
     */
    private fun checkBatteryOptimization() {
        // 判断应用是否在电池优化白名单中
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
        if (!isIgnored) {
            // 跳转到电池优化设置页面，引导用户关闭
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun initPermission(){

        checkBatteryOptimization()

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
        } else {
            // Android 12 及以下：按系统版本申请存储权限
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
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
            // 不能用 grantResults[0]，因为你可能一次申请了多个权限，顺序不稳定
            val notiIndex = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
            val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notiIndex >= 0) {
                grantResults.getOrNull(notiIndex) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (!notificationGranted) {
                // 用户拒绝权限，通知无法显示，Service也无法正常运行
                showMessage("用户拒绝了通知权限，前台Service无法显示常驻通知")
            }
            prepareData()
        }
        else if (requestCode == REQUEST_BLE_PERMISSIONS){
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                mBinding.tvScan.text = "权限已获取，开始扫描"
                // 权限获取成功后，重新开始扫描
                updateDeviceList()
            } else {
                showMessage("BLE权限被拒绝，无法扫描设备")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止扫描，释放资源
        bleManager.stopScan()
        udpManager.release()
    }

}
