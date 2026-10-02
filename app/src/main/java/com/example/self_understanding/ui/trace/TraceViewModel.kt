package com.example.self_understanding.ui.trace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.domain.repository.TraceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TraceListState {
    data object Loading : TraceListState
    data class Content(val traces: List<TraceEntity>) : TraceListState
    data object Error : TraceListState
}

sealed interface TraceDetailState {
    data object Loading : TraceDetailState
    data class Found(val trace: TraceEntity) : TraceDetailState
    data object Missing : TraceDetailState
    data object Error : TraceDetailState
}

data class TraceUiState(
    val listState: TraceListState = TraceListState.Loading,
    val detailState: TraceDetailState = TraceDetailState.Loading,
    val selectedTraceId: String? = null,
    val isCreateDialogOpen: Boolean = false,
    val title: String = "",
    val isSaving: Boolean = false,
    val createError: Boolean = false
)

class TraceViewModel(
    private val repository: TraceRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        TraceUiState(
            selectedTraceId = savedStateHandle[SELECTED_ID],
            isCreateDialogOpen = savedStateHandle[DIALOG_OPEN] ?: false,
            title = savedStateHandle[TITLE] ?: ""
        )
    )
    val uiState: StateFlow<TraceUiState> = mutableUiState.asStateFlow()
    private var listJob: Job? = null
    private var detailJob: Job? = null

    init {
        retryList()
        mutableUiState.value.selectedTraceId?.let(::loadDetail)
    }

    fun retryList() {
        listJob?.cancel()
        mutableUiState.update { it.copy(listState = TraceListState.Loading) }
        listJob = viewModelScope.launch {
            try {
                repository.getAllActiveTraces().collect { traces ->
                    mutableUiState.update { it.copy(listState = TraceListState.Content(traces)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(listState = TraceListState.Error) }
            }
        }
    }

    fun openCreate() {
        if (mutableUiState.value.isSaving) return
        savedStateHandle[DIALOG_OPEN] = true
        mutableUiState.update { it.copy(isCreateDialogOpen = true, createError = false) }
    }

    fun updateTitle(title: String) {
        if (mutableUiState.value.isSaving) return
        savedStateHandle[TITLE] = title
        mutableUiState.update { it.copy(title = title, createError = false) }
    }

    fun dismissCreate() {
        if (mutableUiState.value.isSaving) return
        savedStateHandle[DIALOG_OPEN] = false
        savedStateHandle[TITLE] = ""
        mutableUiState.update { it.copy(isCreateDialogOpen = false, title = "", createError = false) }
    }

    fun createTrace() {
        val current = mutableUiState.value
        if (current.isSaving || !current.isCreateDialogOpen) return
        // Set the guard before launching so consecutive taps cannot enqueue two saves.
        mutableUiState.update { it.copy(isSaving = true, createError = false) }
        viewModelScope.launch {
            try {
                val id = repository.createTrace(current.title.trim())
                savedStateHandle[DIALOG_OPEN] = false
                savedStateHandle[TITLE] = ""
                savedStateHandle[SELECTED_ID] = id
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        isCreateDialogOpen = false,
                        title = "",
                        selectedTraceId = id,
                        detailState = TraceDetailState.Loading
                    )
                }
                // Read the inserted row directly: the list Flow can still hold its previous value.
                loadDetail(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(isSaving = false, createError = true) }
            }
        }
    }

    fun selectTrace(id: String) {
        if (mutableUiState.value.isSaving) return
        savedStateHandle[SELECTED_ID] = id
        mutableUiState.update { it.copy(selectedTraceId = id) }
        loadDetail(id)
    }

    fun retryDetail() {
        mutableUiState.value.selectedTraceId?.let(::loadDetail)
    }

    private fun loadDetail(id: String) {
        detailJob?.cancel()
        mutableUiState.update { it.copy(detailState = TraceDetailState.Loading) }
        detailJob = viewModelScope.launch {
            try {
                val trace = repository.getTraceById(id)
                if (mutableUiState.value.selectedTraceId == id) {
                    mutableUiState.update {
                        it.copy(detailState = trace?.let { found -> TraceDetailState.Found(found) } ?: TraceDetailState.Missing)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (mutableUiState.value.selectedTraceId == id) {
                    mutableUiState.update { it.copy(detailState = TraceDetailState.Error) }
                }
            }
        }
    }

    fun goBack() {
        val current = mutableUiState.value
        if (current.isSaving) return
        if (current.isCreateDialogOpen) {
            dismissCreate()
        } else if (current.selectedTraceId != null) {
            detailJob?.cancel()
            savedStateHandle[SELECTED_ID] = null
            mutableUiState.update { it.copy(selectedTraceId = null, detailState = TraceDetailState.Loading) }
        }
    }

    companion object {
        private const val SELECTED_ID = "selectedTraceId"
        private const val DIALOG_OPEN = "createDialogOpen"
        private const val TITLE = "title"

        fun factory(repository: TraceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TraceViewModel(repository, createSavedStateHandle()) }
        }
    }
}
