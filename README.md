# 🦞 OpenClaw Android

将 OpenClaw AI Gateway 装进你的 Android 手机——无需服务器，离线运行。

> Run your personal AI gateway right on your phone. No server required.

---

## 功能特性

- 🔐 **私有部署** — 所有数据在手机本地处理，零云端依赖
- 📲 **独立 APK** — 单文件安装，内置 Node.js 运行时
- 🖥️ **网关服务** — 在后台持续运行 OpenClaw Gateway
- 📊 **状态面板** — 实时查看网关状态、日志和连接信息
- 🚀 **开箱即用** — 首次启动引导式配置向导
- 🔄 **开机自启** — 支持 Gateway 随设备启动
- 📱 **Flutter + 原生混合** — Dart 界面 + Kotlin 进程管理

## 技术架构

```
┌─────────────────────────────────┐
│           Flutter UI            │
│   Dashboard  │  Settings  │  ... │
├─────────────────────────────────┤
│        Provider State Mgmt      │
├─────────────────────────────────┤
│    MethodChannel / EventChannel │
├─────────────────────────────────┤
│         Android Native          │
│  ProcessManager │ GlibcRunner   │
│  GatewayForegroundService       │
├─────────────────────────────────┤
│    Node.js Runtime (bundled)    │
│         OpenClaw Gateway        │
└─────────────────────────────────┘
```

| 层级 | 技术 | 职责 |
|------|------|------|
| **UI** | Flutter (Dart) | 用户界面、状态面板、配置向导 |
| **状态** | Provider | 响应式状态管理 |
| **桥接** | MethodChannel + EventChannel | Dart ↔ Kotlin 通信 |
| **原生** | Kotlin + Android SDK | 进程管理、前台服务、glibc 兼容 |
| **运行时** | Node.js (arm64) | 执行 OpenClaw Gateway |
| **核心** | OpenClaw Gateway (JS) | AI 代理服务 |

## 项目结构

```
openclaw-android/
├── android/                    # Android 原生工程
│   ├── app/src/main/kotlin/    # Kotlin 源码
│   │   ├── GlibcRunner.kt      #   glibc linker 封装
│   │   ├── ProcessManager.kt   #   进程生命周期管理
│   │   ├── BootReceiver.kt     #   开机自启
│   │   ├── MainActivity.kt     #   主 Activity
│   │   └── GatewayForegroundService.kt
│   ├── app/src/main/           #   AndroidManifest, res
│   └── app/build.gradle
├── assets/
│   ├── node/                   # Node.js 运行时 (下载后放入)
│   └── scripts/                # 辅助脚本
├── lib/
│   ├── main.dart               # 应用入口
│   ├── constants.dart           # 常量定义
│   ├── models/                  # 数据模型
│   ├── providers/               # Provider 状态
│   ├── screens/                 # 页面
│   ├── services/                # 业务服务
│   └── widgets/                 # 可复用组件
├── scripts/
│   ├── build-apk.sh            # 一键构建 APK
│   └── download-node.sh        # 下载 Node.js 运行时
├── pubspec.yaml
└── README.md
```

## 快速开始

### 环境要求

| 工具 | 最低版本 |
|------|---------|
| Flutter SDK | 3.24.0+ |
| Dart SDK | 3.4.0+ |
| Android SDK | API 34 |
| JDK | 17+ |
| 目标设备 | Android 8.0+ (arm64) |

### 一键构建

```bash
# 1. 安装 Flutter 依赖
flutter pub get

# 2. 下载 Node.js 运行时 (arm64)
bash scripts/download-node.sh

# 3. 构建 APK
bash scripts/build-apk.sh
```

构建完成后，APK 位于：

```
build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

### 安装到手机

```bash
# 通过 USB
flutter install

# 或手动安装adb
adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

### 构建 AAB (Google Play 上架)

```bash
bash scripts/build-apk.sh --aab
```

### 其他架构

```bash
# 下载 32 位 arm
bash scripts/download-node.sh arm v22.14.0

# 下载 x86_64
bash scripts/download-node.sh x86_64 v22.14.0
```

## 开发指南

### 运行调试版本

```bash
flutter run
```

### 项目命令速查

```bash
flutter pub get          # 安装依赖
flutter analyze          # 静态分析
flutter test             # 运行测试
flutter build apk        # 构建调试 APK
flutter build apk --release  # 构建发布 APK
```

### 架构要点

- **进程输出**: 通过 `EventChannel("com.openclaw.android/process_output")` 实时接收进程 stdout/stderr
- **前台服务**: Gateway 以 `ForegroundService` 运行，保证后台不被杀
- **glibc 兼容**: `GlibcRunner` 自动检测并使用 glibc linker 运行 Node.js
- **状态管理**: 使用 Provider 模式；各页面通过 `GatewayProvider` 和 `SetupProvider` 获取状态

## 已知问题和 TODO

### 已知问题

- [x] 进程流式输出 ✅ (Phase 2 已实现)
- [x] 前台服务保障 ✅ (Phase 2 已实现)
- [x] 开机自启 ✅ (Phase 2 已实现)
- [ ] glibc 运行时在部分定制 ROM 上可能不兼容
- [ ] x86_64 模拟器需单独下载 Node 运行时
- [ ] 部分设备通知栏权限需手动开启

### 路线图

- [ ] **WebView 调试面板** — 直接在 App 内调试 Gateway 控制台
- [ ] **终端模拟** — 在 App 内直接与 Gateway 交互
- [ ] **自动更新** — 检查并下载新版本 APK
- [ ] **深色主题** — 跟随系统或手动切换
- [ ] **多实例** — 支持多个 Gateway 配置
- [ ] **推送通知** — Gateway 异常时推送提醒
- [ ] **Google Play 上架** — 签名 + AAB 流程

## 许可证

MIT License

Copyright (c) 2025 OpenClaw

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
