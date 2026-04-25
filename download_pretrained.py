#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""下载预训练YOLOv8n TFLite模型"""

import sys
print("使用Python:", sys.executable)

from ultralytics import YOLO

# 下载YOLOv8n预训练模型（不是我们训练的，是通用的）
print("下载YOLOv8n预训练模型...")
model = YOLO('yolov8n.pt')

# 尝试直接导出为TFLite
print("导出为TFLite格式...")
try:
    result = model.export(format='tflite')
    print(f"导出成功: {result}")
except Exception as e:
    print(f"TFLite导出失败: {e}")
    print("\n尝试其他方式...")
    # 导出为ONNX
    onnx_result = model.export(format='onnx')
    print(f"ONNX导出: {onnx_result}")
