package com.tracy.industry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.tracy.industry.util.BLEManager
import com.tracy.industry.util.DebugLog
import com.tracy.industry.util.MqttManager
import com.tracy.industry.util.ModbusUtils
import com.tracy.industry.util.SerialPortManager
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/26 22:09
 */
class ForegroundService: Service() {

    // 通知渠道ID（Android O+必须）
    private val CHANNEL_ID = "KEEP_ALIVE_CHANNEL"
    private val NOTIFICATION_ID = 1001
    private var heartbeatTimer: Timer? = null
    private val WORK_NAME = "DEVICE_MONITOR"
    private lateinit var serialPortUtil: SerialPortManager
    private val collectExecutor = Executors.newSingleThreadScheduledExecutor()

    private var temperatureText: String = ""
    private var isConnect = false
    private val bleManager by lazy { BLEManager.getInstance() }
    private var deviceList = mutableListOf<BluetoothDevice>()
    private var isScanCompleted = false

    /**
     * 新增MQTT管理器
     */
    private lateinit var mqttManager: MqttManager

    override fun onBind(intent: Intent?): IBinder {
        return MyBinder(this)
    }

    class MyBinder(val service: ForegroundService) : Binder() {

    }

    override fun onCreate() {
        super.onCreate()
//        mqttManager = MqttManager()
        serialPortUtil = SerialPortManager(portPath = "/dev/ttyS1", baudRate = 9600)
        // 2. 启动前台服务（提升进程优先级）
        startForegroundService()
        // 3. 启动守护进程
        startGuardService()
        // 4.启用心跳
        startHeartbeatLog()

//        5. 串口 / Modbus 通信初始化
//        6. 定时采集设备数据
//        7. 本地数据缓存
//        8. 串口断连重连机制

//        mqttManager.connect(onSuccess = {
//            DebugLog.e("MQTT连接成功")
//            startScanBLE()
//        }, onFailed = {msg ->
//            DebugLog.e("MQTT连接失败: ${msg}")
//        })
    }

    // 心跳日志：核心是打印进程ID和Service名称
    private fun startHeartbeatLog() {
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 获取当前进程ID
                val pid = android.os.Process.myPid()
//                DebugLog.e("【ForegroundService】存活中 | 进程ID：$pid | 时间：${System.currentTimeMillis()}")

                // 主动检查 GuardService 是否存活
                if (!isServiceRunning(GuardService::class.java.name)) {
                    DebugLog.e("【ForegroundService】检测到 GuardService 已死，尝试重启...")
                    startService(Intent(this@ForegroundService, GuardService::class.java))
                }
            }
        }, 0, 5000)
    }

    /**
     * 判断 Service 是否运行
     */
    private fun isServiceRunning(serviceName: String): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
        for (service in runningServices) {
            if (service.service.className == serviceName) {
                return true
            }
        }
        return false
    }

    private fun startForegroundService(){
        // 1. 创建通知渠道（Android O+）
        createNotificationChannel()

        // 2. 构建通知
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("前台服务运行中")
            .setContentText("APP已启动前台Service，通知常驻")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 系统默认图标，也可以用自己的
            .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级，不弹窗
            .build()

        // 3. 启动前台Service
        startForeground(NOTIFICATION_ID, notification)

        // 这里可以写Service的核心逻辑（比如后台任务）
        // 示例：打印日志
        DebugLog.e("ForegroundService已启动，常驻通知栏显示")
    }

    // ========== 定时采集数据 ==========
    private fun startDataCollection() {
        collectExecutor.scheduleWithFixedDelay({
            DebugLog.e("startDataCollection-------->")
            serialPortUtil.open()
            // 1. 读保持寄存器（03）：温度
            val tempRegValues = ModbusUtils.buildReadHoldingRegistersCmd(1, 0,1)
            if (tempRegValues.isNotEmpty()) {
                val temperature = tempRegValues[0] / 10.0
                temperatureText = "采集温度：${temperature}℃"
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    // 创建通知渠道（Android 8.0+ 必须）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "前台服务渠道"
            val channelDescription = "用于显示前台Service的常驻通知"
            val importance = NotificationManager.IMPORTANCE_LOW // 低重要性，不打扰用户
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
            }
            // 注册渠道到系统
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动守护进程：当主 Service 被杀死时，守护进程重启它
     */
    private fun startGuardService() {
        val intent = Intent(this, GuardService::class.java)
        // GuardService 是普通服务，不是前台服务，使用 startService 启动
        startService(intent)
    }

    fun getTemperature(): String = temperatureText

    fun isSocketConnected(): Boolean = isConnect

    fun getBLEDeviceList(): MutableList<BluetoothDevice> = deviceList

    fun isDeviceScanCompleted(): Boolean = isScanCompleted

    /**
     * 模拟BLE数据采集（替换为实际逻辑）
     */
    private fun collectBLEData(): ByteArray {
        // 示例：温湿度数据（0x01=设备ID，0x19=25℃，0x3C=60%）
        return byteArrayOf(0x01, 0x19, 0x3C)
    }

    /**
     * 模拟串口指令发送（替换为实际逻辑）
     */
    private fun sendSerialCommand(cmd: String) {
        DebugLog.e("发送串口指令：$cmd")
    }

    /**
     * 对外提供数据上传接口
     */
    fun uploadDeviceData(data: String) {
        mqttManager.publish(data)
    }

    /**
     * 开始扫描设备
     */
    fun startScanBLE(){
        if (bleManager.checkBLEAvailable()) {
            // 检查权限
            if (bleManager.checkBLEPermissions() && !isScanCompleted) {
                // 权限已获取，开始扫描
                scanBLE()
            }
            else {
                DebugLog.e("无此权限")
            }
        }
        else {
            DebugLog.e("请先开启蓝牙")
        }
    }

    private fun scanBLE() {
        bleManager.startScan(
            filterName = null, // 移除名称过滤，扫周边所有蓝牙设备
            callback = object : BLEManager.BLEScanCallback {
                override fun onDeviceFound(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
                    deviceList.add(device)
                }

                override fun onScanStart() {
                    DebugLog.e("扫描中...")
                }

                override fun onScanStop() {
                    if (deviceList.size > 0){
                        isScanCompleted = true
                    }
                }

                override fun onScanError(errorCode: Int) {
                    DebugLog.e("扫描失败：$errorCode")
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatTimer?.cancel()
        // 停止前台Service（可选，销毁时移除通知）
        stopForeground(STOP_FOREGROUND_REMOVE)
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
        DebugLog.e("ForegroundService已销毁，通知栏移除")
    }
}
