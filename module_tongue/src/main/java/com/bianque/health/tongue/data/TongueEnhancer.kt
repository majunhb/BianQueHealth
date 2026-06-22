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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 舌象图像增强器 — 基于 Zero-DCE TFLite 模型 + CLAHE 回退。
 *
 * 主要功能：
 * - 使用 Zero-DCE 深度模型进行低光照图像增强（需 .tflite 模型）
 * - 如果模型不可用，自动回退到纯 Kotlin 实现的 CLAHE 算法
 * - CLAHE 在 YUV 色彩空间的 Y 通道上进行 8x8 分块直方图均衡化
 *
 * Zero-DCE 模型规格：
 * - 输入: 256x256 RGB 图像，归一化到 [0, 1]
 * - 输出: 256x256 增强后的 RGB 图像（值域 [0, 1]）
 *
 * 使用方式：
 * ```
 * val enhancer = TongueEnhancer()
 * val enhanced = enhancer.enhance(inputBitmap, context)
 * // enhanced 为增强后的图像，或 null（如果处理失败）
 * ```
 */
@Singleton
class TongueEnhancer @Inject constructor() {

    companion object {
        private const val TFLITE_MODEL_PATH = "tongue_zerodce_enhancer.tflite"
        private const val INPUT_SIZE = 256
        private const val CHANNELS = 3

        // CLAHE 参数
        private const val CLAHE_TILE_SIZE = 8
        private const val CLAHE_CLIP_LIMIT = 2.0f
        private const val CLAHE_GRAY_LEVELS = 256
    }

    private var interpreter: Interpreter? = null
    private val tfliteInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * CHANNELS * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val tfliteOutputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * CHANNELS * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    /**
     * 增强输入图像。
     *
     * 策略：
     * 1. 优先尝试 Zero-DCE TFLite 模型增强
     * 2. 如果模型不可用或推理失败，回退到 CLAHE 增强
     * 3. 如果 CLAHE 也失败，返回 null（调用方使用原图）
     *
     * @param bitmap 输入舌象图像
     * @param context Android Context
     * @return 增强后的图像，失败时返回 null
     */
    suspend fun enhance(bitmap: Bitmap, context: Context): Bitmap? = withContext(Dispatchers.Default) {
        try {
            // 策略1: 尝试 Zero-DCE TFLite 模型
            val tfliteResult = enhanceWithTflite(bitmap, context)
            if (tfliteResult != null) {
                Timber.d("TongueEnhancer: enhanced with Zero-DCE TFLite model")
                return@withContext tfliteResult
            }

            // 策略2: 回退到 CLAHE
            Timber.d("TongueEnhancer: TFLite model unavailable, falling back to CLAHE")
            val claheResult = enhanceWithClahe(bitmap)
            if (claheResult != null) {
                Timber.d("TongueEnhancer: enhanced with CLAHE")
                return@withContext claheResult
            }

            Timber.w("TongueEnhancer: both TFLite and CLAHE enhancement failed")
            null
        } catch (e: Exception) {
            Timber.e(e, "TongueEnhancer: enhancement failed")
            null
        }
    }

    // ==================== Zero-DCE TFLite 增强 ====================

