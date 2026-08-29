package com.example.aitranslator.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sqrt

interface AudioSegmentListener {
    fun onSegmentCompleted(segmentNumber: Int, wavFile: File, startTime: Long, endTime: Long)
    fun onRecordingError(error: String)
}

class AudioRecorder(
    private val context: Context,
    private val segmentDurationSeconds: Int = Constants.DEFAULT_SEGMENT_DURATION_SECONDS
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioAmplitude = MutableSharedFlow<Float>(replay = 1)
    val audioAmplitude: SharedFlow<Float> = _audioAmplitude.asSharedFlow()

    private var currentSegmentNumber = 1
    private var segmentListener: AudioSegmentListener? = null
    private var conversationId: Long = 0

    fun setSegmentListener(listener: AudioSegmentListener) {
        this.segmentListener = listener
    }

    @SuppressLint("MissingPermission")
    fun startRecording(conversationId: Long) {
        if (_isRecording.value) return
        this.conversationId = conversationId
        this.currentSegmentNumber = 1

        val bufferSize = AudioRecord.getMinBufferSize(
            Constants.SAMPLE_RATE_HZ,
            Constants.CHANNEL_CONFIG_IN,
            Constants.AUDIO_FORMAT
        ).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                Constants.SAMPLE_RATE_HZ,
                Constants.CHANNEL_CONFIG_IN,
                Constants.AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                segmentListener?.onRecordingError("Microphone could not be initialized")
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = coroutineScope.launch {
                runRecordingLoop(bufferSize)
            }
        } catch (e: Exception) {
            segmentListener?.onRecordingError("Failed to start recording: ${e.message}")
            stopRecording()
        }
    }

    private suspend fun runRecordingLoop(bufferSize: Int) {
        val readBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        while (coroutineScope.isActive && _isRecording.value) {
            val segmentStartTime = System.currentTimeMillis()
            val tempPcmFile = File(FileUtils.getAudioSegmentsDirectory(context), "temp_conv_${conversationId}_seg_${currentSegmentNumber}.pcm")
            val targetWavFile = FileUtils.createSegmentAudioFile(context, conversationId, currentSegmentNumber)
            
            var pcmOutputStream: FileOutputStream? = null
            try {
                pcmOutputStream = FileOutputStream(tempPcmFile)
                val targetSampleCount = Constants.SAMPLE_RATE_HZ.toLong() * segmentDurationSeconds
                var totalSamplesWritten = 0L

                while (coroutineScope.isActive && _isRecording.value && totalSamplesWritten < targetSampleCount) {
                    val shortsRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (shortsRead > 0) {
                        // Calculate RMS amplitude for visualizer
                        var sum = 0.0
                        for (i in 0 until shortsRead) {
                            val sample = readBuffer[i]
                            // Convert Short to 16-bit Little Endian bytes
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / shortsRead).toFloat()
                        val normalizedAmp = (rms / 32767f).coerceIn(0f, 1f)
                        _audioAmplitude.emit(normalizedAmp)

                        pcmOutputStream.write(byteBuffer, 0, shortsRead * 2)
                        totalSamplesWritten += shortsRead
                    } else if (shortsRead == AudioRecord.ERROR_INVALID_OPERATION || shortsRead == AudioRecord.ERROR_BAD_VALUE) {
                        segmentListener?.onRecordingError("AudioRecord read error ($shortsRead)")
                        break
                    }
                }

                pcmOutputStream.flush()
                pcmOutputStream.close()
                pcmOutputStream = null

                val segmentEndTime = System.currentTimeMillis()

                if (tempPcmFile.exists() && tempPcmFile.length() > 0) {
                    // Convert PCM to WAV
                    FileUtils.convertPcmToWav(tempPcmFile, targetWavFile)
                    val segNum = currentSegmentNumber
                    currentSegmentNumber++
                    // Notify listener immediately while next loop iteration continues recording!
                    segmentListener?.onSegmentCompleted(segNum, targetWavFile, segmentStartTime, segmentEndTime)
                }
            } catch (e: Exception) {
                segmentListener?.onRecordingError("Recording stream error: ${e.message}")
            } finally {
                try {
                    pcmOutputStream?.close()
                } catch (_: IOException) {}
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
