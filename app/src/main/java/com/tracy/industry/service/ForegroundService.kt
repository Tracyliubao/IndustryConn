package com.tracy.industry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.tracy.industry.util.DebugLog
import java.util.Timer
import java.util.TimerTask

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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // 1. 检测并引导关闭电池优化
        checkBatteryOptimization()
        // 2. 启动前台服务（提升进程优先级）
        startForegroundService()
        // 3. 启动守护进程
        startGuardService()
        // 4.启用心跳
        startHeartbeatLog()
    }

    // 心跳日志：核心是打印进程ID和Service名称
    private fun startHeartbeatLog() {
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 获取当前进程ID
                val pid = android.os.Process.myPid()
                DebugLog.e("【ForegroundService】存活中 | 进程ID：$pid | 时间：${System.currentTimeMillis()}")

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

    override fun onDestroy() {
        super.onDestroy()
        heartbeatTimer?.cancel()
        // 停止前台Service（可选，销毁时移除通知）
        stopForeground(STOP_FOREGROUND_REMOVE)
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
        DebugLog.e("ForegroundService已销毁，通知栏移除")
    }
}
