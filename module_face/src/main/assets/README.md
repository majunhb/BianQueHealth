# MediaPipe Face Landmarker 模型文件

## 模型说明

此目录需要放置 `face_landmarker.task` 模型文件，供 MediaPipe Face Landmarker 使用。

该模型提供 **468 个面部关键点** 检测能力，相比 ML Kit 的约 130 个轮廓点，精度更高，适合中医面诊的精细分区分析。

## 下载方式

### 方式一：直接下载（推荐）

从 Google 官方 MediaPipe Model Zoo 下载：

```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

可以通过以下命令下载：

```bash
# 在 module_face/src/main/assets/ 目录下执行
curl -L -o face_landmarker.task \
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
```

### 方式二：通过 MediaPipe Studio 下载

访问 [MediaPipe Studio - Face Landmarker](https://mediapipe-studio.webapps.google.com/demo/face_landmarker)，下载模型文件。

### 方式三：通过 Python pip 包下载

```bash
pip install mediapipe
wget https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

### 方式四：GitHub Release

访问 [MediaPipe Models Releases](https://github.com/google-ai-edge/mediapipe/releases) 查找最新版本。

## 技术规格

| 属性 | 值 |
|------|-----|
| 模型名称 | face_landmarker.task |
| 关键点数量 | 468 |
| 精度 | float16 |
| 输入图像尺寸 | 任意（自动缩放） |
| 运行模式 | IMAGE（单帧分析） |
| 输出 | 468 个归一化关键点 + 52 种 Blendshapes |

## 注意事项

1. **文件大小**：约 5-8 MB，请确保设备有足够空间
2. **位置要求**：必须放在 `module_face/src/main/assets/` 目录下
3. **运行时加载**：模型文件会随 APK 一起打包，在应用首次使用时加载
4. **优雅降级**：如果模型文件不存在，代码会自动回退到 ML Kit 方案，不会影响应用正常运行
5. **不要重命名**：文件名必须保持为 `face_landmarker.task`

## 验证

下载完成后，确认文件存在：

```bash
ls -la module_face/src/main/assets/face_landmarker.task
```

如果文件存在且大小合理（约 5-8 MB），模型即可正常使用。应用首次启动时会自动检测并加载模型。