package com.example.self_understanding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.self_understanding.ui.theme.Self_understandingTheme
import com.example.self_understanding.ui.trace.TraceApp
import com.example.self_understanding.ui.trace.TraceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SelfUnderstandingApplication
        setContent {
            Self_understandingTheme {
                var initializationAttempt by remember { mutableIntStateOf(0) }
                val repository = remember(initializationAttempt) {
                    try {
                        app.traceRepository
                    } catch (_: Exception) {
                        null
                    }
                }
                if (repository != null) {
                    val factory = remember(repository) { TraceViewModel.factory(repository) }
                    val traceViewModel: TraceViewModel = viewModel(factory = factory)
                    TraceApp(traceViewModel)
                } else {
                    Scaffold { padding ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                        ) {
                            Text(stringResource(R.string.initialization_error))
                            Button(onClick = { initializationAttempt++ }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}
