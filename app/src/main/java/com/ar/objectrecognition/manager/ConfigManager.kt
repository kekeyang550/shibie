package com.ar.objectrecognition.manager

import android.content.Context
import com.ar.objectrecognition.ObjectInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader

class ConfigManager(private val context: Context) {

    private val modelManager = ModelManager(context)

    fun loadConfig(): List<ObjectInfo> {
        val configFile = modelManager.getConfigFile()
        if (!configFile.exists()) {
            return emptyList()
        }

        try {
            val jsonString = configFile.readText()
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
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
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
