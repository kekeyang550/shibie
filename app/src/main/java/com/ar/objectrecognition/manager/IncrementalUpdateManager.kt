package com.ar.objectrecognition.manager

import android.content.Context
import com.ar.objectrecognition.ObjectInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ObjectUpdate(
    val objectId: String,
    val action: UpdateAction,
    val objectInfo: ObjectInfo? = null,
    val modelDelta: ByteArray? = null
)

enum class UpdateAction {
    ADD,      // 添加新物体
    UPDATE,   // 更新现有物体
    DELETE    // 删除物体
}

data class UpdatePackage(
    val versionFrom: Int,
    val versionTo: Int,
    val updates: List<ObjectUpdate>,
    val totalSize: Long,
    val checksum: String
)

class IncrementalUpdateManager(private val context: Context) {

    private val configManager = ConfigManager(context)
    private val modelManager = ModelManager(context)
    private val versionManager = VersionManager(context)

    companion object {
        private const val UPDATE_DIR = "updates"
        private const val DELTA_FILE_SUFFIX = ".delta"
    }

    private val updateDir: File by lazy {
        File(context.filesDir, UPDATE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 计算两个配置之间的差异
     */
    fun calculateConfigDelta(
        oldConfig: List<ObjectInfo>,
        newConfig: List<ObjectInfo>
    ): List<ObjectUpdate> {
        val updates = mutableListOf<ObjectUpdate>()
        val oldMap = oldConfig.associateBy { it.id }
        val newMap = newConfig.associateBy { it.id }

        // 找出新增的物体
        newMap.forEach { (id, newObj) ->
            if (!oldMap.containsKey(id)) {
                updates.add(ObjectUpdate(
                    objectId = id,
                    action = UpdateAction.ADD,
                    objectInfo = newObj
                ))
            } else {
                // 检查是否有更新
                val oldObj = oldMap[id]!!
                if (hasObjectChanged(oldObj, newObj)) {
                    updates.add(ObjectUpdate(
                        objectId = id,
                        action = UpdateAction.UPDATE,
                        objectInfo = newObj
                    ))
                }
            }
        }

        // 找出删除的物体
        oldMap.forEach { (id, _) ->
            if (!newMap.containsKey(id)) {
                updates.add(ObjectUpdate(
                    objectId = id,
                    action = UpdateAction.DELETE
                ))
            }
        }

        return updates
    }

    /**
     * 应用增量更新
     */
    fun applyIncrementalUpdate(updatePackage: UpdatePackage): Boolean {
        return try {
            val currentConfig = configManager.loadConfig().toMutableList()
            val configMap = currentConfig.associateBy { it.id }.toMutableMap()

            updatePackage.updates.forEach { update ->
                when (update.action) {
                    UpdateAction.ADD -> {
                        update.objectInfo?.let { configMap[it.id] = it }
                    }
                    UpdateAction.UPDATE -> {
                        update.objectInfo?.let { configMap[it.id] = it }
                    }
                    UpdateAction.DELETE -> {
                        configMap.remove(update.objectId)
                    }
                }
            }

            // 保存更新后的配置
            val newConfig = configMap.values.toList()
            configManager.saveConfig(newConfig)

            // 如果有模型增量，应用模型更新
            updatePackage.updates.forEach { update ->
                if (update.modelDelta != null && update.modelDelta.isNotEmpty()) {
                    applyModelDelta(update.objectId, update.modelDelta)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 生成更新包（用于服务器端）
     */
    fun generateUpdatePackage(
        oldConfig: List<ObjectInfo>,
        newConfig: List<ObjectInfo>,
        oldVersion: Int,
        newVersion: Int
    ): UpdatePackage {
        val updates = calculateConfigDelta(oldConfig, newConfig)
        var totalSize = 0L

        // 计算总大小
        updates.forEach { update ->
            totalSize += update.objectInfo?.let { 
                it.toString().length.toLong() 
            } ?: 0L
            totalSize += update.modelDelta?.size?.toLong() ?: 0L
        }

        return UpdatePackage(
            versionFrom = oldVersion,
            versionTo = newVersion,
            updates = updates,
            totalSize = totalSize,
            checksum = generateChecksum(updates)
        )
    }

    /**
     * 检查是否需要增量更新
     */
    fun needsIncrementalUpdate(serverVersion: Int): Boolean {
        val currentVersion = versionManager.getCurrentVersion()
        return currentVersion != null && 
               serverVersion > currentVersion.versionCode &&
               serverVersion - currentVersion.versionCode <= 5  // 最多支持5个版本的增量更新
    }

    /**
     * 保存更新包到本地
     */
    fun saveUpdatePackage(updatePackage: UpdatePackage): File {
        val updateFile = File(updateDir, "update_${updatePackage.versionFrom}_${updatePackage.versionTo}.json")
        
        val jsonObject = JSONObject().apply {
            put("versionFrom", updatePackage.versionFrom)
            put("versionTo", updatePackage.versionTo)
            put("totalSize", updatePackage.totalSize)
            put("checksum", updatePackage.checksum)
            
            val updatesArray = JSONArray()
            updatePackage.updates.forEach { update ->
                val updateObj = JSONObject().apply {
                    put("objectId", update.objectId)
                    put("action", update.action.name)
                    update.objectInfo?.let { 
                        put("objectInfo", objectToJson(it))
                    }
                }
                updatesArray.put(updateObj)
            }
            put("updates", updatesArray)
        }

        updateFile.writeText(jsonObject.toString())
        return updateFile
    }

    /**
     * 加载本地保存的更新包
     */
    fun loadUpdatePackage(fromVersion: Int, toVersion: Int): UpdatePackage? {
        val updateFile = File(updateDir, "update_${fromVersion}_${toVersion}.json")
        if (!updateFile.exists()) return null

        return try {
            val json = JSONObject(updateFile.readText())
            val updates = mutableListOf<ObjectUpdate>()
            
            val updatesArray = json.getJSONArray("updates")
            for (i in 0 until updatesArray.length()) {
                val updateObj = updatesArray.getJSONObject(i)
                updates.add(ObjectUpdate(
                    objectId = updateObj.getString("objectId"),
                    action = UpdateAction.valueOf(updateObj.getString("action")),
                    objectInfo = if (updateObj.has("objectInfo")) {
                        jsonToObject(updateObj.getJSONObject("objectInfo"))
                    } else null
                ))
            }

            UpdatePackage(
                versionFrom = json.getInt("versionFrom"),
                versionTo = json.getInt("versionTo"),
                updates = updates,
                totalSize = json.getLong("totalSize"),
                checksum = json.getString("checksum")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 清理旧的更新包
     */
    fun cleanupOldUpdates(keepLatest: Int = 3) {
        val updateFiles = updateDir.listFiles { file ->
            file.name.endsWith(".json") && file.name.startsWith("update_")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (updateFiles.size > keepLatest) {
            updateFiles.subList(keepLatest, updateFiles.size).forEach { it.delete() }
        }
    }

    private fun hasObjectChanged(oldObj: ObjectInfo, newObj: ObjectInfo): Boolean {
        return oldObj.name != newObj.name ||
               oldObj.labels != newObj.labels ||
               oldObj.hintText != newObj.hintText ||
               oldObj.steps != newObj.steps
    }

    private fun applyModelDelta(objectId: String, delta: ByteArray) {
        // 这里可以实现模型增量更新的逻辑
        // 例如：使用模型补丁技术更新特定类别的检测权重
    }

    private fun objectToJson(obj: ObjectInfo): JSONObject {
        return JSONObject().apply {
            put("id", obj.id)
            put("name", obj.name)
            put("labels", JSONArray(obj.labels))
            put("hintText", obj.hintText)
            put("steps", JSONArray(obj.steps))
        }
    }

    private fun jsonToObject(json: JSONObject): ObjectInfo {
        val labels = mutableListOf<String>()
        val labelsArray = json.getJSONArray("labels")
        for (i in 0 until labelsArray.length()) {
            labels.add(labelsArray.getString(i))
        }

        val steps = mutableListOf<String>()
        val stepsArray = json.getJSONArray("steps")
        for (i in 0 until stepsArray.length()) {
            steps.add(stepsArray.getString(i))
        }

        return ObjectInfo(
            id = json.getString("id"),
            name = json.getString("name"),
            labels = labels,
            hintText = json.getString("hintText"),
            steps = steps
        )
    }

    private fun generateChecksum(updates: List<ObjectUpdate>): String {
        val data = updates.joinToString { "${it.objectId}_${it.action}" }
        return hashString(data)
    }

    private fun hashString(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
