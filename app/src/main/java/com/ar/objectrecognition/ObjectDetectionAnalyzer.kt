package com.ar.objectrecognition

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ar.objectrecognition.manager.ConfigManager
import com.ar.objectrecognition.manager.ModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObjectDetectionAnalyzer(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val configManager: ConfigManager,
    private val onImageDimensionsReady: (Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val modelManager = ModelManager(context)
    private var objectDetector = createDefaultDetector()
    private var frameCount = 0

    companion object {
        private const val TAG = "ObjectDetectionAnalyzer"
        private const val LOG_INTERVAL = 30
    }

    private fun createDefaultDetector() = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    fun updateDetector() {
        val customDetector = modelManager.loadModel()
        if (customDetector != null) {
            objectDetector = customDetector
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val imageWidth = mediaImage.width
            val imageHeight = mediaImage.height
            onImageDimensionsReady(imageWidth, imageHeight)

            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    frameCount++
                    if (frameCount % LOG_INTERVAL == 0) {
                        Log.d(TAG, "检测到 ${detectedObjects.size} 个物体")
                        detectedObjects.take(3).forEach { obj ->
                            val label = obj.labels.firstOrNull()
                            Log.d(TAG, "  - ${label?.text} (${String.format("%.1f", (label?.confidence ?: 0f) * 100)}%)")
                        }
                    }
                    processDetectionResults(detectedObjects)
                    imageProxy.close()
                }
                .addOnFailureListener {
                    Log.e(TAG, "检测失败", it)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processDetectionResults(detectedObjects: List<DetectedObject>) {
        val results = detectedObjects.map { obj ->
            val labelInfo = obj.labels.firstOrNull()
            val rawLabel = labelInfo?.text ?: "物体"
            val confidence = labelInfo?.confidence ?: 0.5f

            Log.d(TAG, "处理物体: label=$rawLabel, confidence=$confidence")

            val objectInfo = configManager.findObjectByLabel(rawLabel)
            val displayName = objectInfo?.name ?: rawLabel
            val annotation = objectInfo?.hintText

            DetectionResult(
                label = displayName,
                confidence = confidence,
                boundingBox = obj.boundingBox,
                annotation = annotation
            )
        }

        viewModel.updateDetectionResults(results)
    }
}
