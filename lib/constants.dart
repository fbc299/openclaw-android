/// Application-wide constants for OpenClaw Android.
class AppConstants {
  AppConstants._();

  static const String appTitle = 'OpenClaw Android';
  static const String appDescription = 'AI Gateway for Android';

  // Gateway
  static const String gatewayHost = '127.0.0.1';
  static const int gatewayPort = 18789;
  static const Duration gatewayHealthCheckInterval = Duration(seconds: 5);
  static const Duration gatewayHealthTimeout = Duration(seconds: 10);

  // Data directory (app private data)
  static const String dataDirName = '.openclaw';

  // Node.js runtime
  static const String nodejsVersion = 'v22.14.0';
  static const String nodejsArch = 'linux-arm64';
  static const String nodejsUrl =
      'https://nodejs.org/dist/v22.14.0/node-v22.14.0-linux-arm64.tar.gz';
  static const String nodejsTarballName = 'node-v22.14.0-linux-arm64.tar.gz';
  static const String nodejsExtractedDir = 'node-v22.14.0-linux-arm64';

  // Bootstrap step names (default)
  static const String stepPrepareDirectory = '准备安装目录';
  static const String stepUnpackRuntime = '解包运行环境';
  static const String stepSetupEnvironment = '配置环境';
  static const String stepInstallOpenClaw = '安装 OpenClaw';
  static const String stepApplyPatches = '应用适配修补';
  static const String stepVerifyInstallation = '验证安装';

  // AI Provider defaults
  static const String defaultAiProvider = 'anthropic';
  static const String defaultAnthropicModel = 'claude-sonnet-4-20250514';
  static const String defaultOpenAiModel = 'gpt-4o';
  static const String defaultGoogleModel = 'gemini-2.5-pro';
  static const String defaultDeepSeekModel = 'deepseek-chat';
  static const String defaultXaiModel = 'grok-3';

  // URLs
  static const String gatewayBaseUrl = 'http://127.0.0.1:18789';
  static const String gatewayDashboardUrl = 'http://127.0.0.1:18789/';

  // Battery optimization
  static const String batteryOptPackageName =
      'com.openclaw.android';
}
