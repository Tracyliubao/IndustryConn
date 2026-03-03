package com.tracy.industry.base

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.tracy.industry.database.AppDatabase
import com.tracy.industry.util.CrashHandler
import com.tracy.industry.util.IndustrialTimeUtils

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/26 21:11
 */
class MyApplication: Application() {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!WorkManager.isInitialized()){
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
            WorkManager.initialize(this, config)
        }
        CrashHandler.getInstance().init(this)
        // 数据库初始化
        AppDatabase.getInstance()
        MMKV.initialize(this)
        // 开始循环采集
        IndustrialTimeUtils.startCollectTask()
        // 每日0点校准
        IndustrialTimeUtils.calculateDelayToMidnight()
    }

}