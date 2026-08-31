package com.example.aitranslator.offline.nllb

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

class NllbEncoder(
    private val env: OrtEnvironment,
    private val session: OrtSession
) {

    fun encode(inputIds: LongArray, attentionMask: LongArray): OnnxTensor? {
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputIdsBuffer = LongBuffer.wrap(inputIds)
        val attentionMaskBuffer = LongBuffer.wrap(attentionMask)

        val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsBuffer, shape)
        val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        val results = session.run(inputs)
        val lastHiddenState = results.get("last_hidden_state")?.get() as? OnnxTensor

        inputIdsTensor.close()
        attentionMaskTensor.close()

        return lastHiddenState
    }
}
