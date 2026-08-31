package com.example.aitranslator.offline

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs a local ONNX Runtime load check. This intentionally does not claim
 * that a model is translation-ready merely because the files exist.
 */
@Singleton
class OnnxModelValidator @Inject constructor() {
    fun validate(modelDir: File): Result<String> = runCatching {
        val encoder = File(modelDir, "encoder_model_int8.onnx")
        val decoder = File(modelDir, "decoder_with_past_model_int8.onnx")
        require(encoder.isFile && encoder.length() > 0) { "encoder_model_int8.onnx is missing or empty" }
        require(decoder.isFile && decoder.length() > 0) { "decoder_with_past_model_int8.onnx is missing or empty" }
        require(File(modelDir, "tokenizer.json").isFile) { "tokenizer.json is missing" }

        val env = OrtEnvironment.getEnvironment()
        env.createSession(encoder.absolutePath, OrtSession.SessionOptions()).use { encoderSession ->
            require(encoderSession.inputNames.isNotEmpty()) { "Encoder has no inputs" }
            require(encoderSession.outputNames.isNotEmpty()) { "Encoder has no outputs" }
        }
        env.createSession(decoder.absolutePath, OrtSession.SessionOptions()).use { decoderSession ->
            require(decoderSession.inputNames.isNotEmpty()) { "Decoder has no inputs" }
            require(decoderSession.outputNames.isNotEmpty()) { "Decoder has no outputs" }
        }
        "ONNX model files load successfully in ONNX Runtime"
    }
}
