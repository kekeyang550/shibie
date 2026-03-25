package com.ar.objectrecognition.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ar.objectrecognition.MainActivity
import com.ar.objectrecognition.R
import com.ar.objectrecognition.manager.LogManager
import com.ar.objectrecognition.manager.ModelManager
import com.ar.objectrecognition.manager.VersionManager
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var modelManager: ModelManager
    private lateinit var versionManager: VersionManager
    private lateinit var logManager: LogManager
    
    private var currentDownloadJob: Job? = null
    private var isDownloading = false
    
    companion object {
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ModelDownloadService"
        
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_PAUSE_DOWNLOAD = "action_pause_download"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        const val EXTRA_CONFIG_URL = "extra_config_url"
        const val EXTRA_VERSION_CODE = "extra_version_code"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ModelDownloadService = this@ModelDownloadService
    }
    
    interface DownloadCallback {
        fun onProgress(progress: Int, downloadedBytes: Long, totalBytes: Long)
        fun onSuccess()
        fun onFailure(error: String)
        fun onPaused()
        fun onCancelled()
    }
    
    private var downloadCallback: DownloadCallback? = null
    
    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        versionManager = VersionManager(this)
        logManager = LogManager.getInstance(this)
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                val configUrl = intent.getStringExtra(EXTRA_CONFIG_URL)
                val versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, 0)
                
                if (modelUrl != null && configUrl != null) {
                    startDownload(modelUrl, configUrl, versionCode)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> pauseDownload()
            ACTION_CANCEL_DOWNLOAD -> cancelDownload()
        }
        return START_NOT_STICKY
    }
    
    fun setDownloadCallback(callback: DownloadCallback) {
        downloadCallback = callback
    }
    
    private fun startDownload(modelUrl: String, configUrl: String, versionCode: Int) {
        if (isDownloading) {
            logManager.warn(TAG, "Download already in progress")
            return
        }
        
        isDownloading = true
        currentDownloadJob = serviceScope.launch {
            try {
                logManager.info(TAG, "Starting download from $modelUrl")
                
                // 下载模型文件
                val modelFile = File(cacheDir, "downloading_model.tflite")
                val modelSuccess = downloadFileWithResume(modelUrl, modelFile) { progress, downloaded, total ->
                    updateNotification("下载模型中...", progress)
                    downloadCallback?.onProgress(progress / 2, downloaded, total)
                }
                
                if (!modelSuccess) {
                    throw Exception("模型文件下载失败")
                }
                
                // 下载配置文件
                val configFile = File(cacheDir, "downloading_config.json")
                val configSuccess = downloadFileWithResume(configUrl, configFile) { progress, downloaded, total ->
                    updateNotification("下载配置中...", 50 + progress / 2)
                    downloadCallback?.onProgress(50 + progress / 2, downloaded, total)
                }
                
                if (!configSuccess) {
                    throw Exception("配置文件下载失败")
                }
                
                // 验证并保存文件
                if (validateAndSaveFiles(modelFile, configFile)) {
                    // 更新版本信息
                    val newVersion = versionManager.generateNewVersion("Downloaded from server")
                    versionManager.setCurrentVersion(newVersion)
                    
                    showCompletionNotification()
                    downloadCallback?.onSuccess()
                    logManager.info(TAG, "Download completed successfully")
                } else {
                    throw Exception("文件验证失败")
                }
                
            } catch (e: Exception) {
                logManager.error(TAG, "Download failed", e)
                downloadCallback?.onFailure(e.message ?: "未知错误")
            } finally {
                isDownloading = false
                stopForeground(true)
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification("准备下载...", 0))
    }
    
    private suspend fun downloadFileWithResume(
        urlString: String,
        targetFile: File,
        onProgress: (Int, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var randomAccessFile: RandomAccessFile? = null
        
        try {
            val url = URL(urlString)
            val existingSize = if (targetFile.exists()) targetFile.length() else 0
            
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Range", "bytes=$existingSize-")
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                return@withContext false
            }
            
            val totalBytes = connection.getHeaderFieldLong("Content-Length", -1) + existingSize
            randomAccessFile = RandomAccessFile(targetFile, "rw")
            randomAccessFile.seek(existingSize)
            
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var downloadedBytes = existingSize
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        throw CancellationException("Download cancelled")
                    }
                    
                    randomAccessFile.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            logManager.error(TAG, "Download error", e)
            false
        } finally {
            try {
                randomAccessFile?.close()
            } catch (e: Exception) {
                logManager.error(TAG, "Error closing file", e)
            }
            connection?.disconnect()
        }
    }
    
    private fun validateAndSaveFiles(modelFile: File, configFile: File): Boolean {
        return try {
            // 验证模型文件
            if (modelFile.length() < 1024) {
                return false
            }
            
            // 验证配置文件
            val configContent = configFile.readText()
            if (!configContent.contains("id") || !configContent.contains("name")) {
                return false
            }
            
            // 保存到最终位置
            val finalModelFile = modelManager.getModelFile()
            val finalConfigFile = ModelManager(this).getConfigFile()
            
            modelFile.copyTo(finalModelFile, overwrite = true)
            configFile.copyTo(finalConfigFile, overwrite = true)
            
            // 清理临时文件
            modelFile.delete()
            configFile.delete()
            
            true
        } catch (e: Exception) {
            logManager.error(TAG, "Validation failed", e)
            false
        }
    }
    
    private fun pauseDownload() {
        currentDownloadJob?.cancel()
        isDownloading = false
        downloadCallback?.onPaused()
        stopForeground(true)
        logManager.info(TAG, "Download paused")
    }
    
    private fun cancelDownload() {
        currentDownloadJob?.cancel()
        isDownloading = false
        
        // 清理临时文件
        File(cacheDir, "downloading_model.tflite").delete()
        File(cacheDir, "downloading_config.json").delete()
        
        downloadCallback?.onCancelled()
        stopForeground(true)
        logManager.info(TAG, "Download cancelled")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "模型下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示模型下载进度"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR物体识别")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(content: String, progress: Int) {
        val notification = createNotification(content, progress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR物体识别")
            .setContentText("模型下载完成")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
