import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:openclaw_android/models/gateway_state.dart';
import 'package:openclaw_android/services/gateway_service.dart';

class GatewayProvider extends ChangeNotifier {
  GatewayState _state = GatewayState.initial();
  bool _isOperating = false;

  GatewayState get state => _state;
  bool get isOperating => _isOperating;

  late GatewayService _service;

  /// Set the shared gateway service (injected from main).
  void setService(GatewayService service) {
    _service = service;
    _service.state.addListener(_onStateChanged);
    _state = _service.state.value;
    notifyListeners();
  }

  void _onStateChanged() {
    _state = _service.state.value;
    _isOperating = false;
    notifyListeners();
  }

  /// Start the gateway.
  Future<void> start() async {
    if (_isOperating || _state.isActive) return;
    _isOperating = true;
    notifyListeners();
    try {
      await _service.start();
    } catch (e) {
      debugPrint('Gateway start error: $e');
    } finally {
      _isOperating = false;
      notifyListeners();
    }
  }

  /// Stop the gateway.
  Future<void> stop() async {
    if (_isOperating || !_state.isActive) return;
    _isOperating = true;
    notifyListeners();
    try {
      await _service.stop();
    } catch (e) {
      debugPrint('Gateway stop error: $e');
    } finally {
      _isOperating = false;
      notifyListeners();
    }
  }

  /// Manually refresh status.
  Future<void> refresh() async {
    await _service.checkStatus();
  }
}
