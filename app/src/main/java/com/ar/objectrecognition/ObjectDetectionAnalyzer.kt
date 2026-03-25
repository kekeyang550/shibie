package com.ar.objectrecognition

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObjectDetectionAnalyzer(
    private val viewModel: MainViewModel
) : ImageAnalysis.Analyzer {

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

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
