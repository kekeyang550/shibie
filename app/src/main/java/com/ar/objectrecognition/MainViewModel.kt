package com.ar.objectrecognition

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect
)

class MainViewModel : ViewModel() {
    
    private val _detectionResults = MutableLiveData<List<DetectionResult>>()
    val detectionResults: LiveData<List<DetectionResult>> = _detectionResults

    fun updateDetectionResults(results: List<DetectionResult>) {
        _detectionResults.value = results
    }
}
