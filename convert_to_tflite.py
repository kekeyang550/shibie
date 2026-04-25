#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ONNX转TFLite"""

import sys
print("使用Python:", sys.executable)

try:
    import onnx
    from onnx import numpy_helper
    print("ONNX已加载")
except ImportError as e:
    print("ONNX未安装:", e)
    sys.exit(1)

try:
    import onnx_tf
    print("ONNX-TF已加载")
except ImportError as e:
    print("ONNX-TF未安装:", e)
    sys.exit(1)

try:
    import tensorflow as tf
    print("TensorFlow已加载:", tf.__version__)
except ImportError as e:
    print("TensorFlow未安装:", e)
    sys.exit(1)

ONNX_MODEL = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\train_output\weights\best.onnx"
OUTPUT_DIR = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\exported_model"

import os
os.makedirs(OUTPUT_DIR, exist_ok=True)

print(f"\n1. 加载ONNX模型...")
onnx_model = onnx.load(ONNX_MODEL)
print("ONNX模型加载成功")

print(f"\n2. 转换为TensorFlow格式...")
import onnx_tf.backend as tf_backend
tf_rep = tf_backend.prepare(onnx_model, device='CPU')
print("TensorFlow转换成功")

print(f"\n3. 导出为SavedModel格式...")
saved_model_path = os.path.join(OUTPUT_DIR, "saved_model")
tf_rep.export_graph(saved_model_path)
print(f"SavedModel导出成功: {saved_model_path}")

print(f"\n4. 转换为TFLite格式...")
converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

tflite_path = os.path.join(OUTPUT_DIR, "model.tflite")
with open(tflite_path, 'wb') as f:
    f.write(tflite_model)

size = os.path.getsize(tflite_path) / (1024 * 1024)
print(f"\n转换完成!")
print(f"TFLite模型: {tflite_path}")
print(f"模型大小: {size:.2f} MB")
