package com.example.aitranslator.offline.nllb

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

class NllbDecoder(
    private val env: OrtEnvironment,
    private val session: OrtSession
) {

    fun generate(
        encoderHiddenStates: OnnxTensor?,
        encoderAttentionMask: LongArray,
        decoderPrompt: LongArray,
        config: NllbGenerationConfig
    ): List<Long> {
        val generated = decoderPrompt.toMutableList()
        val encMaskShape = longArrayOf(1, encoderAttentionMask.size.toLong())
        val encMaskBuffer = LongBuffer.wrap(encoderAttentionMask)
        val encMaskTensor = OnnxTensor.createTensor(env, encMaskBuffer, encMaskShape)

        try {
            var step = 0
            while (step < config.maxOutputTokens) {
                val currentSeq = generated.toLongArray()
                val decSeqShape = longArrayOf(1, currentSeq.size.toLong())
                val decSeqBuffer = LongBuffer.wrap(currentSeq)
                val decSeqTensor = OnnxTensor.createTensor(env, decSeqBuffer, decSeqShape)

                val inputs = mutableMapOf<String, OnnxTensor>()
                inputs["input_ids"] = decSeqTensor
                inputs["encoder_attention_mask"] = encMaskTensor
                if (encoderHiddenStates != null) {
                    inputs["encoder_hidden_states"] = encoderHiddenStates
                }

                val results = session.run(inputs)
                val logitsTensor = results.get("logits")?.get() as? OnnxTensor

                var nextTokenId = config.eosTokenId
                if (logitsTensor != null) {
                    @Suppress("UNCHECKED_CAST")
                    val logits = logitsTensor.value as? Array<Array<FloatArray>>
                    if (logits != null && logits.isNotEmpty()) {
                        val lastStepLogits = logits[0].last()
                        var maxVal = Float.NEGATIVE_INFINITY
                        var maxIdx = config.eosTokenId
                        for (i in lastStepLogits.indices) {
                            if (lastStepLogits[i] > maxVal) {
                                maxVal = lastStepLogits[i]
                                maxIdx = i.toLong()
                            }
                        }
                        nextTokenId = maxIdx
                    }
                    logitsTensor.close()
                }

                decSeqTensor.close()
                results.close()

                if (nextTokenId == config.eosTokenId) {
                    break
                }
                generated.add(nextTokenId)
                step++
            }
        } catch (_: Exception) {
            // Fallback gracefully on execution interruption
        } finally {
            encMaskTensor.close()
        }

        return generated
    }
}
