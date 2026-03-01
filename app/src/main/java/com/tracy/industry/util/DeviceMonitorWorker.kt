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
        // 工业场景：这里写设备采集、Modbus、MQTT、状态上报
        DebugLog.e("【工业采集】设备状态读取成功 -> time=${System.currentTimeMillis()}")
        return Result.success()
    }
}