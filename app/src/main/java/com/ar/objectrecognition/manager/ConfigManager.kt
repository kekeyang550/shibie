package com.ar.objectrecognition.manager

import android.content.Context
import com.ar.objectrecognition.ObjectInfo
import org.json.JSONArray
import org.json.JSONObject

class ConfigManager(private val context: Context) {

    private val modelManager = ModelManager(context)

    fun loadConfig(): List<ObjectInfo> {
        val configFile = modelManager.getConfigFile()
        if (!configFile.exists()) {
            return loadDefaultConfig()
        }

        try {
            val jsonString = configFile.readText()
            if (validateConfig(jsonString)) {
                return parseConfigJson(jsonString)
            }
            return loadDefaultConfig()
        } catch (e: Exception) {
            e.printStackTrace()
            return loadDefaultConfig()
        }
    }

    private fun loadDefaultConfig(): List<ObjectInfo> {
        return try {
            val inputStream = context.assets.open("objects_example.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            parseConfigJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseConfigJson(jsonString: String): List<ObjectInfo> {
        val jsonArray = JSONArray(jsonString)
        val objects = mutableListOf<ObjectInfo>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val id = jsonObject.getString("id")
            val name = jsonObject.getString("name")
            val labels = mutableListOf<String>()
            val labelsArray = jsonObject.getJSONArray("labels")
            for (j in 0 until labelsArray.length()) {
                labels.add(labelsArray.getString(j))
            }
            val hintText = jsonObject.getString("hintText")
            val steps = mutableListOf<String>()
            if (jsonObject.has("steps")) {
                val stepsArray = jsonObject.getJSONArray("steps")
                for (j in 0 until stepsArray.length()) {
                    steps.add(stepsArray.getString(j))
                }
            }

            objects.add(ObjectInfo(
                id = id,
                name = name,
                labels = labels,
                hintText = hintText,
                steps = steps
            ))
        }

        return objects
    }

    fun validateConfig(jsonString: String): Boolean {
        return try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                if (!jsonObject.has("id") || !jsonObject.has("name") || 
                    !jsonObject.has("labels") || !jsonObject.has("hintText")) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun saveConfig(objects: List<ObjectInfo>): Boolean {
        try {
            val jsonArray = JSONArray()
            objects.forEach { obj ->
                val jsonObject = JSONObject()
                jsonObject.put("id", obj.id)
                jsonObject.put("name", obj.name)
                jsonObject.put("labels", JSONArray(obj.labels))
                jsonObject.put("hintText", obj.hintText)
                jsonObject.put("steps", JSONArray(obj.steps))
                jsonArray.put(jsonObject)
            }

            val configFile = modelManager.getConfigFile()
            configFile.writeText(jsonArray.toString())
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun findObjectByLabel(label: String): ObjectInfo? {
        val objects = loadConfig()
        val lowerLabel = label.lowercase()
        return objects.find { obj ->
            obj.labels.any { it.lowercase() in lowerLabel || lowerLabel in it.lowercase() }
        }
    }

    fun getConfigVersion(): String {
        val configFile = modelManager.getConfigFile()
        if (!configFile.exists()) {
            return ""
        }
        return configFile.lastModified().toString()
    }

    fun hasConfig(): Boolean {
        return modelManager.hasConfig()
    }
}
