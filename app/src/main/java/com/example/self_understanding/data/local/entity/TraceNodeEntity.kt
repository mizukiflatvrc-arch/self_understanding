package com.example.self_understanding.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.self_understanding.domain.model.TraceNodeType
import java.time.Instant

@Entity(
    tableName = "trace_nodes",
    foreignKeys = [
        ForeignKey(
            entity = TraceEntity::class,
            parentColumns = ["id"],
            childColumns = ["traceId"],
            onDelete = ForeignKey.NO_ACTION // 物理CASCADE DELETEは行わない
        )
    ],
    indices = [
        Index(value = ["traceId", "deletedAt", "sequence"])
    ]
)
data class TraceNodeEntity(
    @PrimaryKey val id: String,
    val traceId: String,
    val sequence: Int,
    val type: TraceNodeType,
    val content: String,
    val occurredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
