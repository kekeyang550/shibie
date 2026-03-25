# AR物体识别应用

这是一个基于Google ML Kit和ARCore开发的Android应用，用于AR智能眼镜上的物体识别和教学提示。

## 技术特点

- ✅ **完全本地运行**：无需云端或外接设备，所有功能在设备本地完成
- ✅ **ML Kit物体检测**：使用Google ML Kit进行实时物体识别
- ✅ **ARCore支持**：为AR眼镜提供增强现实体验
- ✅ **自定义物体库**：可配置识别物体和对应的提示内容

## 项目结构

```
d:\apk\shibie\
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/ar/objectrecognition/
│   │       │   ├── MainActivity.kt              # 主Activity
│   │       │   ├── MainViewModel.kt             # 数据模型
│   │       │   ├── ObjectDetectionAnalyzer.kt   # 物体检测分析器
│   │       │   └── ObjectLibrary.kt             # 物体库管理
│   │       ├── res/
│   │       │   ├── layout/                      # 布局文件
│   │       │   └── values/                      # 资源文件
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 快速开始

### 1. 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK API 24+
- 支持ARCore的Android设备

### 2. 构建项目

```bash
# 克隆或打开项目
cd d:\apk\shibie

# 使用Gradle构建
./gradlew build
```

### 3. 运行应用

1. 在Android Studio中打开项目
2. 连接支持ARCore的Android设备
3. 点击运行按钮或执行：
```bash
./gradlew installDebug
```

## 自定义物体识别

### 使用ML Kit AutoML训练自定义模型

#### 步骤1：准备训练数据

1. 收集每个物体的图片（建议每个物体50-200张）
2. 在不同光照、角度、背景下拍摄
3. 使用LabelImg或CVAT标注图片

#### 步骤2：使用Google AutoML Vision Edge

1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建新项目并启用Vision API
3. 上传标注好的图片数据集
4. 训练模型（选择"Edge"模型类型）
5. 导出TFLite模型

#### 步骤3：集成到应用中

1. 将导出的 `.tflite` 模型文件放入 `app/src/main/assets/` 目录
2. 修改 `ObjectDetectionAnalyzer.kt` 使用自定义模型：

```kotlin
val localModel = LocalModel.Builder()
    .setAssetFilePath("custom_model.tflite")
    .build()

val options = CustomObjectDetectorOptions.Builder(localModel)
    .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
    .enableClassification()
    .setClassificationConfidenceThreshold(0.5f)
    .setMaxPerObjectLabelCount(3)
    .build()

val objectDetector = ObjectDetection.getClient(options)
```

### 使用YOLOv5训练自定义模型

#### 1. 环境准备

```bash
# 安装依赖
pip install torch torchvision
git clone https://github.com/ultralytics/yolov5
cd yolov5
pip install -r requirements.txt
```

#### 2. 准备数据集

数据集结构：
```
dataset/
├── images/
│   ├── train/
│   └── val/
└── labels/
    ├── train/
    └── val/
```

#### 3. 训练模型

```bash
python train.py --img 640 --batch 16 --epochs 100 --data custom.yaml --weights yolov5n.pt
```

#### 4. 导出为TFLite

```bash
python export.py --weights runs/train/exp/weights/best.pt --include tflite
```

## 物体库配置

在 `ObjectLibrary.kt` 中添加自定义物体：

```kotlin
ObjectInfo(
    id = "your_object_id",
    name = "物体名称",
    labels = listOf("标签1", "标签2", "标签3"),
    hintText = "识别到物体时显示的提示文字",
    steps = listOf(
        "步骤1",
        "步骤2",
        "步骤3"
    )
)
```

## AR眼镜适配

### 常见AR眼镜

| 设备 | 系统 | ARCore支持 |
|------|------|-----------|
| Meta Quest 2/3 | Android | ✅ |
| HoloLens 2 | Windows | ❌ (使用HoloLens SDK) |
| Vuzix Blade | Android | ✅ |
| RealWear Navigator | Android | ✅ |
| Google Glass Enterprise | Android | ✅ |

### 适配要点

1. **屏幕适配**：AR眼镜通常有特殊的屏幕比例和分辨率
2. **交互方式**：支持语音控制、手势控制、触摸板等
3. **性能优化**：AR眼镜算力有限，需要进一步优化模型
4. **电池优化**：减少后台活动，优化相机和ML推理

## 性能优化建议

### 模型优化

- 使用量化模型（INT8）
- 选择轻量模型：YOLOv5n、MobileNet、EfficientDet-Lite0
- 模型剪枝和知识蒸馏

### 应用优化

- 降低检测帧率（如10-15 FPS）
- 使用CameraX的 `STRATEGY_KEEP_ONLY_LATEST`
- 在后台线程执行推理
- 合理设置置信度阈值

## 许可证

本项目仅供学习和研究使用。

## 技术支持

- [ML Kit文档](https://developers.google.com/ml-kit)
- [ARCore文档](https://developers.google.com/ar)
- [TensorFlow Lite](https://www.tensorflow.org/lite)
