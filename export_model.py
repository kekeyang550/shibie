#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导出模型为TFLite格式"""

import sys
print("使用Python:", sys.executable)

from ultralytics import YOLO

MODEL_PATH = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\train_output\weights\best.pt"
OUTPUT_DIR = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\exported_model"

print(f"加载模型: {MODEL_PATH}")
model = YOLO(MODEL_PATH)

print("导出为TFLite格式...")
result = model.export(format='tflite')

print(f"\n导出完成!")
print(f"模型路径: {result}")

import os
if os.path.exists(result):
    size = os.path.getsize(result) / (1024 * 1024)
    print(f"模型大小: {size:.2f} MB")
