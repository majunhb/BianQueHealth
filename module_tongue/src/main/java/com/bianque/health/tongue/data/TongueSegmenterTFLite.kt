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
 * 基于 U-Net TFLite 模型的舌体分割器。
 *
 * 模型规格：
 * - 输入: 256x256 RGB 图像，归一化到 [0, 1]
 * - 输出: 256x256 单通道 mask，sigmoid 激活
 * - 阈值: 0.5（大于 0.5 视为舌体区域）
 *
 * 如果模型不可用（assets 中不存在对应的 .tflite 文件），
 * 所有方法返回 null，调用方应回退到 HSV 传统分割算法。
 *
 * @see TongueSegmenter 传统 HSV 分割器，用作回退方案
 */
@Singleton
class TongueSegmenterTFLite @Inject constructor() {

    companion object {
        private const val MODEL_PATH = "tongue_unet_segmenter.tflite"
        private const val INPUT_SIZE = 256
        private const val MASK_THRESHOLD = 0.5f
        private const val CHANNELS = 3
    }

    private var interpreter: Interpreter? = null
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * CHANNELS * 4 // 4 bytes per float32
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * 4 // 1 channel float32
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    /**
     * 对输入图像进行舌体分割，返回 mask 位图。
     *
     * 流程：
     * 1. 将输入图像缩放至 256x256
     * 2. 提取 RGB 像素并归一化到 [0, 1]
     * 3. 运行 TFLite 推理
     * 4. 对输出 mask 进行 0.5 阈值二值化
     * 5. 将 mask 缩放到原始图像尺寸
     *
     * @param bitmap 输入舌象图像
     * @param context Android Context，用于加载模型
     * @return 与输入同尺寸的 mask 位图（白色=舌体，黑色=背景），模型不可用时返回 null
     */
    suspend fun segment(bitmap: Bitmap, context: Context): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val interpreter = getInterpreter(context) ?: return@withContext null

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // 步骤1: 缩放至 256x256
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 步骤2: 提取 RGB 像素并归一化到 [0, 1]
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            inputBuffer.rewind()
            for (pixel in pixels) {
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            // 步骤3: 运行推理
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)

            // 步骤4: 阈值二值化，生成 mask 位图
            val maskPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            outputBuffer.rewind()
            for (i in maskPixels.indices) {
                val value = outputBuffer.float
                maskPixels[i] = if (value >= MASK_THRESHOLD) Color.WHITE else Color.BLACK
            }

            val maskBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            maskBitmap.setPixels(maskPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            // 步骤5: 缩放回原始尺寸
            if (originalWidth != INPUT_SIZE || originalHeight != INPUT_SIZE) {
                Bitmap.createScaledBitmap(maskBitmap, originalWidth, originalHeight, true)
            } else {
                maskBitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "TongueSegmenterTFLite: segmentation failed")
            null
        }
    }

    /**
     * 获取或初始化 TFLite Interpreter。
     * 使用懒加载，首次调用时从 assets 加载模型。
     */
    private fun getInterpreter(context: Context): Interpreter? {
        if (interpreter != null) return interpreter
        interpreter = TfliteModelLoader.loadModel(context, MODEL_PATH)
        return interpreter
    }

    /**
     * 释放 TFLite 资源。
     * 在不再需要模型推理时调用，释放内存。
     */
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Timber.d("TongueSegmenterTFLite: resources released")
        } catch (e: Exception) {
            Timber.e(e, "TongueSegmenterTFLite: error closing interpreter")
        }
    }
}