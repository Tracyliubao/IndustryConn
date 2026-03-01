package com.tracy.industry.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_data")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val deviceId: Int = 0,
    val deviceName: String,
    val deviceCode: String,
    val deviceStatus: String,
    val createTime: Long = System.currentTimeMillis()
)
