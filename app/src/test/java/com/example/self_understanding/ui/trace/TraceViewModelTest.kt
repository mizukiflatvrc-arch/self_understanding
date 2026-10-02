package com.example.self_understanding.ui.trace

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.domain.model.OccurredAtUpdate
import com.example.self_understanding.domain.model.TraceNodeType
import com.example.self_understanding.domain.repository.TraceRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        stores.clear()
        Dispatchers.resetMain()
    }

    @Test fun listDistinguishesLoadingEmptyUpdatesAndErrorRetry() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        assertEquals(TraceListState.Loading, model.uiState.value.listState)
        runCurrent()
        assertTrue((model.uiState.value.listState as TraceListState.Content).traces.isEmpty())
        val trace = fixture("active")
        repository.traces.value = listOf(trace)
        runCurrent()
        assertEquals(listOf(trace), (model.uiState.value.listState as TraceListState.Content).traces)
        repository.failList = true
        model.retryList()
        runCurrent()
        assertEquals(TraceListState.Error, model.uiState.value.listState)
        repository.failList = false
        model.retryList()
        runCurrent()
        assertEquals(listOf(trace), (model.uiState.value.listState as TraceListState.Content).traces)
    }

    @Test fun createTrimsTitleAndSelectsSavedTrace() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        runCurrent()
        model.openCreate()
        model.updateTitle("  Fixture title  ")
        model.createTrace()
        runCurrent()
        assertEquals(listOf("Fixture title"), repository.createdTitles)
        assertEquals("created-1", model.uiState.value.selectedTraceId)
        assertEquals("Fixture title", (model.uiState.value.detailState as TraceDetailState.Found).trace.title)
        assertFalse(model.uiState.value.isCreateDialogOpen)
        assertFalse(model.uiState.value.isSaving)
        assertEquals(listOf("created-1"), (model.uiState.value.listState as TraceListState.Content).traces.map { it.id })
        model.goBack()
        assertNull(model.uiState.value.selectedTraceId)
    }

    @Test fun emptyTitleRemainsEmptyInStorage() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.openCreate()
        model.updateTitle("  ")
        model.createTrace()
        runCurrent()
        assertEquals(listOf(""), repository.createdTitles)
    }

    @Test fun pendingSaveRejectsDuplicateAndDismissAndFailureKeepsDraftForRetry() = runTest(dispatcher) {
        val repository = FakeRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        repository.failCreate = true
        val model = model(repository)
        model.openCreate()
        model.updateTitle("Retry fixture")
        model.createTrace()
        model.createTrace()
        runCurrent()
        assertEquals(1, repository.createCalls)
        assertTrue(model.uiState.value.isSaving)
        model.dismissCreate()
        assertTrue(model.uiState.value.isCreateDialogOpen)
        gate.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
        assertTrue(model.uiState.value.createError)
        assertTrue(model.uiState.value.isCreateDialogOpen)
        assertEquals("Retry fixture", model.uiState.value.title)
        assertNull(model.uiState.value.selectedTraceId)
        repository.failCreate = false
        model.createTrace()
        runCurrent()
        assertEquals(2, repository.createCalls)
        assertEquals("created-2", model.uiState.value.selectedTraceId)
        assertFalse(model.uiState.value.createError)
    }

    @Test fun missingDeletedAndDetailFailureAreSafeAndRetryable() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        model.selectTrace("missing")
        runCurrent()
        assertEquals(TraceDetailState.Missing, model.uiState.value.detailState)
        repository.traces.value = listOf(fixture("deleted").copy(deletedAt = Instant.EPOCH))
        model.selectTrace("deleted")
        runCurrent()
        assertEquals(TraceDetailState.Missing, model.uiState.value.detailState)
        repository.failDetail = true
        model.selectTrace("active")
        runCurrent()
        assertEquals(TraceDetailState.Error, model.uiState.value.detailState)
        repository.failDetail = false
        repository.traces.value = listOf(fixture("active"))
        model.retryDetail()
        runCurrent()
        assertEquals("active", (model.uiState.value.detailState as TraceDetailState.Found).trace.id)
    }

    @Test fun savedSelectionAndDraftRestoreWithoutReplayingCreate() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.traces.value = listOf(fixture("selected"))
        val restored = model(repository, SavedStateHandle(mapOf(
            "selectedTraceId" to "selected", "createDialogOpen" to true, "title" to "Unsaved fixture"
        )))
        runCurrent()
        assertEquals("selected", restored.uiState.value.selectedTraceId)
        assertEquals("selected", (restored.uiState.value.detailState as TraceDetailState.Found).trace.id)
        assertEquals("Unsaved fixture", restored.uiState.value.title)
        assertTrue(restored.uiState.value.isCreateDialogOpen)
        assertFalse(restored.uiState.value.isSaving)
        assertEquals(0, repository.createCalls)
    }

    private fun model(repository: TraceRepository, state: SavedStateHandle = SavedStateHandle()): TraceViewModel {
        val model = TraceViewModel(repository, state)
        stores += ViewModelStore().also { it.put("fixture", model) }
        return model
    }

    private fun fixture(id: String, title: String = "Fixture") = TraceEntity(
        id, title, Instant.EPOCH, null, Instant.EPOCH, Instant.EPOCH, null
    )

    private inner class FakeRepository : TraceRepository {
        val traces = MutableStateFlow<List<TraceEntity>>(emptyList())
        var failList = false
        var failDetail = false
        var failCreate = false
        var createGate: CompletableDeferred<Unit>? = null
        var createCalls = 0
        val createdTitles = mutableListOf<String>()
        override fun getAllActiveTraces(): Flow<List<TraceEntity>> = flow {
            if (failList) error("Synthetic list failure")
            traces.collect { emit(it.filter { trace -> trace.deletedAt == null }) }
        }
        override suspend fun getTraceById(id: String): TraceEntity? {
            if (failDetail) error("Synthetic detail failure")
            return traces.value.firstOrNull { it.id == id && it.deletedAt == null }
        }
        override suspend fun createTrace(title: String): String {
            createCalls++
            createGate?.await()
            if (failCreate) error("Synthetic save failure")
            createdTitles += title
            val trace = fixture("created-" + createCalls, title)
            traces.value = listOf(trace) + traces.value
            return trace.id
        }
        override suspend fun endTrace(id: String) = error("Outside Phase 2")
        override suspend fun deleteTrace(id: String) = error("Outside Phase 2")
        override fun getNodesForTrace(traceId: String): Flow<List<TraceNodeEntity>> = error("Outside Phase 2")
        override suspend fun addNode(traceId: String, type: TraceNodeType, content: String, occurredAt: Instant?, sequence: Int?) = error("Outside Phase 2")
        override suspend fun updateNode(id: String, type: TraceNodeType, content: String, occurredAtUpdate: OccurredAtUpdate) = error("Outside Phase 2")
        override suspend fun deleteNode(id: String) = error("Outside Phase 2")
    }
}
