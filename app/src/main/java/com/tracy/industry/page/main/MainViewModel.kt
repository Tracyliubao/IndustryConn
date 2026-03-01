package com.tracy.industry.page.main

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.tracy.industry.base.BaseViewModel
import com.tracy.industry.database.entity.DeviceEntity
import com.tracy.industry.util.ConfParams
import com.tracy.industry.util.DebugLog
import java.io.File

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 14:58
 */
class MainViewModel(application: Application) : BaseViewModel(application) {

    private val deviceDao = getAppDatabase().deviceDao()

    val deviceData = MutableLiveData<DeviceEntity>()
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

    /**
     * 插入一条数据
     */
    fun insertDevice(){
        launchSingleThread({
            deviceDao.insertDevice(DeviceEntity(deviceName = "智能传感器", deviceCode = "DEV001", deviceStatus = "在线"))
        }, onComplete = {
            DebugLog.e("插入成功")
        })

    }

    fun queryDevice(){
        launchSingleThread({
            deviceDao.getDeviceByCode("DEV001")
        }, onComplete = {
            if (it != null){
                deviceData.value = it
            }
            else {
                DebugLog.e("device is null")
            }
        })
    }

}