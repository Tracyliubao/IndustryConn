package com.tracy.industry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.tracy.industry.socket.WebSocketManager
import com.tracy.industry.util.DebugLog
import com.tracy.industry.util.SerialPortUtil
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
    private lateinit var powerManager: PowerManager
    private var heartbeatTimer: Timer? = null
    private val WORK_NAME = "DEVICE_MONITOR"
    private lateinit var modbusUtil: SerialPortUtil
    private val collectExecutor = Executors.newSingleThreadScheduledExecutor()

    private lateinit var wsManager: WebSocketManager

    private var temperatureText: String = ""
    private var count = 0

    override fun onBind(intent: Intent?): IBinder {
        return MyBinder(this)
    }

    class MyBinder(val service: ForegroundService) : Binder() {

    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        modbusUtil = SerialPortUtil(portPath = "/dev/ttyS1",
            baudRate = 9600,
            dataBits = 8,
            stopBits = 1,
            parity = 0 )
        wsManager = WebSocketManager()
        // 1. 检测并引导关闭电池优化
        checkBatteryOptimization()
        // 2. 启动前台服务（提升进程优先级）
        startForegroundService()
        // 3. 启动守护进程
        startGuardService()
        // 4.启用心跳
        startHeartbeatLog()

//        startDataCollection()

//        5. 串口 / Modbus 通信初始化
//        6. 定时采集设备数据
//        7. 本地数据缓存
//        8. 串口断连重连机制

        initWebSocket()
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

        // 3. 启动前台Service（关键步骤）
        startForeground(NOTIFICATION_ID, notification)

        // 这里可以写Service的核心逻辑（比如后台任务）
        // 示例：打印日志
        DebugLog.e("ForegroundService已启动，常驻通知栏显示")
    }

    // ========== 定时采集数据 ==========
    private fun startDataCollection() {
        collectExecutor.scheduleWithFixedDelay({
            DebugLog.e("startDataCollection-------->")
            modbusUtil.openSerialPort()
            // 1. 读保持寄存器（03）：温度
            val tempRegValues = modbusUtil.readHoldingRegisters(1, 0, 1)
            if (tempRegValues.isNotEmpty()) {
                val temperature = tempRegValues[0] / 10.0
                temperatureText = "采集温度：${temperature}℃"
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    /**
     * 检测电池优化：若开启，引导用户关闭
     */
    private fun checkBatteryOptimization() {
        // 判断应用是否在电池优化白名单中
        val isIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
        if (!isIgnored) {
            // 跳转到电池优化设置页面，引导用户关闭
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
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


    private fun initWebSocket(){
        val testWsUrl = "wss://ws.postman-echo.com/raw"
        wsManager.init(testWsUrl, object : WebSocketManager.Callback {
            override fun onConnectionStateChanged(isConnected: Boolean) {
                // 连接状态变化（更新UI/记录状态）
                DebugLog.e("长连接状态：$isConnected")
            }

            override fun onTextDataReceived(data: String) {
                // 处理服务器下发的文本指令
                handleServerTextCommand(data)
            }

            override fun onBinaryDataReceived(data: ByteArray) {
                // 处理服务器下发的二进制配置
                handleServerBinaryConfig(data)
            }

            override fun onError(code: Int, msg: String) {
                // 异常处理（记录日志/上报监控）
                DebugLog.e("长连接异常：$code -> $msg")
            }
        })
        // 2. 启动连接
        wsManager.connect()
    }

    /**
     * 处理服务器文本指令（工业场景核心）
     */
    private fun handleServerTextCommand(cmd: String) {
        when (cmd) {
            // 示例：服务器下发BLE采集指令
            "CMD:COLLECT_BLE" -> {
                // 调用BLE工具类采集数据
                val bleData = collectBLEData()
                // 上传二进制数据
                wsManager.sendBinaryData(bleData)
                // 上报采集完成状态
                wsManager.sendTextData("RESP:BLE_COLLECT_DONE")
            }
            // 示例：服务器下发串口控制指令
            "CMD:SERIAL_CTRL_ON" -> {
                // 调用串口工具类执行指令
                sendSerialCommand("DEVICE_ON")
                wsManager.sendTextData("RESP:SERIAL_CTRL_SUCCESS")
            }
        }
    }

    /**
     * 处理服务器二进制配置
     */
    private fun handleServerBinaryConfig(config: ByteArray) {
        // 解析工业设备参数（如采集频率、阈值）
        DebugLog.e("解析设备配置，长度：${config.size}")
        wsManager.sendTextData("RESP:CONFIG_PARSED_SUCCESS")
    }

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
    fun uploadDeviceData(data: ByteArray) {
        wsManager.sendBinaryData(data)
    }


    override fun onDestroy() {
        super.onDestroy()
        heartbeatTimer?.cancel()
        // 停止前台Service（可选，销毁时移除通知）
        stopForeground(STOP_FOREGROUND_REMOVE)
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
        wsManager.release()
        DebugLog.e("ForegroundService已销毁，通知栏移除")
    }
}
