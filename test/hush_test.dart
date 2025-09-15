import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hush/hush.dart';

void main() {
  const MethodChannel channel = MethodChannel('hush');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      if (methodCall.method == 'isSupported') {
        return true;
      }
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('isSupported returns true', () async {
    expect(await Hush.isSupported(), true);
  });
}