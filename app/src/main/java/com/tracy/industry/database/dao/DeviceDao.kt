package com.tracy.industry.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tracy.industry.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow


/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 21:32
 */
@Dao
interface DeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDevice(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDevices(vararg devices: DeviceEntity)

    // 查询所有设备数据（Flow支持数据监听，数据变化自动回调）
    @Query("SELECT * FROM device_data ORDER BY createTime DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    // 根据设备编号查询单条数据
    @Query("SELECT * FROM device_data WHERE deviceCode = :code LIMIT 1")
    suspend fun getDeviceByCode(code: String): DeviceEntity?

    // 根据ID删除设备数据
    @Query("DELETE FROM device_data WHERE deviceId = :id")
    suspend fun deleteDeviceById(id: Int): Int

    // 删除所有设备数据
    @Query("DELETE FROM device_data")
    suspend fun deleteAllDevices()
}