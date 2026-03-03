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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

object IndustrialTimeUtils {

    private const val REQUEST_CODE = 1001
    private val mmkv = ConfParams.getMMKVInstance()
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

        // 精准触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
        // 每天重复
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
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
        // 1. WorkManager兜底（进程重启后恢复）
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

        // 2. 协程循环实现5s精准采集（核心）
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                // 写入日志
                delay(5000) // 精准延迟5秒，无漂移
            }
        }
    }

    /**
     * 停止采集
     */
    fun stopCollectTask() {
        WorkManager.getInstance(MyApplication.instance).cancelUniqueWork("CollectTask")
    }
}