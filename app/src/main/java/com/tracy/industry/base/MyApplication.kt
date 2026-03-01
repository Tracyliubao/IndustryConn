package com.tracy.industry.base

import android.app.Application
import com.tracy.industry.util.CrashHandler

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
        CrashHandler.getInstance().init(this)
    }

}