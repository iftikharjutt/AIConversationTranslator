package com.example.aitranslator.ui.translation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.ui.recording.SegmentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.conversation?.title ?: "Conversation Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        state.conversation?.let { conv ->
                            Text(
                                "${Language.getByCode(conv.sourceLanguage).name} → ${Language.getByCode(conv.targetLanguage).name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        titleInput = state.conversation?.title.orEmpty()
                        showEditTitleDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Title")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (state.segments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No translation segments found in this conversation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.segments, key = { it.id }) { seg ->
                        SegmentCard(
                            segment = seg,
                            targetLanguage = state.conversation?.targetLanguage ?: "en",
                            onSpeak = { text ->
                                viewModel.speakTranslation(text, state.conversation?.targetLanguage ?: "en")
                            },
                            onRetry = { viewModel.retrySegment(seg.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            viewModel.updateTitle(titleInput.trim())
                        }
                        showEditTitleDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
