package com.example.self_understanding.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.self_understanding.data.local.AppDatabase
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.data.repository.TraceRepositoryImpl
import com.example.self_understanding.domain.model.OccurredAtUpdate
import com.example.self_understanding.domain.model.TraceNodeType
import com.example.self_understanding.domain.repository.TraceRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: TraceRepository

    private fun repository(db: AppDatabase): TraceRepository =
        TraceRepositoryImpl(db.traceDao(), db.traceNodeDao())

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = repository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createsTracesAndRestoresEveryNodeTypeIncludingUnknown() = runBlocking {
        val id = repository.createTrace("")
        assertEquals(id, UUID.fromString(id).toString())
        val trace = requireNotNull(repository.getTraceById(id))
        assertEquals("", trace.title)
        assertNull(trace.endedAt)
        assertNull(trace.deletedAt)
        assertEquals(listOf(id), repository.getAllActiveTraces().first().map { it.id })
        assertEquals(7, TraceNodeType.entries.size)
        for (type in TraceNodeType.entries) repository.addNode(id, type, "", null)
        val nodes = repository.getNodesForTrace(id).first()
        assertEquals(TraceNodeType.entries.toList(), nodes.map { it.type })
        assertEquals((1..7).toList(), nodes.map { it.sequence })
        for (node in nodes) {
            assertEquals(node.id, UUID.fromString(node.id).toString())
            assertEquals(node, database.traceNodeDao().getNodeById(node.id))
            assertNull(node.occurredAt)
            assertNull(node.deletedAt)
        }
    }

    @Test
    fun occurrenceTimeCanBeKeptSetAndCleared() = runBlocking {
        val traceId = repository.createTrace("time")
        val time = Instant.parse("2026-01-02T03:04:05.123Z")
        repository.addNode(traceId, TraceNodeType.UNKNOWN, "before", time)
        val id = repository.getNodesForTrace(traceId).first().single().id
        repository.updateNode(id, TraceNodeType.THOUGHT, "keep")
        assertEquals(time, database.traceNodeDao().getNodeById(id)?.occurredAt)
        repository.updateNode(id, TraceNodeType.EMOTION, "keep", OccurredAtUpdate.Keep)
        assertEquals(time, database.traceNodeDao().getNodeById(id)?.occurredAt)
        repository.updateNode(id, TraceNodeType.UNKNOWN, "clear", OccurredAtUpdate.Set(null))
        assertNull(database.traceNodeDao().getNodeById(id)?.occurredAt)
        val replacement = time.plusSeconds(60)
        repository.updateNode(id, TraceNodeType.ACTION, "set", OccurredAtUpdate.Set(replacement))
        val node = requireNotNull(database.traceNodeDao().getNodeById(id))
        assertEquals(replacement, node.occurredAt)
        assertEquals("set", node.content)
        assertEquals(TraceNodeType.ACTION, node.type)
        assertEquals(1, node.sequence)
    }

    @Test
    fun insertsAtBeginningMiddleAndEndWithoutChangingOtherTrace() = runBlocking {
        val id = repository.createTrace("ordering")
        val otherId = repository.createTrace("other")
        val late = Instant.parse("2026-01-02T00:00:00Z")
        val early = late.minusSeconds(3600)
        repository.addNode(otherId, TraceNodeType.BODY, "other", null)
        val otherBefore = repository.getNodesForTrace(otherId).first()
        repository.addNode(id, TraceNodeType.THOUGHT, "B", late)
        repository.addNode(id, TraceNodeType.UNKNOWN, "A", null, 1)
        repository.addNode(id, TraceNodeType.ACTION, "C", early, 3)
        repository.addNode(id, TraceNodeType.EMOTION, "between", null, 2)
        val before = repository.getNodesForTrace(id).first()
        assertEquals(listOf("A", "between", "B", "C"), before.map { it.content })
        assertEquals(listOf(1, 2, 3, 4), before.map { it.sequence })
        assertEquals(listOf(null, null, late, early), before.map { it.occurredAt })
        assertEquals(otherBefore, repository.getNodesForTrace(otherId).first())
        for (sequence in listOf(-1, 0, 6)) {
            expectFailure<IllegalArgumentException> {
                repository.addNode(id, TraceNodeType.UNKNOWN, "invalid", null, sequence)
            }
            assertEquals(before, repository.getNodesForTrace(id).first())
        }
    }

    @Test
    fun logicalNodeDeletionRetainsRowAndReordersActiveNodes() = runBlocking {
        val id = repository.createTrace("delete")
        val otherId = repository.createTrace("other")
        repository.addNode(otherId, TraceNodeType.RESULT, "other", null)
        val otherBefore = repository.getNodesForTrace(otherId).first()
        for (content in listOf("A", "B", "C")) repository.addNode(id, TraceNodeType.UNKNOWN, content, null)
        val deletedId = repository.getNodesForTrace(id).first()[1].id
        repository.deleteNode(deletedId)
        val nodes = repository.getNodesForTrace(id).first()
        assertEquals(listOf("A", "C"), nodes.map { it.content })
        assertEquals(listOf(1, 2), nodes.map { it.sequence })
        assertNull(database.traceNodeDao().getNodeById(deletedId))
        database.openHelper.readableDatabase.query(
            "SELECT deletedAt, sequence FROM trace_nodes WHERE id = ?", arrayOf(deletedId)
        ).use {
            assertTrue(it.moveToFirst())
            assertFalse(it.isNull(0))
            assertEquals(2, it.getInt(1))
        }
        assertEquals(otherBefore, repository.getNodesForTrace(otherId).first())
        expectFailure<IllegalStateException> {
            repository.updateNode(deletedId, TraceNodeType.ACTION, "deleted edit")
        }
        expectFailure<IllegalArgumentException> {
            repository.addNode(id, TraceNodeType.UNKNOWN, "invalid after delete", null, 4)
        }
        assertEquals(nodes, repository.getNodesForTrace(id).first())
        repository.addNode(id, TraceNodeType.UNKNOWN, "D", null, 3)
        assertEquals(listOf("A", "C", "D"), repository.getNodesForTrace(id).first().map { it.content })
        repository.addNode(id, TraceNodeType.UNKNOWN, "E", null)
        assertEquals(listOf(1, 2, 3, 4), repository.getNodesForTrace(id).first().map { it.sequence })
    }

    @Test
    fun deletedParentHidesChildrenWithoutDeletingTheirRows() = runBlocking {
        val id = repository.createTrace("parent")
        repository.addNode(id, TraceNodeType.UNKNOWN, "child", null)
        val childId = repository.getNodesForTrace(id).first().single().id
        repository.deleteTrace(id)
        assertNull(repository.getTraceById(id))
        assertTrue(repository.getAllActiveTraces().first().isEmpty())
        assertTrue(repository.getNodesForTrace(id).first().isEmpty())
        assertNull(database.traceNodeDao().getNodeById(childId))
        database.openHelper.readableDatabase.query(
            "SELECT deletedAt FROM trace_nodes WHERE id = ?", arrayOf(childId)
        ).use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        database.openHelper.readableDatabase.query(
            "SELECT deletedAt FROM traces WHERE id = ?", arrayOf(id)
        ).use {
            assertTrue(it.moveToFirst())
            assertFalse(it.isNull(0))
        }
        expectFailure<IllegalStateException> { repository.addNode(id, TraceNodeType.UNKNOWN, "append", null) }
        expectFailure<IllegalStateException> { repository.addNode(id, TraceNodeType.UNKNOWN, "insert", null, 1) }
        expectFailure<IllegalStateException> { repository.updateNode(childId, TraceNodeType.ACTION, "edit") }
        expectFailure<IllegalStateException> {
            repository.updateNode(childId, TraceNodeType.ACTION, "clear", OccurredAtUpdate.Set(null))
        }
        expectFailure<IllegalStateException> {
            repository.addNode(UUID.randomUUID().toString(), TraceNodeType.UNKNOWN, "missing", null)
        }
        database.openHelper.readableDatabase.query(
            "SELECT content, sequence, deletedAt FROM trace_nodes WHERE id = ?", arrayOf(childId)
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("child", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertTrue(it.isNull(2))
        }
    }

    @Test
    fun failedInsertionRollsBackSequenceShift() = runBlocking {
        val id = repository.createTrace("rollback")
        repository.addNode(id, TraceNodeType.SITUATION, "A", null)
        repository.addNode(id, TraceNodeType.RESULT, "B", null)
        val before = repository.getNodesForTrace(id).first()
        expectFailure<SQLiteConstraintException> {
            database.traceNodeDao().insertNodeAtSequence(
                before[1].copy(sequence = 1, content = "duplicate ID"), Instant.now()
            )
        }
        assertEquals(before, repository.getNodesForTrace(id).first())
    }

    @Test
    fun concurrentAppendsProduceDistinctContiguousSequences() = runBlocking {
        val id = repository.createTrace("concurrent")
        coroutineScope {
            (1..20).map { index ->
                async(Dispatchers.Default) { repository.addNode(id, TraceNodeType.UNKNOWN, "node-$index", null) }
            }.awaitAll()
        }
        val nodes = repository.getNodesForTrace(id).first()
        assertEquals(20, nodes.size)
        assertEquals((1..20).toList(), nodes.map { it.sequence })
        assertEquals(20, nodes.map { it.id }.toSet().size)
        assertEquals((1..20).map { "node-$it" }.toSet(), nodes.map { it.content }.toSet())
    }

    @Test
    fun opensOriginalVersionOneDatabaseAndPreservesLegacyValuesOnReopen() = runBlocking {
        val name = "phase1-legacy-test-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        val legacyTypes = listOf(
            TraceNodeType.SITUATION, TraceNodeType.THOUGHT, TraceNodeType.EMOTION,
            TraceNodeType.BODY, TraceNodeType.ACTION, TraceNodeType.RESULT
        )
        try {
            // Original v1 DDL/hash: independent of the regenerated Room implementation.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null as SQLiteDatabase.CursorFactory?).use { sqlite ->
                sqlite.execSQL("CREATE TABLE traces (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)")
                sqlite.execSQL("CREATE TABLE trace_nodes (id TEXT NOT NULL PRIMARY KEY, traceId TEXT NOT NULL, sequence INTEGER NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, occurredAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, FOREIGN KEY(traceId) REFERENCES traces(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                sqlite.execSQL("CREATE INDEX index_trace_nodes_traceId_deletedAt_sequence ON trace_nodes(traceId, deletedAt, sequence)")
                sqlite.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                sqlite.execSQL("INSERT INTO room_master_table VALUES(42, 'cef9e5049c88a5a603a688da0d5e02c7')")
                sqlite.execSQL("INSERT INTO traces(id,title,startedAt,createdAt,updatedAt) VALUES(?, 'legacy', 1000, 1000, 1000)", arrayOf(id))
                legacyTypes.forEachIndexed { index, type ->
                    sqlite.execSQL(
                        "INSERT INTO trace_nodes(id,traceId,sequence,type,content,createdAt,updatedAt) VALUES(?,?,?,?,?,1000,1000)",
                        arrayOf<Any>(UUID.randomUUID().toString(), id, index + 1, type.name, type.name)
                    )
                }
                sqlite.version = 1
            }
            var before: List<TraceNodeEntity> = emptyList()
            val opened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val repo = repository(opened)
                assertEquals(Instant.ofEpochMilli(1000), repo.getTraceById(id)?.startedAt)
                before = repo.getNodesForTrace(id).first()
                assertEquals(legacyTypes, before.map { it.type })
                assertTrue(before.all { it.occurredAt == null })
                repo.addNode(id, TraceNodeType.UNKNOWN, "new", null)
            } finally {
                opened.close()
            }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val nodes = reopened.traceNodeDao().getNodesForTrace(id).first()
                assertEquals(before, nodes.take(legacyTypes.size))
                assertEquals(TraceNodeType.UNKNOWN, nodes.last().type)
                assertEquals(7, nodes.last().sequence)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline action: suspend () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.java.name}, got ${error::class.java.name}", error)
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }
}
