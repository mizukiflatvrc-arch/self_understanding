package com.example.self_understanding.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.self_understanding.data.local.AppDatabase
import com.example.self_understanding.data.repository.TraceRepositoryImpl
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TracePersistenceTest {
    @Test
    fun activeTracesSurviveDatabaseCloseAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase2-persistence-test-" + UUID.randomUUID() + ".db"
        try {
            val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val visibleId: String
            val deletedId: String
            try {
                val repository = TraceRepositoryImpl(first.traceDao(), first.traceNodeDao())
                visibleId = repository.createTrace("Persistence fixture")
                deletedId = repository.createTrace("Deleted fixture")
                repository.deleteTrace(deletedId)
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val repository = TraceRepositoryImpl(reopened.traceDao(), reopened.traceNodeDao())
                val traces = repository.getAllActiveTraces().first()
                assertEquals(listOf(visibleId), traces.map { it.id })
                val saved = requireNotNull(repository.getTraceById(visibleId))
                assertEquals("Persistence fixture", saved.title)
                assertNull(saved.deletedAt)
                assertNull(repository.getTraceById(deletedId))
                assertEquals(1, reopened.openHelper.readableDatabase.version)
            } finally { reopened.close() }
        } finally {
            // Remove this unique test DB only; leave the application's DB untouched.
            context.deleteDatabase(name)
        }
    }
}
