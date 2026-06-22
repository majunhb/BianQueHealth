package com.bianque.health.tongue.data

import android.content.Context
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite 模型加载工具类。
 *
 * 负责从 assets 目录加载 .tflite 模型文件，并创建 Interpreter 实例。
 * 如果模型文件不存在，返回 null，调用方应回退到传统算法。
 *
 * 使用方式：
 * ```
 * val interpreter = TfliteModelLoader.loadModel(context, "tongue_segmenter.tflite")
 * if (interpreter != null) {
 *     // 使用 TFLite 推理
 * } else {
 *     // 回退到传统 HSV 算法
 * }
 * ```
 */
object TfliteModelLoader {

    private const val NUM_THREADS = 4

    /**
     * 从 assets 加载 TFLite 模型，返回 Interpreter 实例。
     *
     * @param context Android Context，用于访问 assets
     * @param modelPath assets 中的模型文件路径，例如 "tongue_segmenter.tflite"
     * @return Interpreter 实例，如果模型不可用则返回 null
     */
    fun loadModel(context: Context, modelPath: String): Interpreter? {
        return try {
            if (!isModelAvailable(context, modelPath)) {
                Timber.w("TfliteModelLoader: model not found in assets: %s", modelPath)
                return null
            }
            val modelBuffer = loadModelFile(context, modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(NUM_THREADS)
            }
            Interpreter(modelBuffer, options).also {
                Timber.d("TfliteModelLoader: successfully loaded model: %s (threads=%d)", modelPath, NUM_THREADS)
            }
        } catch (e: IOException) {
            Timber.e(e, "TfliteModelLoader: failed to load model: %s", modelPath)
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TfliteModelLoader: invalid model file: %s", modelPath)
            null
        } catch (e: Exception) {
            Timber.e(e, "TfliteModelLoader: unexpected error loading model: %s", modelPath)
            null
        }
    }

    /**
     * 检查 assets 中是否存在指定的模型文件。
     *
     * @param context Android Context
     * @param modelPath assets 中的模型文件路径
     * @return true 如果模型文件存在
     */
    fun isModelAvailable(context: Context, modelPath: String): Boolean {
        return try {
            context.assets.open(modelPath).use { true }
        } catch (e: IOException) {
            Timber.d("TfliteModelLoader: model not available in assets: %s", modelPath)
            false
        }
    }

    /**
     * 从 assets 读取模型文件为 MappedByteBuffer。
     * 大文件会先拷贝到缓存目录再映射，避免直接操作 assets 流带来的性能问题。
     */
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        // 尝试直接从 assets 文件描述符加载
        try {
            val assetFd = context.assets.openFd(modelPath)
            val inputStream = assetFd.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFd.startOffset
            val declaredLength = assetFd.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: IOException) {
            // 如果无法直接映射（例如压缩的 assets），则先拷贝到缓存目录
            Timber.d("TfliteModelLoader: direct mapping failed, copying to cache: %s", modelPath)
            return copyToCacheAndLoad(context, modelPath)
        }
    }

    /**
     * 将 assets 中的模型文件拷贝到缓存目录，然后映射加载。
     */
    private fun copyToCacheAndLoad(context: Context, modelPath: String): MappedByteBuffer {
        val cacheFile = File(context.cacheDir, modelPath)
        context.assets.open(modelPath).use { input ->
            FileOutputStream(cacheFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        return FileInputStreamCache(cacheFile).use { input ->
            input.channel.map(FileChannel.MapMode.READ_ONLY, 0, cacheFile.length())
        }
    }
}

/**
 * 包装 FileInputStream 以便在 use 块中安全关闭。
 */
private class FileInputStreamCache(file: File) : java.io.FileInputStream(file), AutoCloseable {
    override fun close() {
        try {
            super.close()
        } catch (_: IOException) {
            // 忽略关闭异常
        }
    }
}