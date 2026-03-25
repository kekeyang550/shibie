#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据准备工具
用于准备YOLO训练所需的数据集
"""

import os
import json
import shutil
from pathlib import Path
from typing import List, Dict
import yaml

class DataPreparation:
    """数据准备类"""
    
    def __init__(self, project_dir: str):
        self.project_dir = Path(project_dir)
        self.images_dir = self.project_dir / "images"
        self.labels_dir = self.project_dir / "labels"
        
    def create_project_structure(self):
        """创建项目目录结构"""
        # 创建目录
        for split in ["train", "val", "test"]:
            (self.images_dir / split).mkdir(parents=True, exist_ok=True)
            (self.labels_dir / split).mkdir(parents=True, exist_ok=True)
            
        print(f"项目结构已创建: {self.project_dir}")
        
    def generate_yaml_config(self, classes: List[str], dataset_name: str = "custom"):
        """生成YAML配置文件"""
        config = {
            "path": str(self.project_dir.absolute()),
            "train": "images/train",
            "val": "images/val",
            "test": "images/test",
            "nc": len(classes),
            "names": {i: name for i, name in enumerate(classes)}
        }
        
        yaml_path = self.project_dir / f"{dataset_name}.yaml"
        with open(yaml_path, 'w', encoding='utf-8') as f:
            yaml.dump(config, f, allow_unicode=True, sort_keys=False)
            
        print(f"YAML配置文件已生成: {yaml_path}")
        return yaml_path
        
    def organize_images(self, image_files: List[str], split: str = "train"):
        """组织图片到对应目录"""
        target_dir = self.images_dir / split
        
        for img_file in image_files:
            src_path = Path(img_file)
            if src_path.exists():
                dst_path = target_dir / src_path.name
                shutil.copy2(src_path, dst_path)
                
        print(f"已复制 {len(image_files)} 张图片到 {target_dir}")
        
    def create_label_file(self, image_name: str, annotations: List[Dict], split: str = "train"):
        """创建标注文件"""
        label_path = self.labels_dir / split / f"{Path(image_name).stem}.txt"
        
        with open(label_path, 'w') as f:
            for ann in annotations:
                # YOLO格式: class_id x_center y_center width height (归一化)
                line = f"{ann['class_id']} {ann['x_center']} {ann['y_center']} {ann['width']} {ann['height']}\n"
                f.write(line)
                
    def generate_config_json(self, objects_info: List[Dict], output_path: str = None):
        """生成Android应用配置文件"""
        if output_path is None:
            output_path = self.project_dir / "objects.json"
            
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(objects_info, f, ensure_ascii=False, indent=2)
            
        print(f"配置文件已生成: {output_path}")
        return output_path

def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='准备YOLO训练数据')
    parser.add_argument('--project', type=str, required=True, help='项目目录')
    parser.add_argument('--classes', type=str, nargs='+', required=True, help='物体类别列表')
    parser.add_argument('--images', type=str, nargs='+', help='图片文件列表')
    
    args = parser.parse_args()
    
    # 创建数据准备工具
    prep = DataPreparation(args.project)
    
    # 创建项目结构
    prep.create_project_structure()
    
    # 生成YAML配置
    prep.generate_yaml_config(args.classes)
    
    # 如果有图片，组织图片
    if args.images:
        prep.organize_images(args.images)
        
    print("数据准备完成！")

if __name__ == '__main__':
    main()
