#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""转换标注数据为YOLO格式"""

import os
import json
from PIL import Image

# 路径配置
SOURCE_DIR = r"C:\Users\Administrator\Pictures\钳子"
ANNOTATION_FILE = os.path.join(SOURCE_DIR, "gongju-qianzi.json")
OUTPUT_DIR = r"C:\Users\Administrator\Desktop\MyApplicationrtsp\shibie\dataset"

# 创建输出目录
TRAIN_IMAGES_DIR = os.path.join(OUTPUT_DIR, "train", "images")
TRAIN_LABELS_DIR = os.path.join(OUTPUT_DIR, "train", "labels")

os.makedirs(TRAIN_IMAGES_DIR, exist_ok=True)
os.makedirs(TRAIN_LABELS_DIR, exist_ok=True)

# 类别列表
CLASSES = ["工具-钳子"]
class_mapping = {name: idx for idx, name in enumerate(CLASSES)}

# 读取标注文件
with open(ANNOTATION_FILE, 'r', encoding='utf-8') as f:
    annotations = json.load(f)

print(f"读取到 {len(annotations)} 张图片的标注")

# 转换每张图片
converted_count = 0
for img_path, anns in annotations.items():
    # 获取图片文件名
    img_name = os.path.basename(img_path)
    label_name = os.path.splitext(img_name)[0] + ".txt"

    # 处理路径分隔符
    img_path = img_path.replace('\\\\', '\\')

    # 检查图片是否存在
    if not os.path.exists(img_path):
        print(f"图片不存在: {img_path}")
        continue

    # 获取图片尺寸
    with Image.open(img_path) as img:
        img_width, img_height = img.size

    # 复制图片到数据集
    import shutil
    dest_img_path = os.path.join(TRAIN_IMAGES_DIR, img_name)
    shutil.copy(img_path, dest_img_path)

    # 转换标注为YOLO格式
    yolo_annotations = []
    for ann in anns:
        class_name = ann.get("class", "")
        if class_name not in class_mapping:
            print(f"未知类别: {class_name}")
            continue

        class_id = class_mapping[class_name]

        # 计算YOLO格式的坐标 (归一化的中心点和宽高)
        x = ann["x"]
        y = ann["y"]
        w = ann["width"]
        h = ann["height"]

        # 计算中心点
        x_center = x + w / 2
        y_center = y + h / 2

        # 归一化
        x_center_norm = x_center / img_width
        y_center_norm = y_center / img_height
        w_norm = w / img_width
        h_norm = h / img_height

        # 确保值在0-1范围内
        x_center_norm = max(0, min(1, x_center_norm))
        y_center_norm = max(0, min(1, y_center_norm))
        w_norm = max(0, min(1, w_norm))
        h_norm = max(0, min(1, h_norm))

        yolo_annotations.append(f"{class_id} {x_center_norm:.6f} {y_center_norm:.6f} {w_norm:.6f} {h_norm:.6f}")

    # 保存标注文件
    label_path = os.path.join(TRAIN_LABELS_DIR, label_name)
    with open(label_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(yolo_annotations))

    print(f"转换: {img_name} -> {label_name}")
    converted_count += 1

print(f"\n转换完成! 共转换 {converted_count} 张图片")

# 创建data.yaml
yaml_content = f"""path: {OUTPUT_DIR}
train: train/images
val: train/images

nc: {len(CLASSES)}
names: {CLASSES}
"""

yaml_path = os.path.join(OUTPUT_DIR, "data.yaml")
with open(yaml_path, 'w', encoding='utf-8') as f:
    f.write(yaml_content)

print(f"\n已创建 data.yaml:")
print(yaml_content)
print(f"\n数据集路径: {OUTPUT_DIR}")
