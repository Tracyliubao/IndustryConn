package com.tracy.industry.util

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/28 21:16
 */
class DeviceMonitorWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            DebugLog.e("doWork精准采集")
            // 此处可以将记录写入日志
            // writeToLogFile()
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            DebugLog.e("采集失败")
            // 此处可以将记录写入日志
            // writeToLogFile()
            return Result.retry()
        }
    }
}