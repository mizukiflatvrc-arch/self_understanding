package com.example.self_understanding.ui.trace

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.data.local.entity.TraceNodeEntity
import com.example.self_understanding.domain.model.OccurredAtUpdate
import com.example.self_understanding.domain.model.TraceNodeType
import com.example.self_understanding.domain.repository.TraceRepository
import com.example.self_understanding.ui.theme.Self_understandingTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: TraceViewModel
    private val store = ViewModelStore()

    private fun launch(repository: FakeRepository) {
        compose.runOnUiThread {
            model = TraceViewModel(repository)
            store.put("fixture", model)
        }
        compose.setContent { Self_understandingTheme { TraceApp(model) } }
    }

    @After fun closeModel() { compose.runOnUiThread { store.clear() } }

    @Test fun emptyListCreateDetailBackAndSelect() {
        val repository = FakeRepository()
        launch(repository)
        compose.onNodeWithText("Trace一覧").assertIsDisplayed()
        compose.onNodeWithText("Traceはまだありません").assertIsDisplayed()
        compose.onNodeWithText("新しいTrace").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("UI fixture")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("Trace詳細").assertIsDisplayed()
        compose.onNodeWithText("UI fixture").assertIsDisplayed()
        compose.onNodeWithText("一覧に戻る").performClick()
        compose.onNodeWithText("Trace一覧").assertIsDisplayed()
        compose.onNodeWithText("UI fixture").performClick()
        compose.onNodeWithText("Trace詳細").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Trace一覧").assertIsDisplayed()
        assertEquals(1, repository.createCalls)
    }

    @Test fun emptySavedTitleUsesDisplayFallbackAndDialogBackDoesNotSave() {
        val repository = FakeRepository()
        launch(repository)
        compose.onNodeWithText("新しいTrace").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Discarded fixture")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText("Trace一覧").assertIsDisplayed()
        assertEquals(0, repository.createCalls)
        compose.onNodeWithText("新しいTrace").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("無題のTrace").assertIsDisplayed()
        assertEquals("", repository.traces.value.single().title)
        compose.onNodeWithText("一覧に戻る").performClick()
        compose.onNodeWithText("無題のTrace").assertIsDisplayed()
    }

    @Test fun listErrorRetryAndMissingDeletedDetailsAreShownSafely() {
        val repository = FakeRepository().apply { failList = true }
        launch(repository)
        compose.onNodeWithText("一覧を読み込めませんでした").assertIsDisplayed()
        compose.runOnUiThread { repository.failList = false }
        compose.onNodeWithText("再試行").performClick()
        compose.onNodeWithText("Traceはまだありません").assertIsDisplayed()
        compose.runOnUiThread { model.selectTrace("missing") }
        compose.onNodeWithText("Traceが見つかりません").assertIsDisplayed()
        compose.onNodeWithText("一覧に戻る").performClick()
        compose.runOnUiThread {
            repository.traces.value = listOf(fixture("deleted").copy(deletedAt = Instant.EPOCH))
            model.selectTrace("deleted")
        }
        compose.onNodeWithText("Traceが見つかりません").assertIsDisplayed()
        compose.onNodeWithText("一覧に戻る").performClick()
        compose.onNodeWithText("Traceはまだありません").assertIsDisplayed()
    }

    @Test fun detailFailureRetryAndSaveFailureKeepDraft() {
        val repository = FakeRepository().apply {
            traces.value = listOf(fixture("existing"))
            failDetail = true
        }
        launch(repository)
        compose.onNodeWithText("Fixture").performClick()
        compose.onNodeWithText("Traceを読み込めませんでした").assertIsDisplayed()
        compose.runOnUiThread { repository.failDetail = false }
        compose.onNodeWithText("再試行").performClick()
        compose.onNodeWithText("Fixture").assertIsDisplayed()
        compose.onNodeWithText("一覧に戻る").performClick()
        compose.runOnUiThread { repository.failCreate = true }
        compose.onNodeWithText("新しいTrace").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Retry fixture")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存できませんでした。もう一度お試しください").assertIsDisplayed()
        compose.onNodeWithText("Retry fixture").assertIsDisplayed()
        compose.runOnUiThread { repository.failCreate = false }
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("Trace詳細").assertIsDisplayed()
        compose.onNodeWithText("Retry fixture").assertIsDisplayed()
    }

    @Test fun loadingAndSavingStatesDisableRepeatedActions() {
        val repository = FakeRepository().apply {
            listGate = CompletableDeferred()
            saveGate = CompletableDeferred()
        }
        launch(repository)
        compose.onNodeWithText("読み込み中").assertIsDisplayed()
        compose.runOnUiThread { repository.listGate!!.complete(Unit) }
        compose.onNodeWithText("Traceはまだありません").assertIsDisplayed()
        compose.onNodeWithText("新しいTrace").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("キャンセル").assertIsNotEnabled()
        compose.runOnUiThread { model.createTrace(); model.dismissCreate() }
        assertEquals(1, repository.createCalls)
        compose.runOnUiThread { repository.saveGate!!.complete(Unit) }
        compose.onNodeWithText("Trace詳細").assertIsDisplayed()
        assertEquals(1, repository.createCalls)
    }

    private fun fixture(id: String, title: String = "Fixture") = TraceEntity(
        id, title, Instant.EPOCH, null, Instant.EPOCH, Instant.EPOCH, null
    )

    private inner class FakeRepository : TraceRepository {
        val traces = MutableStateFlow<List<TraceEntity>>(emptyList())
        var failList = false
        var failDetail = false
        var failCreate = false
        var listGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var createCalls = 0
        override fun getAllActiveTraces(): Flow<List<TraceEntity>> = flow {
            if (failList) error("Synthetic list failure")
            listGate?.await()
            traces.collect { emit(it.filter { trace -> trace.deletedAt == null }) }
        }
        override suspend fun getTraceById(id: String): TraceEntity? {
            if (failDetail) error("Synthetic detail failure")
            return traces.value.firstOrNull { it.id == id && it.deletedAt == null }
        }
        override suspend fun createTrace(title: String): String {
            createCalls++
            saveGate?.await()
            if (failCreate) error("Synthetic save failure")
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
