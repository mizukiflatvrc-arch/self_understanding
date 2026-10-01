package com.example.self_understanding.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.domain.model.TraceNodeType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TraceNodeDao {
    @Query("SELECT * FROM trace_nodes WHERE traceId = :traceId AND deletedAt IS NULL AND EXISTS (SELECT 1 FROM traces WHERE traces.id = trace_nodes.traceId AND traces.deletedAt IS NULL) ORDER BY sequence ASC")
    fun getNodesForTrace(traceId: String): Flow<List<TraceNodeEntity>>

    @Query("SELECT * FROM trace_nodes WHERE id = :id AND deletedAt IS NULL AND EXISTS (SELECT 1 FROM traces WHERE traces.id = trace_nodes.traceId AND traces.deletedAt IS NULL)")
    suspend fun getNodeById(id: String): TraceNodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNode(node: TraceNodeEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM traces WHERE id = :traceId AND deletedAt IS NULL)")
    suspend fun isTraceActive(traceId: String): Boolean

    @Query("SELECT COUNT(*) FROM trace_nodes WHERE traceId = :traceId AND deletedAt IS NULL")
    suspend fun getActiveNodeCount(traceId: String): Int

    // Only editable fields are written, preserving concurrent sequence changes.
    @Query("UPDATE trace_nodes SET type = :type, content = :content, occurredAt = CASE WHEN :setOccurredAt THEN :occurredAt ELSE occurredAt END, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL AND EXISTS (SELECT 1 FROM traces WHERE traces.id = trace_nodes.traceId AND traces.deletedAt IS NULL)")
    suspend fun updateNodeFields(id: String, type: TraceNodeType, content: String, setOccurredAt: Boolean, occurredAt: Instant?, updatedAt: Instant): Int

    @Query("UPDATE trace_nodes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteNode(id: String, deletedAt: Instant, updatedAt: Instant)

    @Query("SELECT MAX(sequence) FROM trace_nodes WHERE traceId = :traceId AND deletedAt IS NULL")
    suspend fun getMaxSequence(traceId: String): Int?

    @Query("UPDATE trace_nodes SET sequence = sequence + 1, updatedAt = :updatedAt WHERE traceId = :traceId AND sequence >= :fromSequence AND deletedAt IS NULL")
    suspend fun shiftSequencesUp(traceId: String, fromSequence: Int, updatedAt: Instant)

    @Transaction
    suspend fun appendNode(node: TraceNodeEntity) {
        check(isTraceActive(node.traceId)) { "Trace is missing or deleted: "+node.traceId }
        val nextSequence = (getMaxSequence(node.traceId) ?: 0) + 1
        insertNode(node.copy(sequence = nextSequence))
    }

    @Transaction
    suspend fun insertNodeAtSequence(node: TraceNodeEntity, updatedAt: Instant) {
        check(isTraceActive(node.traceId)) { "Trace is missing or deleted: "+node.traceId }
        val lastInsertionPosition = getActiveNodeCount(node.traceId) + 1
        require(node.sequence in 1..lastInsertionPosition) {
            "Insertion sequence must be in 1.."+lastInsertionPosition+": "+node.sequence
        }
        shiftSequencesUp(node.traceId, node.sequence, updatedAt)
        insertNode(node)
    }

    @Query("SELECT * FROM trace_nodes WHERE traceId = :traceId AND deletedAt IS NULL AND EXISTS (SELECT 1 FROM traces WHERE traces.id = trace_nodes.traceId AND traces.deletedAt IS NULL) ORDER BY sequence ASC")
    suspend fun getNodesForTraceSync(traceId: String): List<TraceNodeEntity>

    @Update
    suspend fun updateNodes(nodes: List<TraceNodeEntity>)

    @Transaction
    suspend fun reorderSequences(traceId: String, updatedAt: Instant) {
        val nodes = getNodesForTraceSync(traceId)
        updateNodes(nodes.mapIndexed { index, node ->
            node.copy(sequence = index + 1, updatedAt = updatedAt)
        })
    }

    @Transaction
    suspend fun deleteNodeAndReorder(id: String, updatedAt: Instant) {
        val node = checkNotNull(getNodeById(id)) { "Node or its Trace is missing or deleted: "+id }
        softDeleteNode(id, updatedAt, updatedAt)
        reorderSequences(node.traceId, updatedAt)
    }
}
