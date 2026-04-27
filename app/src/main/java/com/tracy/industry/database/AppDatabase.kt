package com.tracy.industry.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tracy.industry.base.MyApplication
import com.tracy.industry.database.dao.DeviceDao
import com.tracy.industry.database.dao.InfoDao
import com.tracy.industry.database.entity.DeviceEntity
import com.tracy.industry.database.entity.InfoEntity

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/1 21:33
 */
@Database(entities = [DeviceEntity::class, InfoEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase(){
    // 暴露DAO接口，Room会自动实现
    abstract fun deviceDao(): DeviceDao
    abstract fun infoDao(): InfoDao

    // 单例模式（避免重复创建数据库实例）
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `info` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `status` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
