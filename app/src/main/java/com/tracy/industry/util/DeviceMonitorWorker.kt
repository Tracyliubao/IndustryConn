package com.tracy.industry.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tracy.industry.database.AppDatabase
import com.tracy.industry.database.entity.DeviceEntity

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/28 21:16
 */
class DeviceMonitorWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val deviceDao = AppDatabase.getInstance().deviceDao()

    /**
     * 判断当前网络状态
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun doWork(): Result {
        try {
            DebugLog.e("doWork精准采集")
            val isHasNet = isNetworkAvailable()
            deviceDao.insertDevice(DeviceEntity(deviceId = 111, deviceName = "机械", deviceCode = "101EV", deviceStatus = if (isHasNet) "在线" else "离线"))
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