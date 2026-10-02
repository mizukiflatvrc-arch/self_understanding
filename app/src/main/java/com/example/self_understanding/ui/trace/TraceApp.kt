package com.example.self_understanding.ui.trace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.self_understanding.R
import com.example.self_understanding.data.local.entity.TraceEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceApp(viewModel: TraceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDetail = state.selectedTraceId != null
    BackHandler(enabled = isDetail || state.isCreateDialogOpen || state.isSaving) {
        viewModel.goBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isDetail) R.string.trace_detail else R.string.trace_list)) },
                navigationIcon = {
                    if (isDetail) {
                        TextButton(onClick = viewModel::goBack, enabled = !state.isSaving) {
                            Text(stringResource(R.string.back_to_list))
                        }
                    }
                },
                actions = {
                    if (!isDetail) {
                        TextButton(onClick = viewModel::openCreate, enabled = !state.isSaving) {
                            Text(stringResource(R.string.create_trace))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (isDetail) {
            TraceDetail(state.detailState, viewModel::retryDetail, modifier)
        } else {
            TraceList(state.listState, viewModel::selectTrace, viewModel::retryList, modifier)
        }
    }
    if (state.isCreateDialogOpen) {
        CreateTraceDialog(state, viewModel::updateTitle, viewModel::createTrace, viewModel::dismissCreate)
    }
}

@Composable
private fun TraceList(
    state: TraceListState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        TraceListState.Loading -> Loading(modifier)
        TraceListState.Error -> ErrorMessage(R.string.trace_list_error, onRetry, modifier)
        is TraceListState.Content -> {
            if (state.traces.isEmpty()) {
                StatusColumn(modifier) {
                    Text(stringResource(R.string.no_traces))
                    Text(stringResource(R.string.no_traces_hint), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val openLabel = stringResource(R.string.open_trace)
                LazyColumn(modifier) {
                    items(state.traces, key = { it.id }) { trace ->
                        ListItem(
                            headlineContent = { Text(displayTitle(trace.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(startedAtText(trace.startedAt)) },
                            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = openLabel) { onSelect(trace.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceDetail(state: TraceDetailState, onRetry: () -> Unit, modifier: Modifier) {
    when (state) {
        TraceDetailState.Loading -> Loading(modifier)
        TraceDetailState.Error -> ErrorMessage(R.string.trace_detail_error, onRetry, modifier)
        TraceDetailState.Missing -> StatusColumn(modifier) {
            Text(stringResource(R.string.trace_missing), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        is TraceDetailState.Found -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(displayTitle(state.trace.title), style = MaterialTheme.typography.headlineSmall)
            Text(startedAtText(state.trace.startedAt), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CreateTraceDialog(
    state: TraceUiState,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(stringResource(R.string.create_trace)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.trace_title)) },
                    supportingText = { Text(stringResource(R.string.trace_title_hint)) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.createError) {
                    Text(
                        stringResource(R.string.trace_create_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                if (state.isSaving) {
                    Text(stringResource(R.string.saving_trace), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) { Text(stringResource(R.string.save_trace)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun Loading(modifier: Modifier) {
    StatusColumn(modifier) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_traces))
    }
}

@Composable
private fun ErrorMessage(message: Int, onRetry: () -> Unit, modifier: Modifier) {
    StatusColumn(modifier) {
        Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun StatusColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) { content() }
}

@Composable
private fun displayTitle(title: String): String = title.ifBlank { stringResource(R.string.untitled_trace) }

@Composable
private fun startedAtText(instant: Instant): String {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
    }
    return stringResource(R.string.trace_started_at, formatter.format(instant))
}
