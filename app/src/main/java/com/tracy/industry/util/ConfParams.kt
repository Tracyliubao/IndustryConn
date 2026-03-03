package com.tracy.industry.util

import com.tencent.mmkv.MMKV
import com.tracy.industry.base.MyApplication

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 14:57
 */
class ConfParams {

    companion object {
        // MMKV 实例（工业场景推荐单例）
        private val mmkv: MMKV = MMKV.defaultMMKV()

        // 设备连接相关
        const val KEY_DEVICE_IP = "device_ip"
        const val KEY_DEVICE_PORT = "device_port"
        const val KEY_PLC_TYPE = "plc_type"
        // 采集参数相关
        const val KEY_SAMPLE_RATE = "sample_rate"
        const val KEY_ALARM_THRESHOLD = "alarm_threshold"
        // 默认配置值（工业场景固定默认值）
        const val DEFAULT_PORT = 502
        const val DEFAULT_SAMPLE_RATE = 1000
        const val DEFAULT_ALARM_THRESHOLD = 80

        const val KEY_COLLECT_RUNNING = "collect_running"

        private val APP_BASE = "${MyApplication.instance.getExternalFilesDir(null)?.absolutePath}/industry_conn"

        val DIR_CRASH = "$APP_BASE/crash"

        val DIR_CONFIG = "$APP_BASE/config"

        /**
         * 所有需要初始化创建的目录
         */
        val initialFolders: MutableList<String> = mutableListOf(
            APP_BASE, DIR_CRASH, DIR_CONFIG
        )

        fun getMMKVInstance(): MMKV = mmkv
    }


}