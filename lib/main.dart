import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'providers/setup_provider.dart';
import 'providers/gateway_provider.dart';
import 'screens/splash_screen.dart';
import 'screens/setup_wizard_screen.dart';
import 'screens/onboarding_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/terminal_screen.dart';
import 'screens/web_dashboard_screen.dart';
import 'services/bootstrap_service.dart';
import 'services/gateway_service.dart';
import 'services/preferences_service.dart';

void main() {
  runApp(const OpenClawApp());
}

class OpenClawApp extends StatelessWidget {
  const OpenClawApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Create singletons that must be shared across the widget tree
    final gatewayService = GatewayService();
    final bootstrapService = BootstrapService();

    return MultiProvider(
      providers: [
        // SetupProvider + BootstrapService share the installation flow
        Provider<BootstrapService>.value(value: bootstrapService),
        ChangeNotifierProvider(
          create: (_) => SetupProvider(),
        ),

        // GatewayService (single instance) is shared between GatewayProvider + screens
        Provider<GatewayService>.value(value: gatewayService),
        ChangeNotifierProvider(
          create: (ctx) {
            final provider = GatewayProvider();
            // Use read() since we're in create — the service is already provided above
            provider.setService(ctx.read<GatewayService>());
            return provider;
          },
        ),

        // Preferences for persistent settings
        Provider(create: (_) => PreferencesService()),
      ],
      child: MaterialApp(
        title: _appTitle,
        debugShowCheckedModeBanner: false,
        themeMode: ThemeMode.dark,
        darkTheme: _buildDarkTheme(),
        theme: _buildDarkTheme(),
        initialRoute: '/',
        routes: {
          '/': (context) => const SplashScreen(),
          '/setup': (context) => const SetupWizardScreen(),
          '/onboarding': (context) => const OnboardingScreen(),
          '/dashboard': (context) => const DashboardScreen(),
          '/terminal': (context) => const TerminalScreen(),
          '/web': (context) => const WebDashboardScreen(),
        },
      ),
    );
  }

  ThemeData _buildDarkTheme() {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorSchemeSeed: Colors.orange,
      appBarTheme: const AppBarTheme(
        centerTitle: true,
        elevation: 0,
      ),
      cardTheme: CardTheme(
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      snackBarTheme: const SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}

// Extracted from AppConstants to avoid circular import in main
const _appTitle = 'OpenClaw Android';
