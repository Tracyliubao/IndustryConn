package com.tracy.industry.util

import com.tracy.industry.base.MyApplication

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 14:57
 */
class ConfParams {

    companion object {
        private val APP_BASE = "${MyApplication.instance.getExternalFilesDir(null)?.absolutePath}/industry_conn"

        val DIR_CRASH = "$APP_BASE/crash"

        /**
         * 所有需要初始化创建的目录
         */
        val initialFolders: MutableList<String> = mutableListOf(
            APP_BASE, DIR_CRASH,
        )
    }


}