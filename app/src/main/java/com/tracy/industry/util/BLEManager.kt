package com.industrial.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import com.tracy.industry.util.DebugLog

class BLEManager private constructor(private val context: Context) {

    // BLE核心对象
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private var bleScanner: BluetoothLeScanner? = null

    // 扫描状态
    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    // 扫描超时（默认10秒）
    private val SCAN_TIMEOUT = 10000L

    // 回调接口
    interface BLEScanCallback {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?)
        fun onScanStart()
        fun onScanStop()
        fun onScanError(errorCode: Int)
    }
    private var scanCallback: BLEScanCallback? = null

    // 单例模式
    companion object {
        @Volatile
        private var INSTANCE: BLEManager? = null

        fun getInstance(context: Context): BLEManager {
            return INSTANCE ?: synchronized(this) {
                val instance = BLEManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // ========== 1. 初始化检查 ==========
    /**
     * 检查BLE是否可用
     * @return true=可用，false=不可用
     */
    fun checkBLEAvailable(): Boolean {
        // 🔥 修改：注释掉「必须支持BLE硬件」的校验（部分平板检测不准，但实际能扫描）
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
}