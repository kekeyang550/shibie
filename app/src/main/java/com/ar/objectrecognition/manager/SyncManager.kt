package com.ar.objectrecognition.manager

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SyncManager(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
    }

    private val modelManager = ModelManager(context)

    interface SyncCallback {
        fun onSuccess()
        fun onFailure(error: String)
        fun onProgress(progress: Int)
    }

    fun syncFromServer(serverUrl: String, callback: SyncCallback) {
        SyncTask(serverUrl, callback).execute()
    }

    fun syncFromLocal(modelUri: Uri, configUri: Uri, callback: SyncCallback) {
        try {
            val modelResult = modelManager.saveModel(modelUri)
            val configResult = modelManager.saveConfig(configUri)

            if (modelResult && configResult) {
                callback.onSuccess()
            } else {
                callback.onFailure("同步失败：文件保存失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onFailure("同步失败：${e.message}")
        }
    }

    private inner class SyncTask(
        private val serverUrl: String,
        private val callback: SyncCallback
    ) : AsyncTask<Void, Int, Boolean>() {

        private var errorMessage: String = ""

        override fun doInBackground(vararg params: Void?): Boolean {
            try {
                // 同步模型文件
                publishProgress(25)
                val modelUrl = "$serverUrl/model.tflite"
                if (!downloadFile(modelUrl, modelManager.getModelFile())) {
                    errorMessage = "模型文件下载失败"
                    return false
                }

                publishProgress(75)
                // 同步配置文件
                val configUrl = "$serverUrl/objects.json"
                if (!downloadFile(configUrl, modelManager.getConfigFile())) {
                    errorMessage = "配置文件下载失败"
                    return false
                }

                publishProgress(100)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "同步失败：${e.message}"
                return false
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            values[0]?.let { callback.onProgress(it) }
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                callback.onSuccess()
            } else {
                callback.onFailure(errorMessage)
            }
        }

        private fun downloadFile(urlString: String, targetFile: File): Boolean {
            var connection: HttpURLConnection? = null
            var inputStream: BufferedInputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return false
                }

                inputStream = BufferedInputStream(connection.inputStream)
                outputStream = java.io.FileOutputStream(targetFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            } finally {
                outputStream?.close()
                inputStream?.close()
                connection?.disconnect()
            }
        }
    }
}
