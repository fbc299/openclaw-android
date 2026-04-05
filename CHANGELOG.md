# Changelog

All notable changes to OpenClaw Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Flutter app with Dashboard and Setup Wizard screens
- Native process management via `ProcessManager.kt`
- glibc linker support via `GlibcRunner.kt`
- Foreground Service for persistent Gateway (`GatewayForegroundService.kt`)
- Boot receiver for auto-start on device boot
- MethodChannel + EventChannel bridge for Dart ↔ Kotlin communication
- Streamed process output (stdout/stderr/exit events)
- Provider-based state management (GatewayProvider, SetupProvider)
- Build scripts (`build-apk.sh`, `download-node.sh`)
- WebView dashboard for remote Gateway management
- Terminal screen with TerminalKit integration

### Changed
- Comprehensive rewrite of MainActivity to integrate ProcessManager

## [0.1.0] - 2025-04-05

### Added
- Initial project scaffolding
- pubspec.yaml with dependencies
- Basic screen structure (Splash, Setup Wizard, Onboarding, Dashboard, Terminal, Web Dashboard)
- Service layer (GatewayService, BootstrapService, PreferencesService, NativeBridge)
- Model classes (AIProvider, GatewayState, SetupState)
- Widget components (GatewayControls, ProgressStep, StatusCard)
- Constants and configuration
- Phase 2 native layer implementation (Kotlin)
