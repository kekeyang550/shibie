package com.ar.objectrecognition.manager

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.*
import java.io.File

class OptimizedModelManager(private val context: Context) {

    private var objectDetector: com.google.mlkit.vision.objects.ObjectDetector? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val modelManager = ModelManager(context)
    private val logManager = LogManager.getInstance(context)

    companion object {
        private const val TAG = "OptimizedModelManager"
    }

    suspend fun loadModelAsync(): com.google.mlkit.vision.objects.ObjectDetector? =
        withContext(Dispatchers.IO) {
            try {
                val options = ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableClassification()
                    .build()

                objectDetector = ObjectDetection.getClient(options)
                logManager.info(TAG, "Default object detector created")
                objectDetector
            } catch (e: Exception) {
                logManager.error(TAG, "Error creating detector", e)
                null
            }
        }

    fun getDetector(): com.google.mlkit.vision.objects.ObjectDetector? {
        return objectDetector ?: createDefaultDetector()
    }

    private fun createDefaultDetector(): com.google.mlkit.vision.objects.ObjectDetector {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        return ObjectDetection.getClient(options)
    }

    fun clearCache() {
        objectDetector?.close()
        objectDetector = null
        logManager.info(TAG, "Detector cache cleared")
    }

    fun release() {
        coroutineScope.cancel()
        clearCache()
    }
}
