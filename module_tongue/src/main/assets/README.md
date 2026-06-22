# TFLite 模型文件目录

本目录用于存放舌诊模块的 TFLite 模型文件。

## 需要的模型文件

请将以下 `.tflite` 模型文件放置到本目录：

### 1. `tongue_unet_segmenter.tflite` — 舌体分割模型

- **架构**: U-Net
- **输入**: `[1, 256, 256, 3]` float32，RGB 图像，归一化到 [0, 1]
- **输出**: `[1, 256, 256, 1]` float32，sigmoid 激活的 mask
- **用途**: 从舌象图像中分割出舌体区域
- **对应的 Kotlin 类**: `TongueSegmenterTFLite`

### 2. `tongue_color_mobilenetv3.tflite` — 舌色分类模型

- **架构**: MobileNetV3-Small
- **输入**: `[1, 224, 224, 3]` float32，RGB 图像，归一化到 [-1, 1]
- **输出**: `[1, 8]` float32，softmax 概率分布
- **分类标签**（按输出索引顺序）:
  - 0: 淡白舌
  - 1: 淡红舌
  - 2: 红舌
  - 3: 红绛舌
  - 4: 紫暗舌
  - 5: 暗红舌
  - 6: 青紫舌
  - 7: 其他
- **对应的 Kotlin 类**: `TongueColorClassifier`

### 3. `tongue_zerodce_enhancer.tflite` — 舌象图像增强模型

- **架构**: Zero-DCE (Zero-Reference Deep Curve Estimation)
- **输入**: `[1, 256, 256, 3]` float32，RGB 图像，归一化到 [0, 1]
- **输出**: `[1, 256, 256, 3]` float32，增强后的 RGB 图像，值域 [0, 1]
- **用途**: 低光照条件下的舌象图像增强
- **对应的 Kotlin 类**: `TongueEnhancer`

## 模型训练指南

### 舌体分割模型训练

使用 U-Net 架构，在标注的舌象数据集上训练。建议使用以下数据增强策略：
- 随机旋转（±15度）
- 随机亮度/对比度调整
- 随机水平翻转

损失函数：Dice Loss + Binary Cross-Entropy

### 舌色分类模型训练

使用 MobileNetV3-Small 架构，在已标注的 8 类舌色数据集上训练。
建议使用 ImageNet 预训练权重进行迁移学习。

### 图像增强模型训练

使用 Zero-DCE 架构，在低光照舌象图像数据集上训练。
参考论文：Zero-Reference Deep Curve Estimation for Low-Light Image Enhancement (CVPR 2020)

## 模型量化

为减小模型体积和提升推理速度，建议对模型进行 INT8 量化：

```python
import tensorflow as tf

converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.int8]
converter.representative_dataset = representative_dataset_gen
tflite_quantized_model = converter.convert()
```

## 回退机制

所有 TFLite 模型都是可选的。如果某个模型文件不存在，系统会自动回退到传统算法：
- 无分割模型 → 回退到 HSV 颜色阈值分割
- 无分类模型 → 回退到基于 HSV 统计特征的分类
- 无增强模型 → 回退到 CLAHE 算法增强

## 模型文件大小

建议将模型文件大小控制在以下范围：
- 分割模型：< 30 MB（量化后约 8 MB）
- 分类模型：< 10 MB（量化后约 3 MB）
- 增强模型：< 5 MB（量化后约 1 MB）