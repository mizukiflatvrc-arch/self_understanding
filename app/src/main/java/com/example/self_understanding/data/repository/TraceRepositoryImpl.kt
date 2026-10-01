package com.example.self_understanding.data.repository

import com.example.self_understanding.data.local.dao.TraceDao
import com.example.self_understanding.data.local.dao.TraceNodeDao
import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.domain.model.OccurredAtUpdate
import com.example.self_understanding.domain.model.TraceNodeType
import com.example.self_understanding.domain.repository.TraceRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class TraceRepositoryImpl(
    private val traceDao: TraceDao,
    private val traceNodeDao: TraceNodeDao
) : TraceRepository {

    override fun getAllActiveTraces(): Flow<List<TraceEntity>> {
        return traceDao.getAllActiveTraces()
    }

    override suspend fun getTraceById(id: String): TraceEntity? {
        return traceDao.getTraceById(id)
    }

    override suspend fun createTrace(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val trace = TraceEntity(
            id = id,
            title = title,
            startedAt = now,
            endedAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        traceDao.insertTrace(trace)
        return id
    }

    override suspend fun endTrace(id: String) {
        val now = Instant.now()
        check(traceDao.endTrace(id, now, now) == 1) { "Trace is missing or deleted: $id" }
    }

    override suspend fun deleteTrace(id: String) {
        val now = Instant.now()
        check(traceDao.softDeleteTrace(id, now, now) == 1) { "Trace is missing or deleted: $id" }
    }

    override fun getNodesForTrace(traceId: String): Flow<List<TraceNodeEntity>> {
        return traceNodeDao.getNodesForTrace(traceId)
    }

    override suspend fun addNode(
        traceId: String,
        type: TraceNodeType,
        content: String,
        occurredAt: Instant?,
        sequence: Int?
    ) {
        val id = UUID.randomUUID().toString()
        val now = Instant.now()

        val node = TraceNodeEntity(
            id = id,
            traceId = traceId,
            sequence = sequence ?: 1,
            type = type,
            content = content,
            occurredAt = occurredAt,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        if (sequence != null) {
            traceNodeDao.insertNodeAtSequence(node, now)
        } else {
            traceNodeDao.appendNode(node)
        }
    }

    override suspend fun updateNode(
        id: String,
        type: TraceNodeType,
        content: String,
        occurredAtUpdate: OccurredAtUpdate
    ) {
        val updatedRows = traceNodeDao.updateNodeFields(
            id = id,
            type = type,
            content = content,
            setOccurredAt = occurredAtUpdate is OccurredAtUpdate.Set,
            occurredAt = (occurredAtUpdate as? OccurredAtUpdate.Set)?.value,
            updatedAt = Instant.now()
        )
        check(updatedRows == 1) { "Node or its Trace is missing or deleted: $id" }
    }

    override suspend fun deleteNode(id: String) {
        traceNodeDao.deleteNodeAndReorder(id, Instant.now())
    }
}
