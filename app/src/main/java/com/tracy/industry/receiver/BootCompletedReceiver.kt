package com.tracy.industry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tracy.industry.util.DebugLog

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/26 21:58
 */
class BootCompletedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // 判断是否接收到开机完成的广播
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            DebugLog.e("设备开机完成，触发开机自启动逻辑")

            // 这里写你开机后要执行的逻辑，比如启动服务、打开Activity等
            // 示例：启动一个后台服务
            // Intent serviceIntent = new Intent(context, MyBackgroundService.class);
            // context.startForegroundService(serviceIntent); // Android 8.0+ 需用前台服务
        }
    }
}