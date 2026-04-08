package com.tracy.industry.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tracy.industry.base.MyApplication
import com.tracy.industry.receiver.IndustrialCalibrateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

object IndustrialTimeUtils {

    private const val REQUEST_CODE = 1001
    private val mmkv = ConfParams.getMMKVInstance()
    private val collectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    /**
     * 计算从当前时间到「次日0点」的延迟（毫秒）
     * 用于每天0点校准的精准触发
     */
    fun calculateDelayToMidnight() {
        val alarmManager =
            MyApplication.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(MyApplication.instance, IndustrialCalibrateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            MyApplication.instance,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 计算0点时间
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }

        // 检查是否有精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                DebugLog.e("没有精确闹钟权限，使用非精确闹钟")
                // 没有权限时使用非精确闹钟
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
                return
            }
        }

        // 有权限时使用精确闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    // 取消校准
    fun cancelCalibrateTask(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, IndustrialCalibrateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }


    /**
     * 启动5s精准采集
     * 已在主服务中启动
     */

    fun startCollectTask() {
        mmkv.encode(ConfParams.KEY_COLLECT_RUNNING, true)
        // 1. WorkManager 仅做兜底健康检查（不做 5s 高频采集）
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<DeviceMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(MyApplication.instance)
            .enqueueUniquePeriodicWork(
                "CollectTask",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

        // 2. 协程循环实现 5s 高频采集（核心，可停止）
        collectJob?.cancel()
        collectJob = collectScope.launch {
            while (true) {
                // 在这里执行真实采集任务（串口/BLE/TCP）
                delay(5000) // 精准延迟5秒，无漂移
            }
        }
    }

    /**
     * 停止采集
     */
    fun stopCollectTask() {
        mmkv.encode(ConfParams.KEY_COLLECT_RUNNING, false)
        collectJob?.cancel()
        collectJob = null
        WorkManager.getInstance(MyApplication.instance).cancelUniqueWork("CollectTask")
    }
}
