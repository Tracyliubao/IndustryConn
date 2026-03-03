package com.tracy.industry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tracy.industry.database.AppDatabase

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/3 20:38
 */
class NetworkStateReceiver : BroadcastReceiver() {

    private val deviceDao = AppDatabase.getInstance().deviceDao()

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.apply {
            if (isNetworkAvailable(this)){
                // deviceDao.getAllDevices()
                // 1.获取到所有数据
                // 2.选出所有【离线】数据
                // 3.上传服务器
            }
        }
    }
}