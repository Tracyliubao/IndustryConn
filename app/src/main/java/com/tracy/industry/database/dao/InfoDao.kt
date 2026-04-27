package com.tracy.industry.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tracy.industry.database.entity.InfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInfo(info: InfoEntity)

    @Query("SELECT * FROM info")
    fun queryInfo(): Flow<List<InfoEntity>>

    @Query("DELETE FROM info WHERE id = :id")
    suspend fun deleteInfoById(id: Int)
}