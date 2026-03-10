package com.tracy.industry.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.tracy.industry.util.DebugLog
import java.util.Timer
import java.util.TimerTask

/**
 * Des:守护进程
 * Author:LiuBao
 * Time:2026/2/27 21:52
 */
class GuardService : Service() {

    private var isRunning = false
    private var heartbeatTimer: Timer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // 启动线程，定时检测主 Service 是否存活
        startMonitorThread()
        DebugLog.e("GuardService已启动")
        startHeartbeatLog()
    }

    // 心跳日志：核心是打印进程ID和Service名称
    private fun startHeartbeatLog() {
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 获取当前进程ID
                val pid = android.os.Process.myPid()
                DebugLog.e("【GuardService】存活中 | 进程ID：$pid | 时间：${System.currentTimeMillis()}")
            }
        }, 0, 5000)
    }

    /**
     * 监控线程：每隔5秒检查主 Service，若未运行则重启
     */
    private fun startMonitorThread() {
        isRunning = true
        Thread {
            while (isRunning) {
                SystemClock.sleep(5000) // 5秒检测一次
                // 检查主 Service 是否存活（简化版，实际可通过 ActivityManager 精准判断）
                val isMainServiceAlive = isServiceRunning(ForegroundService::class.java.name)
                if (!isMainServiceAlive) {
                    // 重启主 Service
                    val intent = Intent(this, ForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    break // 重启后退出当前检测循环
                }
            }
        }.start()
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

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        heartbeatTimer?.cancel()
        // 守护进程被杀死时，重启自身（作为普通服务，不是前台服务）
        val intent = Intent(this, GuardService::class.java)
        startService(intent)
        DebugLog.e("GuardService已被销毁")
    }
}
