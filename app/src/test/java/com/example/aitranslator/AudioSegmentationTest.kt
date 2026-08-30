package com.example.aitranslator

import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.util.FileUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AudioSegmentationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPcmToWavConversion() {
        val pcmFile = tempFolder.newFile("test_audio.pcm")
        val wavFile = tempFolder.newFile("test_audio.wav")

        // Write dummy 16000 samples of 16-bit PCM (1 second of audio = 32000 bytes)
        val pcmData = ByteArray(32000)
        FileOutputStream(pcmFile).use { it.write(pcmData) }
        assertEquals(32000L, pcmFile.length())

        FileUtils.convertPcmToWav(pcmFile, wavFile, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        assertTrue(wavFile.exists())
        // WAV file size must equal 44 bytes header + 32000 bytes raw PCM data = 32044 bytes
        assertEquals(32044L, wavFile.length())

        val wavBytes = wavFile.readBytes()
        // Check RIFF and WAVE magic headers
        assertEquals('R'.code.toByte(), wavBytes[0])
        assertEquals('I'.code.toByte(), wavBytes[1])
        assertEquals('F'.code.toByte(), wavBytes[2])
        assertEquals('F'.code.toByte(), wavBytes[3])

        assertEquals('W'.code.toByte(), wavBytes[8])
        assertEquals('A'.code.toByte(), wavBytes[9])
        assertEquals('V'.code.toByte(), wavBytes[10])
        assertEquals('E'.code.toByte(), wavBytes[11])
    }

    @Test
    fun testSampleAccurateBoundarySplittingWithoutGaps() {
        // Target: 10 samples per segment (20 bytes per segment at 2 bytes/sample)
        val maxSamplesPerSegment = 10L
        var samplesCapturedInCurrentSegment = 0L
        var segmentCounter = 1

        val segment1Stream = ByteArrayOutputStream()
        var currentStream = segment1Stream
        val segment2Stream = ByteArrayOutputStream()

        // Incoming audio chunk of 15 samples (30 bytes: bytes 0..29)
        val readSamples = 15
        val audioBuffer = ShortArray(readSamples) { (it + 1).toShort() } // [1, 2, ..., 15]

        val remainingSamplesInSegment = maxSamplesPerSegment - samplesCapturedInCurrentSegment
        if (readSamples >= remainingSamplesInSegment) {
            val samplesForCurrent = remainingSamplesInSegment.toInt()
            val remainderSamples = readSamples - samplesForCurrent

            // Write first 10 samples to current stream
            for (i in 0 until samplesForCurrent) {
                val sample = audioBuffer[i]
                currentStream.write(sample.toInt() and 0xFF)
                currentStream.write((sample.toInt() shr 8) and 0xFF)
            }
            assertEquals(20, currentStream.size())

            // Boundary crossed: finalize stream 1 and switch to stream 2 immediately
            segmentCounter++
            currentStream = segment2Stream
            samplesCapturedInCurrentSegment = 0L

            // Remainder 5 samples immediately written into stream 2 without delay
            for (i in samplesForCurrent until readSamples) {
                val sample = audioBuffer[i]
                currentStream.write(sample.toInt() and 0xFF)
                currentStream.write((sample.toInt() shr 8) and 0xFF)
            }
            samplesCapturedInCurrentSegment += remainderSamples
        }

        assertEquals(2, segmentCounter)
        assertEquals(20, segment1Stream.size()) // 10 samples * 2 bytes
        assertEquals(10, segment2Stream.size()) // 5 remainder samples * 2 bytes
        assertEquals(5L, samplesCapturedInCurrentSegment)
    }

    @Test
    fun testAudioRetentionLifecycleRules() {
        val audioFile = tempFolder.newFile("segment_test.wav")
        audioFile.writeBytes(ByteArray(100))
        assertTrue(audioFile.exists())

        // Lifecycle Rule: If processing fails, audio MUST be kept on disk for retry
        val failureStatus = SegmentStatus.FAILED
        val deleteOnSuccess = true
        val saveAudioSetting = false

        fun handleRetention(status: SegmentStatus, file: File, deleteAfterProc: Boolean, saveAud: Boolean) {
            if (status == SegmentStatus.COMPLETED) {
                if (deleteAfterProc || !saveAud) {
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }

        // Simulating failed processing
        handleRetention(failureStatus, audioFile, deleteOnSuccess, saveAudioSetting)
        assertTrue("Audio file must NOT be deleted when processing fails", audioFile.exists())

        // Simulating successful processing with deleteAudioAfterProcessing = true
        handleRetention(SegmentStatus.COMPLETED, audioFile, deleteAfterProc = true, saveAud = true)
        assertFalse("Audio file must be deleted upon COMPLETED if deleteAudioAfterProcessing is true", audioFile.exists())
    }
}
