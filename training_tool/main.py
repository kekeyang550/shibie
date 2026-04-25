#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AR物体识别训练工具 - 主入口
用于训练自定义物体识别模型并导出配置文件
"""

import sys
import os
from PyQt5.QtWidgets import QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel, QLineEdit, QTextEdit, QTabWidget, QFileDialog, QMessageBox, QProgressBar, QComboBox, QSpinBox, QListWidget, QGroupBox, QSplitter
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QRect
from PyQt5.QtGui import QPixmap, QImage, QPainter, QPen, QColor, QPicture
from PyQt5.QtGui import QMouseEvent
import cv2
import json


class AnnotationLabel(QLabel):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMouseTracking(True)
        self.start_point = None
        self.end_point = None
        self.is_drawing = False
        self.annotations = []
        self.current_class = ""
        self.pixmap_with_annotations = None

    def setAnnotations(self, annotations):
        self.annotations = annotations
        self.update()

    def setCurrentClass(self, class_name):
        self.current_class = class_name

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton and self.current_class:
            self.start_point = event.pos()
            self.end_point = event.pos()
            self.is_drawing = True
            self.update()

    def mouseMoveEvent(self, event):
        if self.is_drawing and self.start_point:
            self.end_point = event.pos()
            self.update()

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.LeftButton and self.is_drawing and self.current_class:
            self.end_point = event.pos()
            self.is_drawing = False

            rect = QRect(self.start_point, self.end_point).normalized()
            if rect.width() > 10 and rect.height() > 10:
                self.annotations.append({
                    "class": self.current_class,
                    "x": rect.x(),
                    "y": rect.y(),
                    "width": rect.width(),
                    "height": rect.height()
                })
                self.start_point = None
                self.end_point = None
                self.update()

    def paintEvent(self, event):
        super().paintEvent(event)
        painter = QPainter(self)

        if self.start_point and self.end_point and self.is_drawing:
            rect = QRect(self.start_point, self.end_point).normalized()
            pen = QPen(QColor(255, 0, 0), 2)
            painter.setPen(pen)
            painter.drawRect(rect)

        for ann in self.annotations:
            x = ann["x"]
            y = ann["y"]
            w = ann["width"]
            h = ann["height"]
            pen = QPen(QColor(0, 255, 0), 2)
            painter.setPen(pen)
            painter.drawRect(x, y, w, h)

            text = ann.get("class", "")
            if text:
                painter.drawText(x, y - 5, text)


class TrainingThread(QThread):
    progress_signal = pyqtSignal(int)
    log_signal = pyqtSignal(str)
    finished_signal = pyqtSignal(bool, str)

    def __init__(self, data_path, model_type, epochs, batch_size, img_size):
        super().__init__()
        self.data_path = data_path
        self.model_type = model_type
        self.epochs = epochs
        self.batch_size = batch_size
        self.img_size = img_size

    def run(self):
        try:
            from ultralytics import YOLO

            self.log_signal.emit(f"开始训练模型: {self.model_type}")
            self.log_signal.emit(f"数据路径: {self.data_path}")
            self.log_signal.emit(f"训练参数: epochs={self.epochs}, batch={self.batch_size}, imgsz={self.img_size}")

            model = YOLO(f'{self.model_type}.pt')

            results = model.train(
                data=self.data_path,
                epochs=self.epochs,
                batch=self.batch_size,
                imgsz=self.img_size,
                verbose=True
            )

            self.progress_signal.emit(100)
            self.finished_signal.emit(True, "训练完成！")

        except Exception as e:
            self.log_signal.emit(f"训练失败: {str(e)}")
            self.finished_signal.emit(False, str(e))


class DataAnnotationTab(QWidget):

    def __init__(self):
        super().__init__()
        self.init_ui()
        self.current_image = None
        self.current_annotations = []
        self.image_files = []
        self.all_annotations = {}

    def init_ui(self):
        layout = QHBoxLayout()

        left_panel = QWidget()
        left_layout = QVBoxLayout()

        self.image_list = QListWidget()
        self.image_list.itemClicked.connect(self.load_image)
        left_layout.addWidget(QLabel("图片列表:"))
        left_layout.addWidget(self.image_list)

        btn_layout = QHBoxLayout()
        self.btn_load_images = QPushButton("加载图片")
        self.btn_load_images.clicked.connect(self.load_images)
        self.btn_save_annotations = QPushButton("保存标注")
        self.btn_save_annotations.clicked.connect(self.save_annotations)
        btn_layout.addWidget(self.btn_load_images)
        btn_layout.addWidget(self.btn_save_annotations)
        left_layout.addLayout(btn_layout)

        left_layout.addWidget(QLabel("物体类别:"))
        self.class_input = QLineEdit()
        self.class_input.setPlaceholderText("输入类别名称")
        left_layout.addWidget(self.class_input)

        self.btn_add_class = QPushButton("添加类别")
        self.btn_add_class.clicked.connect(self.add_class)
        left_layout.addWidget(self.btn_add_class)

        self.class_list = QListWidget()
        self.class_list.itemClicked.connect(self.on_class_selected)
        left_layout.addWidget(self.class_list)

        left_layout.addWidget(QLabel("当前标注数:"))
        self.annotation_count_label = QLabel("0")
        left_layout.addWidget(self.annotation_count_label)

        self.btn_clear_annotations = QPushButton("清除当前标注")
        self.btn_clear_annotations.clicked.connect(self.clear_annotations)
        left_layout.addWidget(self.btn_clear_annotations)

        left_panel.setLayout(left_layout)
        left_panel.setMaximumWidth(300)

        right_panel = QWidget()
        right_layout = QVBoxLayout()

        self.image_label = AnnotationLabel()
        self.image_label.setAlignment(Qt.AlignCenter)
        self.image_label.setStyleSheet("border: 2px solid #333; background-color: #f0f0f0;")
        self.image_label.setMinimumSize(640, 480)
        right_layout.addWidget(self.image_label)

        self.info_label = QLabel("操作说明: 1.先添加/选择类别 2.在图片上拖拽绘制矩形框")
        self.info_label.setStyleSheet("color: blue; padding: 5px;")
        right_layout.addWidget(self.info_label)

        self.selected_class_label = QLabel("未选择类别")
        self.selected_class_label.setStyleSheet("color: red; font-weight: bold;")
        right_layout.addWidget(self.selected_class_label)

        right_panel.setLayout(right_layout)

        splitter = QSplitter(Qt.Horizontal)
        splitter.addWidget(left_panel)
        splitter.addWidget(right_panel)
        splitter.setSizes([300, 700])

        layout.addWidget(splitter)
        self.setLayout(layout)

    def load_images(self):
        folder = QFileDialog.getExistingDirectory(self, "选择图片文件夹")
        if folder:
            self.image_list.clear()
            self.image_files = []
            for f in os.listdir(folder):
                if f.lower().endswith(('.png', '.jpg', '.jpeg', '.bmp')):
                    full_path = os.path.join(folder, f)
                    self.image_list.addItem(full_path)
                    self.image_files.append(full_path)

    def load_image(self, item):
        if self.current_image and self.current_image in self.image_label.annotations:
            self.all_annotations[self.current_image] = self.image_label.annotations.copy()

        image_path = item.text()
        pixmap = QPixmap(image_path)
        if not pixmap.isNull():
            scaled_pixmap = pixmap.scaled(
                self.image_label.size(),
                Qt.KeepAspectRatio,
                Qt.SmoothTransformation
            )
            self.image_label.setPixmap(scaled_pixmap)
            self.current_image = image_path

            if image_path in self.all_annotations:
                self.image_label.setAnnotations(self.all_annotations[image_path].copy())
            else:
                self.image_label.setAnnotations([])
                self.current_annotations = []

            self.update_annotation_count()
            self.update_saved_count()

    def add_class(self):
        class_name = self.class_input.text().strip()
        if class_name:
            self.class_list.addItem(class_name)
            self.class_input.clear()

    def on_class_selected(self, item):
        class_name = item.text()
        self.image_label.setCurrentClass(class_name)
        self.selected_class_label.setText(f"当前类别: {class_name}")
        self.info_label.setText(f"当前类别: {class_name}，可在图片上绘制矩形框")

    def clear_annotations(self):
        self.current_annotations = []
        self.image_label.setAnnotations([])
        self.update_annotation_count()

    def update_annotation_count(self):
        count = len(self.image_label.annotations)
        self.annotation_count_label.setText(str(count))

    def update_saved_count(self):
        total = len(self.all_annotations)
        total += 1 if (self.current_image and self.current_image in self.image_label.annotations) else 0

    def save_annotations(self):
        if self.current_image:
            if self.image_label.annotations:
                self.all_annotations[self.current_image] = self.image_label.annotations.copy()
            elif self.current_image in self.all_annotations:
                del self.all_annotations[self.current_image]

        if not self.all_annotations:
            QMessageBox.warning(self, "警告", "没有标注数据需要保存")
            return

        save_path, _ = QFileDialog.getSaveFileName(self, "保存标注文件", "", "JSON Files (*.json)")
        if save_path:
            with open(save_path, 'w', encoding='utf-8') as f:
                json.dump(self.all_annotations, f, ensure_ascii=False, indent=2)
            QMessageBox.information(self, "成功", f"已保存 {len(self.all_annotations)} 张图片的标注到:\n{save_path}")


class ModelTrainingTab(QWidget):

    def __init__(self):
        super().__init__()
        self.init_ui()
        self.training_thread = None

    def init_ui(self):
        layout = QVBoxLayout()

        data_group = QGroupBox("数据配置")
        data_layout = QVBoxLayout()

        path_layout = QHBoxLayout()
        path_layout.addWidget(QLabel("数据集路径:"))
        self.data_path_input = QLineEdit()
        self.data_path_input.setPlaceholderText("选择包含images和labels文件夹的数据集路径")
        path_layout.addWidget(self.data_path_input)
        self.btn_browse_data = QPushButton("浏览...")
        self.btn_browse_data.clicked.connect(self.browse_data)
        path_layout.addWidget(self.btn_browse_data)
        data_layout.addLayout(path_layout)

        yaml_layout = QHBoxLayout()
        yaml_layout.addWidget(QLabel("数据配置YAML:"))
        self.yaml_path_input = QLineEdit()
        self.yaml_path_input.setPlaceholderText("选择data.yaml文件")
        yaml_layout.addWidget(self.yaml_path_input)
        self.btn_browse_yaml = QPushButton("浏览...")
        self.btn_browse_yaml.clicked.connect(self.browse_yaml)
        yaml_layout.addWidget(self.btn_browse_yaml)
        data_layout.addLayout(yaml_layout)

        data_group.setLayout(data_layout)
        layout.addWidget(data_group)

        model_group = QGroupBox("模型配置")
        model_layout = QVBoxLayout()

        type_layout = QHBoxLayout()
        type_layout.addWidget(QLabel("模型类型:"))
        self.model_type_combo = QComboBox()
        self.model_type_combo.addItems(["yolov5n", "yolov5s", "yolov5m", "yolov8n", "yolov8s"])
        type_layout.addWidget(self.model_type_combo)
        type_layout.addStretch()
        model_layout.addLayout(type_layout)

        params_layout = QHBoxLayout()

        params_layout.addWidget(QLabel("训练轮数:"))
        self.epochs_spin = QSpinBox()
        self.epochs_spin.setRange(1, 1000)
        self.epochs_spin.setValue(100)
        params_layout.addWidget(self.epochs_spin)

        params_layout.addWidget(QLabel("批次大小:"))
        self.batch_spin = QSpinBox()
        self.batch_spin.setRange(1, 64)
        self.batch_spin.setValue(16)
        params_layout.addWidget(self.batch_spin)

        params_layout.addWidget(QLabel("图像尺寸:"))
        self.imgsz_spin = QSpinBox()
        self.imgsz_spin.setRange(320, 1280)
        self.imgsz_spin.setValue(640)
        self.imgsz_spin.setSingleStep(32)
        params_layout.addWidget(self.imgsz_spin)

        params_layout.addStretch()
        model_layout.addLayout(params_layout)

        model_group.setLayout(model_layout)
        layout.addWidget(model_group)

        control_layout = QHBoxLayout()
        self.btn_start_training = QPushButton("开始训练")
        self.btn_start_training.clicked.connect(self.start_training)
        self.btn_start_training.setStyleSheet("background-color: #4CAF50; color: white; padding: 10px;")
        control_layout.addWidget(self.btn_start_training)

        self.btn_stop_training = QPushButton("停止训练")
        self.btn_stop_training.clicked.connect(self.stop_training)
        self.btn_stop_training.setEnabled(False)
        control_layout.addWidget(self.btn_stop_training)

        control_layout.addStretch()
        layout.addLayout(control_layout)

        self.progress_bar = QProgressBar()
        self.progress_bar.setValue(0)
        layout.addWidget(self.progress_bar)

        layout.addWidget(QLabel("训练日志:"))
        self.log_output = QTextEdit()
        self.log_output.setReadOnly(True)
        self.log_output.setMaximumHeight(200)
        layout.addWidget(self.log_output)

        layout.addStretch()
        self.setLayout(layout)

    def browse_data(self):
        folder = QFileDialog.getExistingDirectory(self, "选择数据集文件夹")
        if folder:
            self.data_path_input.setText(folder)

    def browse_yaml(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "选择数据配置文件", "", "YAML Files (*.yaml *.yml)")
        if file_path:
            self.yaml_path_input.setText(file_path)

    def start_training(self):
        data_path = self.yaml_path_input.text() or self.data_path_input.text()
        if not data_path:
            QMessageBox.warning(self, "警告", "请选择数据集路径或YAML配置文件")
            return

        model_type = self.model_type_combo.currentText()
        epochs = self.epochs_spin.value()
        batch_size = self.batch_spin.value()
        img_size = self.imgsz_spin.value()

        self.log_output.clear()
        self.progress_bar.setValue(0)

        self.training_thread = TrainingThread(data_path, model_type, epochs, batch_size, img_size)
        self.training_thread.progress_signal.connect(self.update_progress)
        self.training_thread.log_signal.connect(self.append_log)
        self.training_thread.finished_signal.connect(self.training_finished)

        self.training_thread.start()
        self.btn_start_training.setEnabled(False)
        self.btn_stop_training.setEnabled(True)

    def stop_training(self):
        if self.training_thread and self.training_thread.isRunning():
            self.training_thread.terminate()
            self.training_thread.wait()
            self.append_log("训练已停止")
            self.training_finished(False, "用户停止")

    def update_progress(self, value):
        self.progress_bar.setValue(value)

    def append_log(self, message):
        self.log_output.append(message)

    def training_finished(self, success, message):
        self.btn_start_training.setEnabled(True)
        self.btn_stop_training.setEnabled(False)

        if success:
            QMessageBox.information(self, "成功", message)
        else:
            QMessageBox.critical(self, "失败", message)


class ModelExportTab(QWidget):

    def __init__(self):
        super().__init__()
        self.init_ui()

    def init_ui(self):
        layout = QVBoxLayout()

        model_layout = QHBoxLayout()
        model_layout.addWidget(QLabel("训练好的模型:"))
        self.model_path_input = QLineEdit()
        self.model_path_input.setPlaceholderText("选择best.pt文件")
        model_layout.addWidget(self.model_path_input)
        self.btn_browse_model = QPushButton("浏览...")
        self.btn_browse_model.clicked.connect(self.browse_model)
        model_layout.addWidget(self.btn_browse_model)
        layout.addLayout(model_layout)

        format_layout = QHBoxLayout()
        format_layout.addWidget(QLabel("导出格式:"))
        self.export_format_combo = QComboBox()
        self.export_format_combo.addItems(["TFLite (推荐)", "ONNX", "TorchScript", "CoreML"])
        format_layout.addWidget(self.export_format_combo)
        format_layout.addStretch()
        layout.addLayout(format_layout)

        self.quantization_check = QPushButton("INT8量化 (减小模型体积)")
        self.quantization_check.setCheckable(True)
        layout.addWidget(self.quantization_check)

        self.btn_export = QPushButton("导出模型")
        self.btn_export.clicked.connect(self.export_model)
        self.btn_export.setStyleSheet("background-color: #2196F3; color: white; padding: 10px;")
        layout.addWidget(self.btn_export)

        config_group = QGroupBox("配置文件生成")
        config_layout = QVBoxLayout()

        config_layout.addWidget(QLabel("物体信息配置:"))
        self.config_text = QTextEdit()
        self.config_text.setPlaceholderText('[{"id": "object_id", "name": "物体名称", "labels": ["标签1"], "hintText": "识别提示", "steps": ["步骤1"]}]')
        config_layout.addWidget(self.config_text)

        self.btn_save_config = QPushButton("保存配置文件")
        self.btn_save_config.clicked.connect(self.save_config)
        config_layout.addWidget(self.btn_save_config)

        config_group.setLayout(config_layout)
        layout.addWidget(config_group)

        layout.addStretch()
        self.setLayout(layout)

    def browse_model(self):
        file_path, _ = QFileDialog.getOpenFileName(self, "选择模型文件", "", "Model Files (*.pt)")
        if file_path:
            self.model_path_input.setText(file_path)

    def export_model(self):
        model_path = self.model_path_input.text()
        if not model_path or not os.path.exists(model_path):
            QMessageBox.warning(self, "警告", "请选择有效的模型文件")
            return

        try:
            from ultralytics import YOLO

            model = YOLO(model_path)

            format_map = {"TFLite (推荐)": "tflite", "ONNX": "onnx", "TorchScript": "torchscript", "CoreML": "coreml"}
            export_format = format_map[self.export_format_combo.currentText()]

            model.export(format=export_format)

            QMessageBox.information(self, "成功", f"模型已导出为 {export_format} 格式")

        except Exception as e:
            QMessageBox.critical(self, "失败", f"导出失败: {str(e)}")

    def save_config(self):
        config_text = self.config_text.toPlainText().strip()
        if not config_text:
            QMessageBox.warning(self, "警告", "请输入配置内容")
            return

        try:
            config_data = json.loads(config_text)

            save_path, _ = QFileDialog.getSaveFileName(self, "保存配置文件", "objects.json", "JSON Files (*.json)")
            if save_path:
                with open(save_path, 'w', encoding='utf-8') as f:
                    json.dump(config_data, f, ensure_ascii=False, indent=2)
                QMessageBox.information(self, "成功", "配置文件已保存")

        except json.JSONDecodeError:
            QMessageBox.critical(self, "错误", "JSON格式错误，请检查配置内容")


class MainWindow(QMainWindow):

    def __init__(self):
        super().__init__()
        self.setWindowTitle("AR物体识别训练工具")
        self.setGeometry(100, 100, 1200, 800)
        self.init_ui()

    def init_ui(self):
        self.tabs = QTabWidget()

        self.tab_annotation = DataAnnotationTab()
        self.tabs.addTab(self.tab_annotation, "数据标注")

        self.tab_training = ModelTrainingTab()
        self.tabs.addTab(self.tab_training, "模型训练")

        self.tab_export = ModelExportTab()
        self.tabs.addTab(self.tab_export, "模型导出")

        self.setCentralWidget(self.tabs)

        self.statusBar().showMessage("就绪")


def main():
    app = QApplication(sys.argv)
    app.setStyle('Fusion')

    app.setStyleSheet("""
        QMainWindow { background-color: #f5f5f5; }
        QGroupBox { font-weight: bold; border: 1px solid #cccccc; border-radius: 5px; margin-top: 10px; padding-top: 10px; }
        QGroupBox::title { subcontrol-origin: margin; left: 10px; padding: 0 5px; }
        QPushButton { padding: 8px 16px; border-radius: 4px; border: none; }
        QPushButton:hover { opacity: 0.8; }
        QTextEdit { border: 1px solid #cccccc; border-radius: 4px; }
        QLineEdit { padding: 6px; border: 1px solid #cccccc; border-radius: 4px; }
    """)

    window = MainWindow()
    window.show()

    sys.exit(app.exec_())


if __name__ == '__main__':
    main()
