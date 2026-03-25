# AR物体识别训练工具

这是一个用于训练自定义物体识别模型的PC端工具，支持数据标注、模型训练和导出功能。

## 功能特性

- **数据标注**：可视化图片标注工具
- **模型训练**：基于YOLOv5/v8的模型训练
- **模型导出**：支持导出为TFLite、ONNX等格式
- **配置生成**：自动生成Android应用配置文件

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

### 1. 启动GUI工具

```bash
python main.py
```

### 2. 命令行工具

#### 数据准备

```bash
python data_preparation.py --project ./my_dataset --classes 螺丝刀 扳手 锤子
```

#### 模型导出

```bash
python export_utils.py --model best.pt --output ./exported --quantize
```

## 工作流程

1. **准备数据**：收集并标注图片
2. **训练模型**：使用GUI或命令行训练
3. **导出模型**：导出为TFLite格式
4. **生成配置**：创建objects.json配置文件
5. **同步到设备**：通过Android应用同步功能更新模型

## 目录结构

```
training_tool/
├── main.py              # GUI主程序
├── export_utils.py      # 模型导出工具
├── data_preparation.py  # 数据准备工具
├── requirements.txt     # 依赖列表
└── README.md           # 使用说明
```
