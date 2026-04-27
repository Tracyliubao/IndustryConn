package com.tracy.industry.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Des:命令信息
 * Author:LiuBao
 * Time:2026/4/26 20:38
 */
@Entity(tableName = "info")
data class InfoEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val content: String,
    /**
     * 发送状态：1-已发送 0-未发送
     */
    val status: Int
)