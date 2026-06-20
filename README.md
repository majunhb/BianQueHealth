# 扁鹊健康 (BianQue Health)

> 多模态中医健康检测应用 — 面诊 · 舌诊 · 血压 · 脉诊 + AI 健康建议引擎

## 功能模块

| 模块 | 技术方案 | 状态 |
|------|----------|------|
| 🏥 面诊 | MediaPipe Face Mesh + CIE Lab 色泽分析 | 🚧 MVP |
| 👅 舌诊 | U-Net 舌体分割 + 颜色/形态特征提取 | 🚧 MVP |
| 💓 血压 | BLE 蓝牙设备 + PPG 光学备选 | 🚧 MVP |
| 🫀 脉诊 | 压力传感器阵列 + LSTM 脉象分类 | 📋 第二阶段 |
| 🧠 健康建议 | 九种体质辨识 + 个性化饮食/运动/作息建议 | 🚧 MVP |

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material3
- **架构**: Clean Architecture (domain/data/di)
- **DI**: Hilt
- **数据库**: Room
- **相机**: CameraX
- **AI**: MediaPipe / TensorFlow Lite / ML Kit
- **网络**: Retrofit + OkHttp (可选后端)
- **安全**: Android Keystore + AES/GCM 加密

## 项目结构

```
BianQueHealth/
├── app/                        # 主应用模块
│   └── src/main/java/com/bianque/health/
│       ├── BianQueApp.kt       # Application
│       ├── MainActivity.kt     # 单 Activity
│       ├── di/AppModule.kt     # Hilt DI
│       ├── ui/
│       │   ├── MainScreen.kt   # 主界面
│       │   ├── BianQueNavHost.kt # 导航图
│       │   ├── screens/        # 5 个功能屏幕
│       │   ├── components/     # 可复用组件
│       │   └── theme/          # 主题
│       └── viewmodel/          # ViewModels
├── base/                       # 基础库
│   └── src/main/java/com/bianque/health/base/
│       ├── common/             # Result, BaseModels
│       ├── camera/             # CameraX 辅助
│       ├── data/local/         # Room DB + DAO
│       ├── security/           # 加密 + 隐私
│       └── di/BaseModule.kt
├── module_face/                # 面诊模块
├── module_tongue/              # 舌诊模块
├── module_blood_pressure/       # 血压模块
├── module_pulse/               # 脉诊模块
└── module_health_engine/       # 健康建议引擎
```

## 构建

```bash
./gradlew assembleDebug
```

## 许可证

MIT License