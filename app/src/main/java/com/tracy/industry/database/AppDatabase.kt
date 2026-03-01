package com.tracy.industry.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tracy.industry.base.MyApplication
import com.tracy.industry.database.dao.DeviceDao
import com.tracy.industry.database.entity.DeviceEntity

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 21:33
 */
@Database(entities = [DeviceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase(){
    // 暴露DAO接口，Room会自动实现
    abstract fun deviceDao(): DeviceDao

    // 单例模式（避免重复创建数据库实例）
    companion object {
        // volatile保证多线程可见性
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase {
            // 双重校验锁，确保单例
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    MyApplication.instance,
                    AppDatabase::class.java,
                    "device_database" // 数据库文件名
                )
                    // 开发阶段允许主线程操作（正式版建议移除，用协程）
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}