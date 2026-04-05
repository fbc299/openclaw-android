# OpenClaw Android

OpenClaw AI Gateway Client for Android - Connect to your FeiNiu NAS running OpenClaw Gateway

## Features

- **Connect to FeiNiu NAS** - Direct connection to your local OpenClaw Gateway
- **Model Management** - Quick add and switch between AI models
- **Session Management** - Manage multiple conversation sessions
- **Token Monitoring** - Track token usage by model and session
- **IPv6 Support** - Access your NAS remotely via DDNS (fbc299.xyz)

## Architecture

This is a **client-only** application that connects to your existing OpenClaw Gateway running on your FeiNiu NAS. 

- ✅ **No Node.js runtime required** on Android
- ✅ **No complex installation** - Just install the APK and connect
- ✅ **Battery friendly** - All AI processing happens on your NAS
- ✅ **Secure** - Direct connection to your local network

## Setup

1. Ensure OpenClaw Gateway is running on your FeiNiu NAS (port 19079)
2. Install this APK on your Android device
3. Open the app and connect to your NAS IP (default: 192.168.1.10)
4. Start using your AI assistant!

## Build

```bash
flutter pub get
flutter build apk --release
```

## Requirements

- Flutter >=3.24.0
- Android 8.0+
- Network access to your FeiNiu NAS

## License

MIT