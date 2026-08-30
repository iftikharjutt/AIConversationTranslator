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
    private val captureScope = CoroutineScope(Dispatchers.IO)
    private val finalizerScope = CoroutineScope(Dispatchers.IO)

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

            recordingJob = captureScope.launch {
                runContinuousCaptureLoop(bufferSize)
            }
        } catch (e: Exception) {
            segmentListener?.onRecordingError("Failed to start recording: ${e.message}")
            stopRecording()
        }
    }

    /**
     * Dedicated tight microphone capture loop.
     * ZERO pause between segments — PCM-to-WAV conversion and WorkManager dispatch
     * are offloaded asynchronously to finalizerScope without blocking AudioRecord reads.
     */
    private suspend fun runContinuousCaptureLoop(bufferSize: Int) {
        val readBuffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)
        val targetSampleCount = Constants.SAMPLE_RATE_HZ.toLong() * segmentDurationSeconds

        var currentPcmStream: FileOutputStream? = null
        var currentTempPcmFile: File? = null
        var currentTargetWavFile: File? = null
        var segmentStartTime = System.currentTimeMillis()
        var samplesWrittenInSegment = 0L

        fun openNewSegmentStream(segNum: Int) {
            val tempFile = File(FileUtils.getAudioSegmentsDirectory(context), "temp_conv_${conversationId}_seg_${segNum}.pcm")
            val targetWav = FileUtils.createSegmentAudioFile(context, conversationId, segNum)
            currentTempPcmFile = tempFile
            currentTargetWavFile = targetWav
            currentPcmStream = FileOutputStream(tempFile)
            segmentStartTime = System.currentTimeMillis()
            samplesWrittenInSegment = 0L
        }

        fun dispatchCurrentSegment(segNum: Int, tempFile: File, targetWav: File, startTime: Long, endTime: Long) {
            finalizerScope.launch {
                try {
                    if (tempFile.exists() && tempFile.length() > 0) {
                        FileUtils.convertPcmToWav(tempFile, targetWav)
                        segmentListener?.onSegmentCompleted(segNum, targetWav, startTime, endTime)
                    }
                } catch (e: Exception) {
                    segmentListener?.onRecordingError("Error finalizing segment $segNum: ${e.message}")
                }
            }
        }

        try {
            openNewSegmentStream(currentSegmentNumber)

            while (captureScope.isActive && _isRecording.value) {
                val shortsRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1

                if (shortsRead > 0) {
                    // 1. Calculate RMS amplitude for real-time visualizer
                    var sum = 0.0
                    for (i in 0 until shortsRead) {
                        val sample = readBuffer[i]
                        byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / shortsRead).toFloat()
                    val normalizedAmp = (rms / 32767f).coerceIn(0f, 1f)
                    _audioAmplitude.emit(normalizedAmp)

                    // 2. Determine boundary split across segments if necessary
                    val remainingInSegment = targetSampleCount - samplesWrittenInSegment

                    if (shortsRead < remainingInSegment) {
                        // Entire buffer fits within the current segment
                        currentPcmStream?.write(byteBuffer, 0, shortsRead * 2)
                        samplesWrittenInSegment += shortsRead
                    } else {
                        // Buffer crosses or meets the segment boundary
                        val samplesForCurrent = remainingInSegment.toInt()
                        val bytesForCurrent = samplesForCurrent * 2

                        // Write samples belonging to current segment
                        if (bytesForCurrent > 0) {
                            currentPcmStream?.write(byteBuffer, 0, bytesForCurrent)
                        }

                        // Finalize current segment immediately and non-blockingly
                        currentPcmStream?.flush()
                        currentPcmStream?.close()
                        val finalizedSegNum = currentSegmentNumber
                        val finalizedTempFile = currentTempPcmFile
                        val finalizedTargetWav = currentTargetWavFile
                        val segmentEndTime = System.currentTimeMillis()

                        if (finalizedTempFile != null && finalizedTargetWav != null) {
                            dispatchCurrentSegment(
                                finalizedSegNum,
                                finalizedTempFile,
                                finalizedTargetWav,
                                segmentStartTime,
                                segmentEndTime
                            )
                        }

                        // Immediately advance to next segment and start writing remainder without dropping samples!
                        currentSegmentNumber++
                        openNewSegmentStream(currentSegmentNumber)

                        val remainderSamples = shortsRead - samplesForCurrent
                        val remainderBytes = remainderSamples * 2
                        if (remainderBytes > 0) {
                            currentPcmStream?.write(byteBuffer, bytesForCurrent, remainderBytes)
                            samplesWrittenInSegment += remainderSamples
                        }
                    }
                } else if (shortsRead == AudioRecord.ERROR_INVALID_OPERATION || shortsRead == AudioRecord.ERROR_BAD_VALUE) {
                    segmentListener?.onRecordingError("AudioRecord read error ($shortsRead)")
                    break
                }
            }
        } catch (e: Exception) {
            segmentListener?.onRecordingError("Continuous recording stream error: ${e.message}")
        } finally {
            try {
                currentPcmStream?.flush()
                currentPcmStream?.close()
                // If there's an active in-flight segment with data on stop, finalize it
                val lastTemp = currentTempPcmFile
                val lastWav = currentTargetWavFile
                if (lastTemp != null && lastWav != null && lastTemp.exists() && lastTemp.length() > 0) {
                    dispatchCurrentSegment(
                        currentSegmentNumber,
                        lastTemp,
                        lastWav,
                        segmentStartTime,
                        System.currentTimeMillis()
                    )
                }
            } catch (_: IOException) {}
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
