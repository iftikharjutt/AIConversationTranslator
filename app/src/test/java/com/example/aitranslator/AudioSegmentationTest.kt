package com.example.aitranslator

import com.example.aitranslator.util.FileUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
}
