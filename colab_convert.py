# Google Colab 转换脚本
# 在 Colab 中运行此单元格

# 1. 安装依赖
!pip install ultralytics onnx onnx-tf tensorflow -q

# 2. 上传您的 ONNX 模型
# 或者直接下载（如果模型在云端）

# 3. 转换 ONNX -> TFLite
import os
import onnx
from onnx import numpy_helper
import onnx_tf.backend as tf_backend
import tensorflow as tf

ONNX_MODEL = "/content/best.onnx"  # 您的ONNX模型路径
OUTPUT_DIR = "/content/tflite_model"
os.makedirs(OUTPUT_DIR, exist_ok=True)

print("1. 加载ONNX模型...")
onnx_model = onnx.load(ONNX_MODEL)
print("ONNX模型加载成功")

print("2. 转换为TensorFlow格式...")
tf_rep = tf_backend.prepare(onnx_model, device='CPU')
print("TensorFlow转换成功")

print("3. 导出为SavedModel格式...")
saved_model_path = os.path.join(OUTPUT_DIR, "saved_model")
tf_rep.export_graph(saved_model_path)
print(f"SavedModel导出成功: {saved_model_path}")

print("4. 转换为TFLite格式...")
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

# 5. 下载模型
from google.colab import files
files.download(tflite_path)
print("模型已准备好下载!")
