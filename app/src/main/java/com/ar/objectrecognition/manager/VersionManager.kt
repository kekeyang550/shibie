package com.ar.objectrecognition.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ModelVersion(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val description: String,
    val modelHash: String,
    val configHash: String
)

class VersionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "model_version_prefs"
        private const val KEY_CURRENT_VERSION = "current_version"
        private const val KEY_VERSION_HISTORY = "version_history"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_MODEL_HASH = "model_hash"
        private const val KEY_CONFIG_HASH = "config_hash"
    }
    
    fun getCurrentVersion(): ModelVersion? {
        val versionJson = prefs.getString(KEY_CURRENT_VERSION, null) ?: return null
        return parseVersionJson(versionJson)
    }
    
    fun setCurrentVersion(version: ModelVersion) {
        prefs.edit().apply {
            putString(KEY_CURRENT_VERSION, versionToJson(version))
            putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
            putString(KEY_MODEL_HASH, version.modelHash)
            putString(KEY_CONFIG_HASH, version.configHash)
            apply()
        }
        addToVersionHistory(version)
    }
    
    fun getVersionHistory(): List<ModelVersion> {
        val historyJson = prefs.getString(KEY_VERSION_HISTORY, "[]")
        val jsonArray = org.json.JSONArray(historyJson)
        val history = mutableListOf<ModelVersion>()
        
        for (i in 0 until jsonArray.length()) {
            parseVersionJson(jsonArray.getString(i))?.let { history.add(it) }
        }
        
        return history.sortedByDescending { it.versionCode }
    }
    
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }
    
    fun isNewVersionAvailable(serverVersion: ModelVersion): Boolean {
        val currentVersion = getCurrentVersion()
        return currentVersion == null || serverVersion.versionCode > currentVersion.versionCode
    }
    
    fun generateNewVersion(description: String): ModelVersion {
        val currentVersion = getCurrentVersion()
        val newVersionCode = (currentVersion?.versionCode ?: 0) + 1
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        return ModelVersion(
            versionCode = newVersionCode,
            versionName = "v1.${newVersionCode}",
            releaseDate = dateFormat.format(Date()),
            description = description,
            modelHash = generateHash(),
            configHash = generateHash()
        )
    }
    
    fun clearVersionHistory() {
        prefs.edit().apply {
            remove(KEY_VERSION_HISTORY)
            remove(KEY_CURRENT_VERSION)
            remove(KEY_LAST_SYNC_TIME)
            apply()
        }
    }
    
    private fun addToVersionHistory(version: ModelVersion) {
        val history = getVersionHistory().toMutableList()
        history.add(0, version)
        
        // 只保留最近10个版本
        if (history.size > 10) {
            history.subList(10, history.size).clear()
        }
        
        val jsonArray = org.json.JSONArray()
        history.forEach { jsonArray.put(versionToJson(it)) }
        
        prefs.edit().putString(KEY_VERSION_HISTORY, jsonArray.toString()).apply()
    }
    
    private fun parseVersionJson(jsonString: String): ModelVersion? {
        return try {
            val json = JSONObject(jsonString)
            ModelVersion(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                releaseDate = json.getString("releaseDate"),
                description = json.getString("description"),
                modelHash = json.getString("modelHash"),
                configHash = json.getString("configHash")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun versionToJson(version: ModelVersion): String {
        val json = JSONObject()
        json.put("versionCode", version.versionCode)
        json.put("versionName", version.versionName)
        json.put("releaseDate", version.releaseDate)
        json.put("description", version.description)
        json.put("modelHash", version.modelHash)
        json.put("configHash", version.configHash)
        return json.toString()
    }
    
    private fun generateHash(): String {
        return System.currentTimeMillis().toString(36).take(8)
    }
    
    fun formatLastSyncTime(): String {
        val lastSync = getLastSyncTime()
        if (lastSync == 0L) {
            return "从未同步"
        }
        
        val diff = System.currentTimeMillis() - lastSync
        return when {
            diff < 60000 -> "刚刚"
            diff < 3600000 -> "${diff / 60000}分钟前"
            diff < 86400000 -> "${diff / 3600000}小时前"
            else -> {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                dateFormat.format(Date(lastSync))
            }
        }
    }
}
