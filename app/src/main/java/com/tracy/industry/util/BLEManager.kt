package com.tracy.industry.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.tracy.industry.base.MyApplication
import java.util.UUID

class BLEManager private constructor(private val context: Context) {

    // BLE核心对象
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null // GATT连接对象

    // 扫描状态
    private var isScanning = false
    // 连接状态标记
    private var isConnecting = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val connectHandler = Handler(Looper.getMainLooper())
    // 扫描超时（默认10秒）
    private val SCAN_TIMEOUT = 5000L
    // 连接超时（15秒）
    private val CONNECT_TIMEOUT = 5000L

    // 回调接口
    interface BLEScanCallback {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?)
        fun onScanStart()
        fun onScanStop()
        fun onScanError(errorCode: Int)
    }

    interface BLEConnectCallback{
        fun onConnectedState(message: String)
        fun onServicesFound(services: List<BluetoothGattService>)
        fun onCharacteristicData(uuid: String, data: ByteArray)
    }

    private var scanCallback: BLEScanCallback? = null
    private var connectCallback: BLEConnectCallback? = null

    // 通用服务/特征值UUID（工业设备通用，可替换为设备专属UUID）
    val UUID_GENERIC_ACCESS = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val UUID_GENERIC_ATTRIBUTE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val UUID_DEVICE_NAME = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

    // 单例模式
    companion object {
        @Volatile
        private var INSTANCE: BLEManager? = null

        fun getInstance(): BLEManager {
            return INSTANCE ?: synchronized(this) {
                val instance = BLEManager(MyApplication.instance)
                INSTANCE = instance
                instance
            }
        }

        const val PROPERTY_READ = 0x02 // 可读
        const val PROPERTY_WRITE = 0x08 // 可写
        const val PROPERTY_NOTIFY = 0x10 // 可通知（实时推送）
    }

    // ========== 1. 初始化检查 ==========
    /**
     * 检查BLE是否可用
     * @return true=可用，false=不可用
     */
    fun checkBLEAvailable(): Boolean {
        // 修改：注释掉「必须支持BLE硬件」的校验（部分平板检测不准，但实际能扫描）
        // if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        //     Log.e(TAG, "设备不支持BLE")
        //     return false
        // }
        // 检查蓝牙是否开启
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            DebugLog.e("蓝牙未开启，请开启蓝牙")
            return false
        }
        return true
    }

    // ========== 2. 权限申请 ==========
    /**
     * 检查并申请BLE扫描所需权限
     * @return true=权限已获取，false=需要申请
     */
    fun checkBLEPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        // Android 12+ 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // Android 12以下需要位置权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // 后台扫描需要后台位置权限（可选）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        return if (permissions.isNotEmpty()) {
            // 需要申请权限（需在Activity中处理）
            false
        } else {
            // 权限已获取
            true
        }
    }

    /**
     * 获取需要申请的权限列表（供Activity调用）
     */
    fun getNeedRequestPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return permissions.toTypedArray()
    }

    // ========== 3. 设备扫描 ==========
    /**
     * 开始扫描BLE设备
     * @param filterName 过滤设备名称（可选，比如"IndustrialSensor"）
     * @param filterUuid 过滤服务UUID（可选，工业设备专属UUID）
     * @param callback 扫描回调
     */
    @SuppressLint("MissingPermission")
    fun startScan(filterName: String? = null, filterUuid: String? = null, callback: BLEScanCallback) {
        // 检查扫描状态
        if (isScanning) {
            DebugLog.e("正在扫描中，无需重复启动")
            return
        }

        // 检查BLE可用性
        if (!checkBLEAvailable()) {
            callback.onScanError(-1)
            return
        }

        // 检查权限
        if (!checkBLEPermissions()) {
            DebugLog.e("BLE权限未获取，无法扫描")
            callback.onScanError(-2)
            return
        }

        this.scanCallback = callback
        isScanning = true
        callback.onScanStart()

        // 初始化扫描器
        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            callback.onScanError(-3)
            isScanning = false
            return
        }

        // 构建扫描过滤器（工业场景：只扫指定设备）
        val scanFilters = mutableListOf<ScanFilter>()
        // 过滤设备名称
        filterName?.let {
            scanFilters.add(ScanFilter.Builder().setDeviceName(it).build())
        }
        // 过滤服务UUID（工业设备专属）
        filterUuid?.let {
            // 示例：UUID转ParcelUuid
            // val parcelUuid = ParcelUuid.fromString(it)
            // scanFilters.add(ScanFilter.Builder().setServiceUuid(parcelUuid).build())
        }

        // 构建扫描设置（低功耗优先）
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // 低功耗模式
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 匹配模式：激进（优先发现设备）
            .build()

        // 开始扫描
        bleScanner?.startScan(scanFilters, scanSettings, bleScanCallback)

        // 扫描超时停止
        scanHandler.postDelayed({
            if (isScanning) {
                stopScan()
            }
        }, SCAN_TIMEOUT)

        DebugLog.e("BLE扫描已启动，超时时间：${SCAN_TIMEOUT/1000}秒")
    }

    /**
     * 停止扫描
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return

        isScanning = false
        scanHandler.removeCallbacksAndMessages(null)
        bleScanner?.stopScan(bleScanCallback)
        scanCallback?.onScanStop()
        DebugLog.e("BLE扫描已停止")
    }

    // ========== 内部扫描回调 ==========
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (!isScanning) return

            // 回调到上层
            scanCallback?.onDeviceFound(
                result.device,
                result.rssi,
                result.scanRecord?.bytes
            )

            // 打印设备信息（工业场景：设备名称/地址/RSSI）
            DebugLog.e("发现BLE设备：")
//            DebugLog.e("  名称：${result.device.name ?: "未知"}")
            DebugLog.e("  地址：${result.device.address}")
            DebugLog.e("  信号强度：${result.rssi}dBm")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach {
                scanCallback?.onDeviceFound(it.device, it.rssi, it.scanRecord?.bytes)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            DebugLog.e("BLE扫描失败，错误码：$errorCode")
            scanCallback?.onScanError(errorCode)

            // 错误码解析
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已启动"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持该功能"
                SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                else -> "未知错误"
            }
           DebugLog.e("错误描述：$errorMsg")
        }
    }

    // ========== 辅助方法 ==========
    /**
     * 打开蓝牙设置页面
     */
    fun openBluetoothSettings() {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice, callback: BLEConnectCallback) {
        // 透传连接阶段回调
        this.connectCallback = callback
        isConnecting = true
        // 关闭旧连接
        bluetoothGatt?.close()

        // 发起连接（false=不自动重连，工业场景推荐手动控制）
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback(){
            // 连接状态变化（核心）
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                val device = gatt.device

                when (newState) {
                    // 连接成功
                    BluetoothProfile.STATE_CONNECTED -> {
                        DebugLog.e("设备连接成功：${device.address}")
                        connectCallback?.onConnectedState("设备连接成功：${device.address}")
                        isConnecting = false
                        // 必须调用：发现设备服务（连接成功后唯一入口）
                        gatt.discoverServices()
                    }

                    // 断开连接
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        DebugLog.e("设备断开连接：${device.address}，状态码：$status")
                        connectCallback?.onConnectedState("设备断开连接：${device.address}，状态码：$status")
                        isConnecting = false

                        // 工业场景可选：非主动断开则自动重连
                        // if (status != 0) {
                        //     connectHandler.postDelayed({
                        //         connectDevice(device, bleCallback!!)
                        //     }, 3000)
                        // }

                        // 释放资源（必须调用，否则蓝牙卡死）
                        gatt.close()
                        bluetoothGatt = null
                    }
                }

                // 连接失败处理
                if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                    isConnecting = false
                    DebugLog.e("设备连接失败：${device.address}，错误码：$status")
                    gatt.close()
                    bluetoothGatt = null
                }
            }

            // 服务发现成功回调
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                val device = gatt.device
                isConnecting = false

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services
                    callback.onConnectedState("发现设备服务：${services.size}个")

                    callback.onServicesFound(services)
                } else {
                    callback.onConnectedState("服务发现失败：${device.address}，状态码：$status")
                }
            }

            // 特征值读取结果回调
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                isConnecting = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val value = characteristic.value
                    DebugLog.e("特征值读取成功：${String(value)}")
                } else {
                    DebugLog.e("特征值读取失败，状态码：$status")
                }
            }
        })

        // 连接超时处理
        connectHandler.postDelayed({
            if (isConnecting) {
                isConnecting = false
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                connectCallback?.onConnectedState("连接超时：${device.address}")
                DebugLog.e("连接超时：${device.address}")
            }
        }, CONNECT_TIMEOUT)
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (bluetoothGatt == null || !checkBLEAvailable()) {
            DebugLog.e("GATT未连接，无法读取特征值")
            return
        }
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray):String {
        if (bluetoothGatt == null || !checkBLEAvailable()) {
            return "GATT未连接，无法写入特征值"
        }

        // 检查特征值是否可写
        if ((characteristic.properties and PROPERTY_WRITE) == 0) {
            return "特征值不可写：${characteristic.uuid}"
        }

        // 设置写入数据
        characteristic.value = data
        // 发起写入（false=无响应写入，true=有响应写入，工业场景推荐true）
        val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        if (!success) {
            return "写入特征值失败：${characteristic.uuid}"
        } else {
            return "写入数据成功：${String(data)}"
        }
    }

    @SuppressLint("MissingPermission")
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        if (bluetoothGatt == null || !checkBLEAvailable()) {
            DebugLog.e("GATT未连接，无法设置通知")
            return
        }

        // 检查特征值是否支持通知
        if ((characteristic.properties and PROPERTY_NOTIFY) == 0) {
            DebugLog.e("特征值不支持通知：${characteristic.uuid}")
            return
        }

        // 第一步：开启GATT通知
        bluetoothGatt?.setCharacteristicNotification(characteristic, enable)

        // 第二步：写入CCCD描述符（BLE通知必须步骤，工业开发必记）
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        characteristic.descriptors.find { it.uuid == cccdUuid }?.let { descriptor ->
            val value = if (enable) {
                // 开启通知
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                // 关闭通知
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            descriptor.value = value
            bluetoothGatt?.writeDescriptor(descriptor)
        }

        DebugLog.e("${if (enable) "开启" else "关闭"}特征值通知：${characteristic.uuid}")
    }
}
