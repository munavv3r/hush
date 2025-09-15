import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'hush_method_channel.dart';

abstract class HushPlatform extends PlatformInterface {
  /// Constructs a HushPlatform.
  HushPlatform() : super(token: _token);

  static final Object _token = Object();

  static HushPlatform _instance = MethodChannelHush();

  /// The default instance of [HushPlatform] to use.
  ///
  /// Defaults to [MethodChannelHush].
  static HushPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [HushPlatform] when
  /// they register themselves.
  static set instance(HushPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
