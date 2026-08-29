package com.example.aitranslator.util

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {

    fun getAudioSegmentsDirectory(context: Context): File {
        val dir = File(context.filesDir, "audio_segments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createSegmentAudioFile(context: Context, conversationId: Long, segmentNumber: Int): File {
        val dir = getAudioSegmentsDirectory(context)
        return File(dir, "conv_${conversationId}_seg_${segmentNumber}.wav")
    }

    fun deleteAudioFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        val file = File(filePath)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Converts raw PCM 16-bit 16kHz mono audio into a valid RIFF/WAV file.
     */
    fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = Constants.SAMPLE_RATE_HZ,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ) {
        val rawAudioSize = pcmFile.length()
        val totalDataLen = rawAudioSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        FileInputStream(pcmFile).use { input ->
            FileOutputStream(wavFile).use { output ->
                // Write WAV Header
                writeWavHeader(output, totalDataLen, rawAudioSize, sampleRate.toLong(), channels, byteRate.toLong(), bitsPerSample)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        // Remove temp PCM file after successful WAV creation
        if (pcmFile.exists() && pcmFile.absolutePath != wavFile.absolutePath) {
            pcmFile.delete()
        }
    }

    @Throws(IOException::class)
    private fun writeWavHeader(
        out: FileOutputStream,
        totalDataLen: Long,
        totalAudioLen: Long,
        longSampleRate: Long,
        channels: Short,
        byteRate: Long,
        bitsPerSample: Short
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}
