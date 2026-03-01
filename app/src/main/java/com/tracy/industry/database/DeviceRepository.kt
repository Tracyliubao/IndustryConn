package com.tracy.industry.database

import com.tracy.industry.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 21:36
 */
class DeviceRepository {
    private val deviceDao = AppDatabase.getInstance().deviceDao()

    // 插入设备数据
    suspend fun insertDevice(device: DeviceEntity) {
        deviceDao.insertDevice(device)
    }

    // 查询所有设备数据
    fun getAllDevices(): Flow<List<DeviceEntity>> {
        return deviceDao.getAllDevices()
    }

    // 根据编号查询设备
    suspend fun getDeviceByCode(code: String): DeviceEntity? {
        return deviceDao.getDeviceByCode(code)
    }

    // 删除设备
    suspend fun deleteDeviceById(id: Int): Int {
        return deviceDao.deleteDeviceById(id)
    }
}