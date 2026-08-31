package com.example.aitranslator.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.GeminiModelOption

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
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    var geminiTestMessage by remember { mutableStateOf<String?>(null) }
    var isGeminiTesting by remember { mutableStateOf(false) }

    var backendTestMessage by remember { mutableStateOf<String?>(null) }
    var isBackendTesting by remember { mutableStateOf(false) }

    var showBenchmarkDialog by remember { mutableStateOf(false) }
    var isBenchmarking by remember { mutableStateOf(false) }
    var benchmarkResults by remember { mutableStateOf<List<com.example.aitranslator.ui.settings.BenchmarkItem>?>(null) }

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
            // Translation Mode Selection Section (AUTO / ONLINE / OFFLINE)
            Text("Translation Engine Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Select how the app processes conversational translations:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    com.example.aitranslator.domain.model.TranslationMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTranslationMode(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.translationMode == mode,
                                onClick = { viewModel.setTranslationMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.displayName, fontWeight = if (state.translationMode == mode) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                Text(mode.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Offline Translation Engine Section
            Text("Offline On-Device Translation (Malay ↔ Urdu)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val activeModel = state.offlineModels.find { it.modelId == state.activeOfflineModelId } ?: state.offlineModels.firstOrNull()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeModel?.modelName ?: "NLLB-200 Malay ↔ Urdu (INT8)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Status: ${activeModel?.status?.name ?: "NOT DOWNLOADED"} • Size: ~${(activeModel?.totalSize ?: 548_000_000L) / 1024 / 1024} MB",
                                fontSize = 12.sp,
                                color = when (activeModel?.status) {
                                    com.example.aitranslator.domain.model.OfflineModelStatus.READY -> MaterialTheme.colorScheme.primary
                                    com.example.aitranslator.domain.model.OfflineModelStatus.DOWNLOADING -> MaterialTheme.colorScheme.secondary
                                    com.example.aitranslator.domain.model.OfflineModelStatus.CORRUPTED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "License: ${activeModel?.license ?: "CC-BY-NC 4.0"} • Works 100% offline with zero internet.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Download Progress Bar
                    if (state.downloadProgress.status == com.example.aitranslator.offline.DownloadStatus.DOWNLOADING ||
                        state.downloadProgress.status == com.example.aitranslator.offline.DownloadStatus.PREPARING ||
                        state.downloadProgress.status == com.example.aitranslator.offline.DownloadStatus.VERIFYING
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(state.downloadProgress.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("${state.downloadProgress.progress}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.pauseModelDownload() }) {
                                Text("Pause", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { viewModel.cancelModelDownload() }) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeModel?.status != com.example.aitranslator.domain.model.OfflineModelStatus.READY) {
                                Button(
                                    onClick = { activeModel?.let { viewModel.startModelDownload(it) } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Download Model")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        activeModel.let {
                                            viewModel.verifyModel(it.modelId) { _, _ -> }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Verify Integrity", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        viewModel.setTranslationMode(com.example.aitranslator.domain.model.TranslationMode.OFFLINE)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use Offline", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.scanOfflineModels() }) {
                                Text("Scan /Download Folder", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                isBenchmarking = true
                                showBenchmarkDialog = true
                                viewModel.runBenchmark { list ->
                                    isBenchmarking = false
                                    benchmarkResults = list
                                }
                            }) {
                                Text("Quality Benchmark", fontSize = 12.sp)
                            }
                            if (activeModel?.status == com.example.aitranslator.domain.model.OfflineModelStatus.READY) {
                                TextButton(
                                    onClick = { activeModel.let { viewModel.deleteModel(it.modelId) } },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Gemini AI Direct Mode Configuration Section
            Text("Google Gemini Direct AI Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // API Key Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiKeyDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini API Key", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.geminiApiKey.isNotBlank()) "••••••••••••" + state.geminiApiKey.takeLast(4) else "Not configured (Required for Direct AI)",
                                fontSize = 13.sp,
                                color = if (state.geminiApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = { showApiKeyDialog = true }) {
                            Text(if (state.geminiApiKey.isNotBlank()) "Edit" else "Set Key")
                        }
                    }

                    HorizontalDivider()

                    // Model Selection Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini Model", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val currentOption = Constants.GEMINI_MODELS.find { it.id == state.geminiModel }
                            Text(
                                text = currentOption?.let { "${it.name} (${it.id})" } ?: state.geminiModel,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showModelDialog = true }) {
                            Text("Change")
                        }
                    }

                    if (state.geminiApiKey.isNotBlank()) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Test Gemini Connection", fontSize = 14.sp)
                            Button(
                                onClick = {
                                    isGeminiTesting = true
                                    geminiTestMessage = "Connecting to Gemini..."
                                    viewModel.testGeminiApiKey(state.geminiApiKey, state.geminiModel) { success, msg ->
                                        isGeminiTesting = false
                                        geminiTestMessage = msg
                                    }
                                },
                                enabled = !isGeminiTesting
                            ) {
                                Text(if (isGeminiTesting) "Testing..." else "Test Connection")
                            }
                        }

                        if (geminiTestMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = geminiTestMessage!!,
                                fontSize = 12.sp,
                                color = if (geminiTestMessage?.startsWith("Successfully") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

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
                        TextButton(onClick = { showIntervalDialog = true }) {
                            Text("Change")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Debug Mode (10s segments)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Enables rapid 10-second segmentation for fast testing", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isDebugMode,
                            onCheckedChange = { viewModel.setDebugMode(it) }
                        )
                    }
                }
            }

            // Translation & Speech
            Text("Translation & Speech", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                            Text("Auto-Play Translation (TTS)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Automatically speak translated segments as they complete (Default: OFF)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Store audio segment files on device storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.saveAudio,
                            onCheckedChange = { viewModel.setSaveAudio(it) }
                        )
                    }
                    HorizontalDivider()
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

            // Backend Server Configuration (Optional Proxy)
            Text("Optional Backend Proxy Server", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Backend Server URL", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.backendUrl,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            backendUrlInput = state.backendUrl
                            showUrlDialog = true
                        }) {
                            Text("Edit")
                        }
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Backend Health Check", fontSize = 14.sp)
                        Button(
                            onClick = {
                                isBackendTesting = true
                                backendTestMessage = "Checking backend..."
                                viewModel.testBackendConnection { success, msg ->
                                    isBackendTesting = false
                                    backendTestMessage = msg
                                }
                            },
                            enabled = !isBackendTesting
                        ) {
                            Text(if (isBackendTesting) "Checking..." else "Test Connection")
                        }
                    }

                    if (backendTestMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = backendTestMessage!!,
                            fontSize = 12.sp,
                            color = if (backendTestMessage?.startsWith("Backend is reachable") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // About Section
            Text("About & Security", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("AI Conversation Translator v1.0.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Continuous gap-free recording and contextual AI translation engine. In direct mode, your API key is encrypted and stored locally on your device only.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Set Gemini API Key Dialog
    if (showApiKeyDialog) {
        var tempApiKey by remember { mutableStateOf(state.geminiApiKey) }
        var passwordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Configure Gemini API Key") },
            text = {
                Column {
                    Text(
                        "Enter your personal Google Gemini API Key. Direct audio processing and contextual translation are processed directly with Google Cloud.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        singleLine = true,
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle API Key Visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(tempApiKey.trim())
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                Row {
                    if (state.geminiApiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.clearGeminiApiKey()
                                showApiKeyDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Key")
                        }
                    }
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Select Gemini Model Dialog
    if (showModelDialog) {
        var customModelInput by remember { mutableStateOf("") }
        var accountModels by remember { mutableStateOf<List<GeminiModelOption>?>(null) }
        var isFetchingModels by remember { mutableStateOf(false) }
        var fetchStatusMessage by remember { mutableStateOf<String?>(null) }

        val displayedModels = accountModels ?: Constants.GEMINI_MODELS

        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select Gemini AI Model", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.geminiApiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                isFetchingModels = true
                                fetchStatusMessage = "Checking your Google account for eligible models..."
                                viewModel.fetchEligibleModels(state.geminiApiKey) { list, error ->
                                    isFetchingModels = false
                                    if (list != null && list.isNotEmpty()) {
                                        accountModels = list
                                        fetchStatusMessage = "Loaded ${list.size} eligible models from your Google account!"
                                    } else {
                                        fetchStatusMessage = error ?: "Could not query models list"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isFetchingModels
                        ) {
                            Text(if (isFetchingModels) "Checking Account..." else "Check Account for Eligible Models", fontSize = 12.sp)
                        }
                        if (fetchStatusMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fetchStatusMessage!!,
                                fontSize = 11.sp,
                                color = if (fetchStatusMessage?.startsWith("Loaded") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    displayedModels.forEach { modelOpt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setGeminiModel(modelOpt.id)
                                    showModelDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.geminiModel == modelOpt.id,
                                onClick = {
                                    viewModel.setGeminiModel(modelOpt.id)
                                    showModelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(modelOpt.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (modelOpt.isRecommended) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(Recommended)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(modelOpt.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("ID: ${modelOpt.id}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Or Custom Model ID:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        placeholder = { Text("e.g. gemini-2.5-flash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customModelInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                viewModel.setGeminiModel(customModelInput.trim())
                                showModelDialog = false
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply Custom Model")
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
                Column {
                    Text(
                        "Enter the URL of your AI Conversation Translator backend service.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = { backendUrlInput = it },
                        singleLine = true,
                        label = { Text("Base URL (e.g. http://10.0.2.2:3000/)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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

    if (showBenchmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBenchmarkDialog = false },
            title = { Text("Offline Quality Benchmark (40 Tests)") },
            text = {
                if (isBenchmarking) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Running 40 Malay ↔ Urdu sentences through on-device engine...", fontSize = 13.sp)
                    }
                } else {
                    val list = benchmarkResults ?: emptyList()
                    val avgLatency = if (list.isNotEmpty()) list.map { it.latencyMs }.average().toLong() else 0L
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Executed ${list.size} test cases • Average Latency: ${avgLatency} ms",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        list.forEachIndexed { idx, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        "#${idx + 1} (${item.sourceLang.uppercase()} → ${item.targetLang.uppercase()}) [${item.latencyMs}ms]",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Input: ${item.sourceText}", fontSize = 12.sp)
                                    Text("Output: ${item.translatedText}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBenchmarkDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
