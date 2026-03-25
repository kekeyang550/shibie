package com.ar.objectrecognition.manager

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.custom.LocalModel
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class OptimizedModelManager(private val context: Context) {

    private val modelCache = ConcurrentHashMap<String, com.google.mlkit.vision.objects.ObjectDetector>()
    private val modelLoadJobs = ConcurrentHashMap<String, Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val modelManager = ModelManager(context)
    
    companion object {
        private const val CACHE_SIZE_LIMIT = 3
        private const val TAG = "OptimizedModelManager"
    }

    /**
     * 异步加载模型，使用缓存
     */
    suspend fun loadModelAsync(modelPath: String? = null): com.google.mlkit.vision.objects.ObjectDetector? =
        withContext(Dispatchers.IO) {
            val path = modelPath ?: modelManager.getModelFile().absolutePath
            
            // 检查缓存
            modelCache[path]?.let {
                LogManager.getInstance(context).info(TAG, "Model loaded from cache: $path")
                return@withContext it
            }
            
            // 检查是否正在加载
            modelLoadJobs[path]?.let { job ->
                LogManager.getInstance(context).info(TAG, "Waiting for model load: $path")
                job.join()
                return@withContext modelCache[path]
            }
            
            // 创建新的加载任务
            val loadJob = coroutineScope.launch {
                try {
                    val detector = loadModelInternal(path)
                    detector?.let {
                        modelCache[path] = it
                        manageCache()
                    }
                } catch (e: Exception) {
                    LogManager.getInstance(context).error(TAG, "Failed to load model: $path", e)
                } finally {
                    modelLoadJobs.remove(path)
                }
            }
            
            modelLoadJobs[path] = loadJob
            loadJob.join()
            
            modelCache[path]
        }

    /**
     * 预加载模型到缓存
     */
    fun preloadModel(modelPath: String) {
        coroutineScope.launch {
            loadModelAsync(modelPath)
        }
    }

    /**
     * 清除模型缓存
     */
    fun clearCache() {
        modelCache.values.forEach { it.close() }
        modelCache.clear()
        modelLoadJobs.values.forEach { it.cancel() }
        modelLoadJobs.clear()
        LogManager.getInstance(context).info(TAG, "Model cache cleared")
    }

    /**
     * 获取缓存的模型
     */
    fun getCachedModel(modelPath: String): com.google.mlkit.vision.objects.ObjectDetector? {
        return modelCache[modelPath]
    }

    /**
     * 检查模型是否在缓存中
     */
    fun isModelCached(modelPath: String): Boolean {
        return modelCache.containsKey(modelPath)
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return modelCache.size
    }

    /**
     * 管理缓存大小
     */
    private fun manageCache() {
        if (modelCache.size > CACHE_SIZE_LIMIT) {
            // 移除最旧的缓存
            val oldestEntry = modelCache.entries.firstOrNull()
            oldestEntry?.let {
                it.value.close()
                modelCache.remove(it.key)
                LogManager.getInstance(context).info(TAG, "Removed oldest model from cache: ${it.key}")
            }
        }
    }

    /**
     * 内部加载模型方法
     */
    private fun loadModelInternal(modelPath: String): com.google.mlkit.vision.objects.ObjectDetector? {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return null
        }

        return try {
            val localModel = LocalModel.Builder()
                .setAbsoluteFilePath(modelPath)
                .build()

            val options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .setClassificationConfidenceThreshold(0.5f)
                .setMaxPerObjectLabelCount(3)
                .build()

            ObjectDetection.getClient(options)
        } catch (e: Exception) {
            LogManager.getInstance(context).error(TAG, "Error loading model", e)
            null
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        coroutineScope.cancel()
        clearCache()
    }
}
