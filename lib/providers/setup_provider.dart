import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:openclaw_android/models/setup_state.dart';
import 'package:openclaw_android/services/bootstrap_service.dart';

class SetupProvider extends ChangeNotifier {
  SetupState _state = SetupState(steps: BootstrapService.defaultSteps());
  bool _isRunning = false;
  String? _errorMessage;

  SetupState get state => _state;
  bool get isRunning => _isRunning;
  String? get errorMessage => _errorMessage;

  // ignore: unused_field
  BootstrapService? _service;
  StreamSubscription<SetupState>? _subscription;

  void _setState(SetupState state) {
    _state = state;
    notifyListeners();
  }

  /// Run the full setup/bootstrap process.
  Future<void> runSetup({String? dataDir}) async {
    if (_isRunning) return;
    _isRunning = true;
    _errorMessage = null;

    // Reset state
    _state = SetupState(steps: BootstrapService.defaultSteps());
    notifyListeners();

    try {
      _service ??= BootstrapService();

      // Listen to state stream
      _subscription = _service!.stateStream.listen(
        (newState) {
          _setState(newState);
        },
        onError: (error) {
          _errorMessage = error.toString();
          _isRunning = false;
          notifyListeners();
        },
      );

      await _service!.runSetup(dataDir: dataDir);

      _isRunning = false;
      _subscription?.cancel();
      _subscription = null;
    } catch (e) {
      _errorMessage = e.toString();
      _isRunning = false;
      _subscription?.cancel();
      _subscription = null;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _service?.dispose();
    super.dispose();
  }
}
