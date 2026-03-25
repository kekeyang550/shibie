package com.ar.objectrecognition.manager

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

class LogManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val logDir: File = File(appContext.filesDir, "logs")
    
    companion object {
        private const val TAG = "LogManager"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_LOG_FILES = 5
        private const val FLUSH_INTERVAL = 30L // 30秒
        
        @Volatile
        private var instance: LogManager? = null
        
        fun getInstance(context: Context): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager(context).also { instance = it }
            }
        }
    }
    
    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        startFlushTimer()
    }
    
    private fun startFlushTimer() {
        executor.scheduleWithFixedDelay({
            flushLogs()
        }, FLUSH_INTERVAL, FLUSH_INTERVAL, TimeUnit.SECONDS)
    }
    
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        logQueue.offer(entry)
        
        // 立即输出到Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        
        // 错误日志立即写入文件
        if (level == LogLevel.ERROR) {
            flushLogs()
        }
    }
    
    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
    
    private fun flushLogs() {
        if (logQueue.isEmpty()) return
        
        val entries = mutableListOf<LogEntry>()
        while (true) {
            val entry = logQueue.poll() ?: break
            entries.add(entry)
        }
        
        if (entries.isEmpty()) return
        
        try {
            val logFile = getCurrentLogFile()
            val logContent = entries.joinToString("\n") { formatLogEntry(it) }
            
            logFile.appendText(logContent + "\n")
            
            // 检查文件大小
            if (logFile.length() > MAX_LOG_FILE_SIZE) {
                rotateLogFiles()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write logs", e)
        }
    }
    
    private fun getCurrentLogFile(): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fileName = "app_${dateFormat.format(Date())}.log"
        return File(logDir, fileName)
    }
    
    private fun formatLogEntry(entry: LogEntry): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = dateFormat.format(Date(entry.timestamp))
        val levelStr = entry.level.name.padEnd(5)
        
        val sb = StringBuilder()
        sb.append("[$timeStr] $levelStr/${entry.tag}: ${entry.message}")
        
        entry.throwable?.let {
            sb.append("\n").append(Log.getStackTraceString(it))
        }
        
        return sb.toString()
    }
    
    private fun rotateLogFiles() {
        val logFiles = logDir.listFiles { file ->
            file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: return
        
        // 删除旧日志文件
        if (logFiles.size >= MAX_LOG_FILES) {
            logFiles.subList(MAX_LOG_FILES - 1, logFiles.size).forEach { it.delete() }
        }
        
        // 重命名当前日志文件
        val currentFile = getCurrentLogFile()
        if (currentFile.exists()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val newName = "app_${dateFormat.format(Date())}.log"
            currentFile.renameTo(File(logDir, newName))
        }
    }
    
    fun getLogFiles(): List<File> {
        return logDir.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun getLogs(since: Long = 0): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        val logFiles = getLogFiles()
        
        logFiles.forEach { file ->
            try {
                file.readLines().forEach { line ->
                    parseLogLine(line)?.let { entry ->
                        if (entry.timestamp >= since) {
                            entries.add(entry)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read log file: ${file.name}", e)
            }
        }
        
        return entries.sortedBy { it.timestamp }
    }
    
    private fun parseLogLine(line: String): LogEntry? {
        // 解析格式: [yyyy-MM-dd HH:mm:ss.SSS] LEVEL/TAG: message
        val regex = """\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})] (\w+)\s*/(\w+): (.+)""".toRegex()
        val matchResult = regex.find(line) ?: return null
        
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val timestamp = dateFormat.parse(matchResult.groupValues[1])?.time ?: return null
            val level = LogLevel.valueOf(matchResult.groupValues[2].trim())
            val tag = matchResult.groupValues[3]
            val message = matchResult.groupValues[4]
            
            LogEntry(timestamp, level, tag, message)
        } catch (e: Exception) {
            null
        }
    }
    
    fun clearLogs() {
        logDir.listFiles()?.forEach { it.delete() }
        logQueue.clear()
    }
    
    fun exportLogs(): File? {
        return try {
            val exportFile = File(appContext.cacheDir, "exported_logs.txt")
            val allLogs = getLogs()
            
            exportFile.writeText(allLogs.joinToString("\n") { formatLogEntry(it) })
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            null
        }
    }
    
    fun shutdown() {
        flushLogs()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}
