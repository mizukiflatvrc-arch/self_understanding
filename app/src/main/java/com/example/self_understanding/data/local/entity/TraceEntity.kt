package com.example.self_understanding.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "traces")
data class TraceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
