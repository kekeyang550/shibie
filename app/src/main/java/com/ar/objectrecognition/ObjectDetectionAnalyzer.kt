package com.ar.objectrecognition

import android.content.Context
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ar.objectrecognition.manager.ModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObjectDetectionAnalyzer(
    private val context: Context,
    private val viewModel: MainViewModel
) : ImageAnalysis.Analyzer {

    private val modelManager = ModelManager(context)
    private var objectDetector = createDefaultDetector()

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
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    processDetectionResults(detectedObjects)
                    imageProxy.close()
                }
                .addOnFailureListener {
                    it.printStackTrace()
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processDetectionResults(detectedObjects: List<DetectedObject>) {
        val results = detectedObjects.mapNotNull { obj ->
            val label = obj.labels.firstOrNull()?.text ?: "未知物体"
            val confidence = obj.labels.firstOrNull()?.confidence ?: 0f
            
            if (confidence >= 0.5f) {
                DetectionResult(
                    label = label,
                    confidence = confidence,
                    boundingBox = obj.boundingBox
                )
            } else {
                null
            }
        }
        
        viewModel.updateDetectionResults(results)
    }
}
