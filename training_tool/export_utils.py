#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模型导出工具
用于导出YOLOv5/v8模型为TFLite格式
"""

import argparse
import os
import sys
from pathlib import Path

def parse_args():
    parser = argparse.ArgumentParser(description='导出YOLO模型为TFLite格式')
    parser.add_argument('--model', type=str, required=True, help='模型文件路径 (.pt)')
    parser.add_argument('--output', type=str, default=None, help='输出目录')
    parser.add_argument('--img-size', type=int, default=640, help='输入图像尺寸')
    parser.add_argument('--batch-size', type=int, default=1, help='批量大小')
    parser.add_argument('--quantize', action='store_true', help='启用INT8量化')
    return parser.parse_args()

def export_model(args):
    try:
        from ultralytics import YOLO
        
        # 加载模型
        model = YOLO(args.model)
        
        # 导出为TFLite
        export_format = 'tflite'
        
        print(f"开始导出模型: {args.model}")
        print(f"导出格式: {export_format}")
        print(f"输入尺寸: {args.img_size}")
        print(f"批量大小: {args.batch_size}")
        
        # 执行导出
        success = model.export(
            format=export_format,
            imgsz=args.img_size,
            batch=args.batch_size,
            int8=args.quantize
        )
        
        print(f"导出成功: {success}")
        return success
        
    except Exception as e:
        print(f"导出失败: {e}")
        import traceback
        traceback.print_exc()
        return None

if __name__ == '__main__':
    args = parse_args()
    result = export_model(args)
    sys.exit(0 if result else 1)
