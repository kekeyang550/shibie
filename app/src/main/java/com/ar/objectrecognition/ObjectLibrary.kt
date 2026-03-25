package com.ar.objectrecognition

data class ObjectInfo(
    val id: String,
    val name: String,
    val labels: List<String>,
    val hintText: String,
    val steps: List<String> = emptyList()
)

object ObjectLibrary {
    
    private val objects = mutableListOf<ObjectInfo>()
    
    init {
        initDefaultObjects()
    }
    
    private fun initDefaultObjects() {
        objects.addAll(
            listOf(
                ObjectInfo(
                    id = "screwdriver",
                    name = "螺丝刀",
                    labels = listOf("螺丝刀", "screwdriver", "工具"),
                    hintText = "这是一把螺丝刀，用于拧紧或松开螺丝",
                    steps = listOf(
                        "选择合适的刀头",
                        "将刀头对准螺丝",
                        "顺时针拧紧，逆时针松开"
                    )
                ),
                ObjectInfo(
                    id = "wrench",
                    name = "扳手",
                    labels = listOf("扳手", "wrench", "工具"),
                    hintText = "这是一把扳手，用于拧紧或松开螺母",
                    steps = listOf(
                        "选择合适尺寸的扳手",
                        "将扳手套在螺母上",
                        "顺时针拧紧，逆时针松开"
                    )
                ),
                ObjectInfo(
                    id = "cup",
                    name = "杯子",
                    labels = listOf("杯子", "cup", "容器"),
                    hintText = "这是一个杯子，可以用来喝水"
                )
            )
        )
    }
    
    fun getAllObjects(): List<ObjectInfo> = objects.toList()
    
    fun findObjectByLabel(label: String): ObjectInfo? {
        val lowerLabel = label.lowercase()
        return objects.find { obj ->
            obj.labels.any { it.lowercase() in lowerLabel || lowerLabel in it.lowercase() }
        }
    }
    
    fun addObject(objectInfo: ObjectInfo) {
        objects.add(objectInfo)
    }
    
    fun removeObject(id: String) {
        objects.removeIf { it.id == id }
    }
}
