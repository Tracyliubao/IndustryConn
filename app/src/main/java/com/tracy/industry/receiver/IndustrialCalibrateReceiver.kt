package com.tracy.industry.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tracy.industry.base.MyApplication
import com.tracy.industry.util.DebugLog
import com.tracy.industry.util.IndustrialTimeUtils

/**
 * Des:校准广播
 * Author:LiuBao
 * Time:2026/3/3 09:17
 */
class IndustrialCalibrateReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            DebugLog.e("0点校准成功，已同步服务器时间")
        }catch (e: Exception){
            DebugLog.e("校准时间失败")
            // writeToLogFile()
            retryCalibrate()
        }
        
        // 无论成功失败，都重新设置下一次的闹钟
        IndustrialTimeUtils.calculateDelayToMidnight()
    }

    /**
     * 校准失败重试
     */
    private fun retryCalibrate() {
        val alarmManager = MyApplication.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(MyApplication.instance, IndustrialCalibrateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            MyApplication.instance,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val retryTime = System.currentTimeMillis() + 5 * 60 * 1000
        
        // 检查是否有精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                DebugLog.e("没有精确闹钟权限，使用非精确闹钟重试")
                alarmManager.set(AlarmManager.RTC_WAKEUP, retryTime, pendingIntent)
                return
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, retryTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, retryTime, pendingIntent)
        }
    }
}