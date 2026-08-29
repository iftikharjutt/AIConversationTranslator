package com.example.aitranslator.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showUrlDialog by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf("") }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val intervalOptions = listOf(
        10 to "10 seconds (Debug / Fast Testing)",
        30 to "30 seconds",
        60 to "60 seconds (1 minute)",
        180 to "180 seconds (3 minutes - Recommended)",
        300 to "300 seconds (5 minutes)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Audio Segmentation Section
            Text("Audio & Segmentation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIntervalDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Recording Interval", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = intervalOptions.find { it.first == state.segmentDuration }?.second ?: "${state.segmentDuration}s",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Debug Mode (10s segments)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Enables rapid 10-second segmentation for testing", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isDebugMode,
                            onCheckedChange = { viewModel.setDebugMode(it) }
                        )
                    }
                }
            }

            // Translation & Speech
            Text("Translation & TTS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Play Translation", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Automatically speak translated text when ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.autoPlayTts,
                            onCheckedChange = { viewModel.setAutoPlayTts(it) }
                        )
                    }
                }
            }

            // Privacy Section
            Text("Privacy & Storage", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save Audio Locally", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Store audio segment files on device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.saveAudio,
                            onCheckedChange = { viewModel.setSaveAudio(it) }
                        )
                    }
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-delete Audio", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Delete audio file immediately after successful translation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.deleteAudioAfterProcessing,
                            onCheckedChange = { viewModel.setDeleteAudioAfterProcessing(it) }
                        )
                    }
                }
            }

            // Gemini AI Translation Configuration
            Text("Gemini AI Engine (Recommended)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    var showApiKeyDialog by remember { mutableStateOf(false) }
                    var tempApiKey by remember { mutableStateOf("") }
                    var testStatusMessage by remember { mutableStateOf<String?>(null) }
                    var isTesting by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempApiKey = state.geminiApiKey
                                testStatusMessage = null
                                showApiKeyDialog = true
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Gemini API Key", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = if (state.geminiApiKey.isNotBlank()) "••••••••••••" + state.geminiApiKey.takeLast(4) else "Not configured (Required for AI translation)",
                                fontSize = 13.sp,
                                color = if (state.geminiApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = {
                            tempApiKey = state.geminiApiKey
                            testStatusMessage = null
                            showApiKeyDialog = true
                        }) {
                            Text(if (state.geminiApiKey.isNotBlank()) "Edit" else "Set Key")
                        }
                    }

                    Divider()

                    var showModelDialog by remember { mutableStateOf(false) }
                    val geminiModels = listOf(
                        "gemini-1.5-flash" to "Gemini 1.5 Flash (Fast & Recommended)",
                        "gemini-2.0-flash" to "Gemini 2.0 Flash (Next-Gen Fast)",
                        "gemini-1.5-pro" to "Gemini 1.5 Pro (High Reasoning)"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gemini Model", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = geminiModels.find { it.first == state.geminiModel }?.second ?: state.geminiModel,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.geminiApiKey.isNotBlank()) {
                        Divider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Test API Connection", fontSize = 14.sp)
                            Button(
                                onClick = {
                                    isTesting = true
                                    testStatusMessage = "Testing..."
                                    viewModel.testGeminiApiKey(state.geminiApiKey, state.geminiModel) { success, msg ->
                                        isTesting = false
                                        testStatusMessage = msg
                                    }
                                },
                                enabled = !isTesting
                            ) {
                                Text(if (isTesting) "Testing..." else "Test Connection")
                            }
                        }
                        if (testStatusMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = testStatusMessage!!,
                                fontSize = 12.sp,
                                color = if (testStatusMessage?.startsWith("Success") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (showApiKeyDialog) {
                        AlertDialog(
                            onDismissRequest = { showApiKeyDialog = false },
                            title = { Text("Set Gemini API Key") },
                            text = {
                                Column {
                                    Text(
                                        "Enter your Google Gemini API key to enable direct cloud AI speech translation without any local proxy server.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = tempApiKey,
                                        onValueChange = { tempApiKey = it },
                                        singleLine = true,
                                        label = { Text("Gemini API Key (AIza...)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.setGeminiApiKey(tempApiKey.trim())
                                        showApiKeyDialog = false
                                    }
                                ) {
                                    Text("Save Key")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showApiKeyDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showModelDialog) {
                        AlertDialog(
                            onDismissRequest = { showModelDialog = false },
                            title = { Text("Select Gemini Model") },
                            text = {
                                Column {
                                    geminiModels.forEach { (modelKey, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setGeminiModel(modelKey)
                                                    showModelDialog = false
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = state.geminiModel == modelKey,
                                                onClick = {
                                                    viewModel.setGeminiModel(modelKey)
                                                    showModelDialog = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(label, fontSize = 14.sp)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showModelDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }

            // Backend Server Configuration
            Text("Custom Proxy Server (Optional)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                backendUrlInput = state.backendUrl
                                showUrlDialog = true
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Fallback Proxy URL", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(state.backendUrl, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // About Section
            Text("About", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("AI Conversation Translator v1.0.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Continuous independent recording and contextual AI translation engine. All provider keys are securely maintained on the proxy backend.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Select Recording Interval") },
            text = {
                Column {
                    intervalOptions.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSegmentDuration(seconds)
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.segmentDuration == seconds,
                                onClick = {
                                    viewModel.setSegmentDuration(seconds)
                                    showIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Configure Backend URL") },
            text = {
                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    singleLine = true,
                    label = { Text("Base URL (e.g. http://10.0.2.2:3000/)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = if (backendUrlInput.endsWith("/")) backendUrlInput else "$backendUrlInput/"
                        viewModel.setBackendUrl(formatted)
                        showUrlDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
