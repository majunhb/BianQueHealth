package com.bianque.health.tongue.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 MobileNetV3 TFLite 模型的舌色分类器。
 *
 * 模型规格：
 * - 输入: 224x224 RGB 图像，归一化到 [-1, 1]
 * - 输出: 8 类 softmax 概率分布
 *
 * 分类标签：
 * - 0: 淡白舌 — 血虚、阳虚
 * - 1: 淡红舌 — 正常舌色
 * - 2: 红舌 — 热证
 * - 3: 红绛舌 — 热入营血
 * - 4: 紫暗舌 — 血瘀
 * - 5: 暗红舌 — 瘀热互结
 * - 6: 青紫舌 — 寒凝血瘀
 * - 7: 其他 — 无法分类
 *
 * 如果模型不可用，返回空结果，调用方应回退到 HSV 颜色分类。
 */
@Singleton
class TongueColorClassifier @Inject constructor() {

    companion object {
        private const val MODEL_PATH = "tongue_color_mobilenetv3.tflite"
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        private const val NORMALIZE_MEAN = 127.5f
        private const val NORMALIZE_SCALE = 127.5f

        /** 8 类舌色标签 */
        val CLASS_LABELS = arrayOf(
            "淡白",   // 0
            "淡红",   // 1
            "红",     // 2
            "红绛",   // 3
            "紫暗",   // 4
            "暗红",   // 5
            "青紫",   // 6
            "其他"    // 7
        )
    }

    private var interpreter: Interpreter? = null
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * CHANNELS * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        CLASS_LABELS.size * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    /**
     * 对舌体图像进行舌色分类。
     *
     * 流程：
     * 1. 将输入图像缩放至 224x224
     * 2. 提取 RGB 像素并归一化到 [-1, 1]
     * 3. 运行 TFLite 推理
     * 4. 对输出 softmax 取 argmax 得到类别
     *
     * @param bitmap 舌体图像（建议已分割，仅含舌体区域）
     * @param context Android Context
     * @return Pair(className, confidence)，模型不可用时返回 Pair("", 0f)
     */
    suspend fun classify(bitmap: Bitmap, context: Context): Pair<String, Float> = withContext(Dispatchers.Default) {
        try {
            val interpreter = getInterpreter(context) ?: return@withContext Pair("", 0f)

            // 步骤1: 缩放至 224x224
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 步骤2: 提取 RGB 像素并归一化到 [-1, 1]
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            inputBuffer.rewind()
            for (pixel in pixels) {
                val r = (Color.red(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE
                val g = (Color.green(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE
                val b = (Color.blue(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            // 步骤3: 运行推理
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)

            // 步骤4: 解析 softmax 输出
            outputBuffer.rewind()
            val probabilities = FloatArray(CLASS_LABELS.size)
            for (i in probabilities.indices) {
                probabilities[i] = outputBuffer.float
            }

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 1
            val className = CLASS_LABELS[maxIndex]
            val confidence = probabilities[maxIndex]

            Timber.d("TongueColorClassifier: classified as %s (confidence=%.3f)", className, confidence)
            Pair(className, confidence)
        } catch (e: Exception) {
            Timber.e(e, "TongueColorClassifier: classification failed")
            Pair("", 0f)
        }
    }

    /**
     * 获取所有类别的概率分布。
     *
     * @param bitmap 舌体图像
     * @param context Android Context
     * @return 类别名到概率的映射，模型不可用时返回空 Map
     */
    suspend fun classifyWithProbabilities(bitmap: Bitmap, context: Context): Map<String, Float> =
        withContext(Dispatchers.Default) {
            try {
                val interpreter = getInterpreter(context) ?: return@withContext emptyMap()

                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
                val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
                resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

                inputBuffer.rewind()
                for (pixel in pixels) {
                    inputBuffer.putFloat((Color.red(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE)
                    inputBuffer.putFloat((Color.green(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE)
                    inputBuffer.putFloat((Color.blue(pixel) - NORMALIZE_MEAN) / NORMALIZE_SCALE)
                }

                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)

                outputBuffer.rewind()
                val result = mutableMapOf<String, Float>()
                for (i in CLASS_LABELS.indices) {
                    result[CLASS_LABELS[i]] = outputBuffer.float
                }
                result
            } catch (e: Exception) {
                Timber.e(e, "TongueColorClassifier: classification with probabilities failed")
                emptyMap()
            }
        }

    /**
     * 判断模型是否可用。
     */
    fun isAvailable(context: Context): Boolean {
        return TfliteModelLoader.isModelAvailable(context, MODEL_PATH)
    }

    private fun getInterpreter(context: Context): Interpreter? {
        if (interpreter != null) return interpreter
        interpreter = TfliteModelLoader.loadModel(context, MODEL_PATH)
        return interpreter
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Timber.d("TongueColorClassifier: resources released")
        } catch (e: Exception) {
            Timber.e(e, "TongueColorClassifier: error closing interpreter")
        }
    }
}