    /**
     * 使用 Zero-DCE TFLite 模型进行增强。
     */
    private suspend fun enhanceWithTflite(bitmap: Bitmap, context: Context): Bitmap? {
        try {
            val interpreter = getInterpreter(context) ?: return null

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // 缩放至 256x256
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 提取像素并归一化到 [0, 1]
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            tfliteInputBuffer.rewind()
            for (pixel in pixels) {
                tfliteInputBuffer.putFloat(Color.red(pixel) / 255f)
                tfliteInputBuffer.putFloat(Color.green(pixel) / 255f)
                tfliteInputBuffer.putFloat(Color.blue(pixel) / 255f)
            }

            // 推理
            tfliteOutputBuffer.rewind()
            interpreter.run(tfliteInputBuffer, tfliteOutputBuffer)

            // 将输出反归一化到 [0, 255] 并构建位图
            val outputPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            tfliteOutputBuffer.rewind()
            for (i in outputPixels.indices) {
                val r = (tfliteOutputBuffer.float * 255f).toInt().coerceIn(0, 255)
                val g = (tfliteOutputBuffer.float * 255f).toInt().coerceIn(0, 255)
                val b = (tfliteOutputBuffer.float * 255f).toInt().coerceIn(0, 255)
                outputPixels[i] = Color.rgb(r, g, b)
            }

            val result = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            result.setPixels(outputPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            // 缩放回原始尺寸
            return if (originalWidth != INPUT_SIZE || originalHeight != INPUT_SIZE) {
                Bitmap.createScaledBitmap(result, originalWidth, originalHeight, true)
            } else {
                result
            }
        } catch (e: Exception) {
            Timber.w(e, "TongueEnhancer: TFLite enhancement failed, will try CLAHE fallback")
            return null
        }
    }

    // ==================== CLAHE 增强（纯 Kotlin 实现） ====================

    /**
     * 使用 CLAHE（对比度受限自适应直方图均衡化）增强图像。
     *
     * 算法流程：
     * 1. RGB → YUV 色彩空间转换
     * 2. 在 Y 通道上进行 8x8 分块 CLAHE
     * 3. 对每个 tile 进行直方图均衡化（带裁剪限制）
     * 4. 双线性插值拼接 tile
     * 5. YUV → RGB 转换回
     *
     * 这是纯 Kotlin 实现，不依赖 OpenCV 或其他 native 库。
     */
    private fun enhanceWithClahe(bitmap: Bitmap): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 步骤1: RGB → YUV
            val yChannel = IntArray(width * height)
            val uChannel = IntArray(width * height)
            val vChannel = IntArray(width * height)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // RGB → YUV (BT.601)
                yChannel[i] = ((0.299 * r + 0.587 * g + 0.114 * b) + 0.5).toInt().coerceIn(0, 255)
                uChannel[i] = ((-0.14713 * r - 0.28886 * g + 0.436 * b) + 128 + 0.5).toInt().coerceIn(0, 255)
                vChannel[i] = ((0.615 * r - 0.51499 * g - 0.10001 * b) + 128 + 0.5).toInt().coerceIn(0, 255)
            }

            // 步骤2: 在 Y 通道上执行 CLAHE
            val equalizedY = claheOnChannel(yChannel, width, height, CLAHE_TILE_SIZE, CLAHE_CLIP_LIMIT)

