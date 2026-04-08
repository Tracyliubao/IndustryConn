package com.tracy.industry.page.main

import android.app.Application
import com.tracy.industry.base.BaseViewModel
import com.tracy.industry.util.ConfParams
import java.io.File

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 14:58
 */
class MainViewModel(application: Application) : BaseViewModel(application) {
    /**
     * 创建app基础目录
     */
    fun createDir() {
        for (path in ConfParams.initialFolders) {
            val file = File(path)
            if (!file.exists()) {
                file.mkdir()
            }
        }
    }

}