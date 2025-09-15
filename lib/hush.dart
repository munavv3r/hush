import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';

class HushException implements Exception {
  final String message;
  final String? code;

  const HushException(this.message, [this.code]);

  @override
  String toString() => 'HushException: $message${code != null ? ' (Code: $code)' : ''}';
}

enum HushState {
  idle,
  loading,
  playing,
  paused,
  completed,
  error,
}

enum HushSourceType {
  file,
  asset,
  url,
  bytes,
}

class HushSource {
  final HushSourceType type;
  final String? path;
  final String? url;
  final Uint8List? bytes;

  const HushSource._({
    required this.type,
    this.path,
    this.url,
    this.bytes,
  });

  factory HushSource.file(String filePath) {
    return HushSource._(type: HushSourceType.file, path: filePath);
  }

  factory HushSource.asset(String assetPath) {
    return HushSource._(type: HushSourceType.asset, path: assetPath);
  }

  factory HushSource.url(String url) {
    return HushSource._(type: HushSourceType.url, url: url);
  }

  factory HushSource.bytes(Uint8List audioBytes) {
    return HushSource._(type: HushSourceType.bytes, bytes: audioBytes);
  }

  Map<String, dynamic> toMap() {
    switch (type) {
      case HushSourceType.file:
        return {'type': 'file', 'path': path};
      case HushSourceType.asset:
        return {'type': 'asset', 'path': path};
      case HushSourceType.url:
        return {'type': 'url', 'url': url};
      case HushSourceType.bytes:
        return {'type': 'bytes', 'bytes': bytes};
    }
  }
}

class Hush {
  static const MethodChannel _channel = MethodChannel('hush');
  static const EventChannel _stateChannel = EventChannel('hush/state');
  static const EventChannel _positionChannel = EventChannel('hush/position');

  static StreamSubscription<HushState>? _stateSubscription;
  static StreamSubscription<Duration>? _positionSubscription;

  static final StreamController<HushState> _stateController =
  StreamController<HushState>.broadcast();
  static final StreamController<Duration> _positionController =
  StreamController<Duration>.broadcast();

  static Stream<HushState> get onStateChanged => _stateController.stream;
  static Stream<Duration> get onPositionChanged => _positionController.stream;

  static Future<bool> isSupported() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool supported = await _channel.invokeMethod('isSupported');
      return supported;
    } catch (e) {
      return false;
    }
  }

  static Future<int> getAndroidVersion() async {
    if (!Platform.isAndroid) return 0;

    try {
      final int version = await _channel.invokeMethod('getAndroidVersion');
      return version;
    } catch (e) {
      return 0;
    }
  }

  static Future<void> initialize() async {
    if (!Platform.isAndroid) {
      throw const HushException('Hush is only supported on Android', 'PLATFORM_NOT_SUPPORTED');
    }

    try {
      await _channel.invokeMethod('initialize');
      _setupEventListeners();
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to initialize', e.code);
    }
  }

  static Future<void> load(HushSource source) async {
    try {
      await _channel.invokeMethod('load', source.toMap());
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to load audio source', e.code);
    }
  }

  static Future<void> play() async {
    try {
      await _channel.invokeMethod('play');
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to start playback', e.code);
    }
  }

  static Future<void> pause() async {
    try {
      await _channel.invokeMethod('pause');
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to pause playback', e.code);
    }
  }

  static Future<void> stop() async {
    try {
      await _channel.invokeMethod('stop');
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to stop playback', e.code);
    }
  }

  static Future<void> seek(Duration position) async {
    try {
      await _channel.invokeMethod('seek', {'position': position.inMilliseconds});
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to seek', e.code);
    }
  }

  static Future<void> setVolume(double volume) async {
    if (volume < 0.0 || volume > 1.0) {
      throw const HushException('Volume must be between 0.0 and 1.0', 'INVALID_VOLUME');
    }

    try {
      await _channel.invokeMethod('setVolume', {'volume': volume});
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to set volume', e.code);
    }
  }

  static Future<Duration> getPosition() async {
    try {
      final int position = await _channel.invokeMethod('getPosition');
      return Duration(milliseconds: position);
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to get position', e.code);
    }
  }

  static Future<Duration> getDuration() async {
    try {
      final int duration = await _channel.invokeMethod('getDuration');
      return Duration(milliseconds: duration);
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to get duration', e.code);
    }
  }

  static Future<HushState> getState() async {
    try {
      final String state = await _channel.invokeMethod('getState');
      return _parseState(state);
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to get state', e.code);
    }
  }

  static Future<void> dispose() async {
    try {
      await _channel.invokeMethod('dispose');
      await _stateSubscription?.cancel();
      await _positionSubscription?.cancel();
      _stateSubscription = null;
      _positionSubscription = null;
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to dispose', e.code);
    }
  }

  static Future<bool> isSecureModeActive() async {
    try {
      final bool active = await _channel.invokeMethod('isSecureModeActive');
      return active;
    } on PlatformException catch (e) {
      throw HushException(e.message ?? 'Failed to check secure mode', e.code);
    }
  }

  static void _setupEventListeners() {
    _stateSubscription?.cancel();
    _positionSubscription?.cancel();

    _stateSubscription = _stateChannel
        .receiveBroadcastStream()
        .map((dynamic data) => _parseState(data.toString()))
        .listen(
          (state) {
        _stateController.add(state);
      },
      onError: (error) {
        _stateController.addError(HushException('State stream error: $error'));
      },
    );

    _positionSubscription = _positionChannel
        .receiveBroadcastStream()
        .map((dynamic data) => Duration(milliseconds: data as int))
        .listen(
          (position) {
        _positionController.add(position);
      },
      onError: (error) {
        _positionController.addError(HushException('Position stream error: $error'));
      },
    );
  }

  static HushState _parseState(String state) {
    switch (state.toLowerCase()) {
      case 'idle':
        return HushState.idle;
      case 'loading':
        return HushState.loading;
      case 'playing':
        return HushState.playing;
      case 'paused':
        return HushState.paused;
      case 'completed':
        return HushState.completed;
      case 'error':
        return HushState.error;
      default:
        return HushState.idle;
    }
  }
}