            // 步骤3: YUV → RGB
            val resultPixels = IntArray(width * height)
            for (i in pixels.indices) {
                val y = equalizedY[i]
                val u = uChannel[i] - 128
                val v = vChannel[i] - 128

                val r = (y + 1.13983 * v + 0.5).toInt().coerceIn(0, 255)
                val g = (y - 0.39465 * u - 0.58060 * v + 0.5).toInt().coerceIn(0, 255)
                val b = (y + 2.03211 * u + 0.5).toInt().coerceIn(0, 255)

                resultPixels[i] = Color.rgb(r, g, b)
            }

            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            Timber.e(e, "TongueEnhancer: CLAHE enhancement failed")
            null
        }
    }

    /**
     * 对单通道执行 CLAHE。
     *
     * @param channel 输入单通道像素值 [0, 255]
     * @param width 图像宽度
     * @param height 图像高度
     * @param tileSize 分块大小（如 8 表示 8x8 tile）
     * @param clipLimit 直方图裁剪限制（控制对比度增强程度）
     * @return 均衡化后的通道像素值
     */
    private fun claheOnChannel(
        channel: IntArray,
        width: Int,
        height: Int,
        tileSize: Int,
        clipLimit: Float
    ): IntArray {
        val result = IntArray(channel.size)

        // 计算 tile 网格
        val tilesX = max(1, (width + tileSize - 1) / tileSize)
        val tilesY = max(1, (height + tileSize - 1) / tileSize)
        val actualTileWidth = (width + tilesX - 1) / tilesX
        val actualTileHeight = (height + tilesY - 1) / tilesY

        // 为每个 tile 计算映射表 (LUT)
        val tileLuts = Array(tilesY) { Array(tilesX) { IntArray(CLAHE_GRAY_LEVELS) } }

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val tileStartX = tx * actualTileWidth
                val tileStartY = ty * actualTileHeight
                val tileEndX = min(tileStartX + actualTileWidth, width)
                val tileEndY = min(tileStartY + actualTileHeight, height)

                // 计算 tile 直方图
                val histogram = IntArray(CLAHE_GRAY_LEVELS)
                var totalPixels = 0
                for (py in tileStartY until tileEndY) {
                    for (px in tileStartX until tileEndX) {
                        val idx = py * width + px
                        histogram[channel[idx]]++
                        totalPixels++
                    }
                }

                // 裁剪直方图
                if (totalPixels > 0) {
                    clipHistogram(histogram, clipLimit, totalPixels)
                }

                // 计算累积分布函数 (CDF) 作为映射表
                computeLut(histogram, totalPixels, tileLuts[ty][tx])
            }
        }

        // 双线性插值拼接
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixelValue = channel[idx]

                // 确定当前像素所在的 tile 和相邻 tile
                val tx = (x / actualTileWidth).coerceIn(0, tilesX - 1)
                val ty = (y / actualTileHeight).coerceIn(0, tilesY - 1)

                // 计算在 tile 内的相对位置 (用于插值权重)
                val tileCenterX = tx * actualTileWidth + actualTileWidth / 2f
                val tileCenterY = ty * actualTileHeight + actualTileHeight / 2f

                val dx = (x - tileCenterX) / actualTileWidth.toFloat()
                val dy = (y - tileCenterY) / actualTileHeight.toFloat()

                // 确定四个相邻 tile 的索引
                val tx0 = tx
                val tx1 = min(tx + 1, tilesX - 1)
                val ty0 = ty
                val ty1 = min(ty + 1, tilesY - 1)

                // 对于边界像素，调整插值方向
                val txPrev = max(tx - 1, 0)
                val tyPrev = max(ty - 1, 0)

                val a = if (dx < 0) txPrev else tx0
                val b = if (dx < 0) tx0 else tx1
                val c = if (dy < 0) tyPrev else ty0
                val d = if (dy < 0) ty0 else ty1

                val weightX = abs(dx)
                val weightY = abs(dy)

                // 双线性插值
                val v00 = tileLuts[c][a][pixelValue]
                val v10 = tileLuts[c][b][pixelValue]
                val v01 = tileLuts[d][a][pixelValue]
                val v11 = tileLuts[d][b][pixelValue]

                val top = v00 + ((v10 - v00).toFloat() * weightX + 0.5f).toInt()
                val bottom = v01 + ((v11 - v01).toFloat() * weightX + 0.5f).toInt()
                val interpolated = top + ((bottom - top).toFloat() * weightY + 0.5f).toInt()

                result[idx] = interpolated.coerceIn(0, 255)
            }
        }

        return result
    }

    /**
     * 对直方图进行裁剪，将超出限制的部分均匀重新分布。
     */
    private fun clipHistogram(histogram: IntArray, clipLimit: Float, totalPixels: Int) {
        val clipThreshold = (clipLimit * totalPixels / CLAHE_GRAY_LEVELS).toInt().coerceAtLeast(1)
        var excess = 0
        for (i in histogram.indices) {
            if (histogram[i] > clipThreshold) {
                excess += histogram[i] - clipThreshold
                histogram[i] = clipThreshold
            }
        }
        if (excess > 0) {
            val increment = excess / CLAHE_GRAY_LEVELS
            val remainder = excess % CLAHE_GRAY_LEVELS
            for (i in histogram.indices) {
                histogram[i] += increment + (if (i < remainder) 1 else 0)
            }
        }
    }

    /**
     * 从直方图计算累积分布函数作为 LUT。
     */
    private fun computeLut(histogram: IntArray, totalPixels: Int, lut: IntArray) {
        if (totalPixels == 0) {
            for (i in lut.indices) lut[i] = i
            return
        }
        var sum = 0.0
        for (i in histogram.indices) {
            sum += histogram[i]
            lut[i] = ((sum / totalPixels) * (CLAHE_GRAY_LEVELS - 1) + 0.5).toInt().coerceIn(0, 255)
        }
    }

    // ==================== 资源管理 ====================

    private fun getInterpreter(context: Context): Interpreter? {
        if (interpreter != null) return interpreter
        interpreter = TfliteModelLoader.loadModel(context, TFLITE_MODEL_PATH)
        return interpreter
    }

    /**
     * 释放 TFLite 资源。
     */
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Timber.d("TongueEnhancer: resources released")
        } catch (e: Exception) {
            Timber.e(e, "TongueEnhancer: error closing interpreter")
        }
    }
}