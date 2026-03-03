package com.tracy.industry.base

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.tracy.industry.database.AppDatabase
import com.tracy.industry.util.ConfParams
import com.tracy.industry.util.CrashHandler
import com.tracy.industry.util.DebugLog
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
        MMKV.initialize(this)

        if (!WorkManager.isInitialized()){
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
            WorkManager.initialize(this, config)
        }

        CrashHandler.getInstance().init(this)
        // 数据库初始化
        AppDatabase.getInstance()

        checkCollectTask()
        // 开始循环采集
        IndustrialTimeUtils.startCollectTask()
        // 每日0点校准
        IndustrialTimeUtils.calculateDelayToMidnight()
    }

    private fun checkCollectTask() {
        // 监听App前后台/进程状态
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                // App进入后台（工业场景：超过5分钟无操作则停止采集）
                if (event == Lifecycle.Event.ON_STOP) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            // 确认是后台且无活跃页面：停止采集
                            DebugLog.e("Application停止采集")
                            IndustrialTimeUtils.stopCollectTask()
                        }
                    }, 5 * 60 * 1000) // 5分钟延迟，避免误触发
                }
                // App回到前台：恢复采集（如果是正常运行状态）
                if (event == Lifecycle.Event.ON_START) {
                    val isRunning = ConfParams.getMMKVInstance().decodeBool(ConfParams.KEY_COLLECT_RUNNING, false)
                    if (isRunning) {
                        DebugLog.e("Application恢复采集")
                        IndustrialTimeUtils.startCollectTask()
                    }
                }
            }
        })
    }

}