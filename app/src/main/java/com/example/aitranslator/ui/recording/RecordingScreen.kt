package com.example.aitranslator.ui.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onNavigateToDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val formatTime: (Long) -> String = { totalSec ->
        val mins = totalSec / 60
        val secs = totalSec % 60
        String.format("%02d:%02d", mins, secs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Conversation", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        state.conversation?.let { conv ->
                            Text(
                                "${Language.getByCode(conv.sourceLanguage).name} → ${Language.getByCode(conv.targetLanguage).name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RECORDING SEGMENT #${state.currentSegmentNumber}",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatTime(state.totalElapsedSeconds),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Segment Progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${formatTime(state.segmentElapsedSeconds)} / ${formatTime(state.segmentTargetSeconds.toLong())}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Audio Waveform / Level Meter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (state.amplitude * 2.5f).coerceIn(0.05f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stop Button
            Button(
                onClick = {
                    viewModel.stopRecording {
                        state.conversation?.let { conv ->
                            onNavigateToDetail(conv.id)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("STOP CONVERSATION", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Segment Stream List
            Text(
                text = "Segments & Translation Queue",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.segments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Recording initial segment...\nSegments process automatically when completed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.segments.reversed(), key = { it.id }) { seg ->
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
}

@Composable
fun SegmentCard(
    segment: TranslationSegment,
    targetLanguage: String,
    onSpeak: (String) -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Segment #${segment.segmentNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                StatusBadge(status = segment.status)
            }

            if (segment.originalText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Speech: ${segment.originalText}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (segment.translatedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = segment.translatedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onSpeak(segment.translatedText) }) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Speak",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (segment.status == SegmentStatus.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = segment.errorMessage ?: "Failed to process",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                TextButton(onClick = onRetry) {
                    Text("Retry Segment")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: SegmentStatus) {
    val (bgColor, textColor, label) = when (status) {
        SegmentStatus.RECORDED -> Triple(Color(0xFFE0E0E0), Color.DarkGray, "QUEUED")
        SegmentStatus.UPLOADING -> Triple(Color(0xFFBBDEFB), Color(0xFF0D47A1), "UPLOADING")
        SegmentStatus.TRANSCRIBING -> Triple(Color(0xFFFFF9C4), Color(0xFFF57F17), "TRANSCRIBING")
        SegmentStatus.TRANSLATING -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "TRANSLATING")
        SegmentStatus.COMPLETED -> Triple(Color(0xFFC8E6C9), Color(0xFF1B5E20), "COMPLETED")
        SegmentStatus.FAILED -> Triple(Color(0xFFFFCDD2), Color(0xFFB71C1C), "FAILED")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
