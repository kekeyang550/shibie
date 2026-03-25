package com.ar.objectrecognition.manager

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.custom.LocalModel
import java.io.File

class ModelManager(private val context: Context) {

    companion object {
        private const val MODEL_DIR = "models"
        private const val CONFIG_DIR = "config"
        private const val MODEL_FILE_NAME = "model.tflite"
        private const val CONFIG_FILE_NAME = "objects.json"
    }

    private val modelDir: File by lazy {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val configDir: File by lazy {
        val dir = File(context.filesDir, CONFIG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    fun getModelFile(): File {
        return File(modelDir, MODEL_FILE_NAME)
    }

    fun getConfigFile(): File {
        return File(configDir, CONFIG_FILE_NAME)
    }

    fun loadModel(): com.google.mlkit.vision.objects.ObjectDetector? {
        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            return null
        }

        try {
            val localModel = LocalModel.Builder()
                .setAbsoluteFilePath(modelFile.absolutePath)
                .build()

            val options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .setClassificationConfidenceThreshold(0.5f)
                .setMaxPerObjectLabelCount(3)
                .build()

            return ObjectDetection.getClient(options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun hasModel(): Boolean {
        return getModelFile().exists()
    }

    fun hasConfig(): Boolean {
        return getConfigFile().exists()
    }

    fun saveModel(uri: Uri): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(modelDir, "temp_model.tflite")
            val outputStream = tempFile.outputStream()
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            if (validateModel(tempFile)) {
                val modelFile = getModelFile()
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                tempFile.renameTo(modelFile)
                return true
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun saveConfig(uri: Uri): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(configDir, "temp_config.json")
            val outputStream = tempFile.outputStream()
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            val configManager = ConfigManager(context)
            val jsonString = tempFile.readText()
            if (configManager.validateConfig(jsonString)) {
                val configFile = getConfigFile()
                if (configFile.exists()) {
                    configFile.delete()
                }
                tempFile.renameTo(configFile)
                return true
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    fun validateModel(file: File): Boolean {
        if (!file.exists()) {
            return false
        }
        
        val fileSize = file.length()
        if (fileSize < 1024) {
            return false
        }
        
        if (fileSize > 50 * 1024 * 1024) {
            return false
        }
        
        return try {
            val localModel = LocalModel.Builder()
                .setAbsoluteFilePath(file.absolutePath)
                .build()
            
            val options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
            
            ObjectDetection.getClient(options).close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteModel() {
        getModelFile().delete()
    }

    fun deleteConfig() {
        getConfigFile().delete()
    }

    fun getModelVersion(): String {
        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            return ""
        }
        return modelFile.lastModified().toString()
    }
}
