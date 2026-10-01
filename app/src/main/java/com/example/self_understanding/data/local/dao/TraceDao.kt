package com.example.self_understanding.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.self_understanding.data.local.entity.TraceEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TraceDao {
    @Query("SELECT * FROM traces WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllActiveTraces(): Flow<List<TraceEntity>>

    @Query("SELECT * FROM traces WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTraceById(id: String): TraceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrace(trace: TraceEntity)

    @Update
    suspend fun updateTrace(trace: TraceEntity)

    // Soft delete: deletedAtを現在時刻で更新する
    @Query("UPDATE traces SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteTrace(id: String, deletedAt: Instant, updatedAt: Instant): Int

    @Query("UPDATE traces SET endedAt = :endedAt, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun endTrace(id: String, endedAt: Instant, updatedAt: Instant): Int
}
