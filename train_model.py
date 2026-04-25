#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""直接使用Python 3.14.3训练模型"""

import sys
import os

print("使用Python:", sys.executable)
print("版本:", sys.version)

try:
    from ultralytics import YOLO
    print("Ultralytics已加载!")
except ImportError as e:
    print("Ultralytics未安装:", e)
    sys.exit(1)

# 数据集配置
DATA_YAML = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\dataset\data.yaml"
MODEL_TYPE = "yolov8n"
EPOCHS = 50
IMG_SIZE = 640
BATCH_SIZE = 8

print(f"\n开始训练...")
print(f"数据集: {DATA_YAML}")
print(f"模型: {MODEL_TYPE}")
print(f"轮数: {EPOCHS}")
print(f"图像尺寸: {IMG_SIZE}")

try:
    model = YOLO(f'{MODEL_TYPE}.pt')
    results = model.train(
        data=DATA_YAML,
        epochs=EPOCHS,
        imgsz=IMG_SIZE,
        batch=BATCH_SIZE,
        project=r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie",
        name="train_output",
        exist_ok=True,
        verbose=True
    )
    print("\n训练完成!")
    print(f"模型保存在: C:\\Users\\Administrator\\Desktop\\MyApplicationrtsp\\shibie\\train_output\\weights\\best.pt")
except Exception as e:
    print(f"\n训练失败: {e}")
    import traceback
    traceback.print_exc()
