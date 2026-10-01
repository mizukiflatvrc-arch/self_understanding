package com.example.self_understanding.domain.repository

import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.domain.model.OccurredAtUpdate
import com.example.self_understanding.domain.model.TraceNodeType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TraceRepository {
    // Trace operations
    fun getAllActiveTraces(): Flow<List<TraceEntity>>
    suspend fun getTraceById(id: String): TraceEntity?
    suspend fun createTrace(title: String): String
    /** 終了時刻を記録する。対象が存在しない、または削除済みの場合はIllegalStateException。 */
    suspend fun endTrace(id: String)
    /** Traceを論理削除する。対象が存在しない、または削除済みの場合はIllegalStateException。 */
    suspend fun deleteTrace(id: String)

    // Node operations
    fun getNodesForTrace(traceId: String): Flow<List<TraceNodeEntity>>
    /**
     * occurredAtはNULLもそのまま保存する。sequenceがNULLなら末尾に追加する。
     * 指定位置は1〜有効Node件数＋1。範囲外はIllegalArgumentException。
     * 親Traceが存在しない、または削除済みの場合はIllegalStateException。
     */
    suspend fun addNode(
        traceId: String,
        type: TraceNodeType,
        content: String,
        occurredAt: Instant?,
        sequence: Int? = null
    )
    /**
     * 日時は既定のKeepで維持し、Set(null)で消去する。
     * Nodeまたは親Traceが存在しない、または削除済みの場合はIllegalStateException。
     */
    suspend fun updateNode(
        id: String,
        type: TraceNodeType,
        content: String,
        occurredAtUpdate: OccurredAtUpdate = OccurredAtUpdate.Keep
    )
    /**
     * Nodeを論理削除し、有効Nodeのsequenceを1始まりに整理する。
     * Nodeまたは親Traceが存在しない、または削除済みの場合はIllegalStateException。
     */
    suspend fun deleteNode(id: String)
}
