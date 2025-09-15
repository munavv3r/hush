# hush

A Flutter plugin that prevents audio from being captured during screen recording on Android devices.

## Why This Plugin?

Its nearly impossible to detect if the user is recording their screen on android, even if you use FLAG_SECURE, it only protects the screen, the audio can be recorded.
even though some devices prevents the audio from being recorded, thats not the case for majority of devices.

This plugin leverages Android's native audio security model by routing audio through the voice communication stream. The system treats this stream as a private phone call, effectively preventing any app—including system-level screen recorders—from capturing the audio output.

Basically it tricks android into thinking the audio is coming from a call

## Features

- **Complete Audio Protection** - Prevents ALL screen recording apps from capturing audio
- **Voice Communication Mode** - Uses Android's secure voice call audio path
- **System-Level Protection** - Works against built-in screen recorders
- **Easy Integration** - Simple API for developers
- **Real-time Monitoring** - State and position streaming
- **Flexible Audio Sources** - Files, assets, URLs, and byte arrays
- **Performance Optimized** - Minimal battery and CPU impact

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
   hush: ^0.0.1
```

## Note on File Permissions:

When using HushSource.file() on Android 12 (API 32) or lower to access files outside 
of your app's private directory, you must request the READ_EXTERNAL_STORAGE permission 
at runtime. The plugin includes the necessary declaration in its manifest.

## Important Notes

### Use Cases
This plugin is designed for **sensitive, private audio content**:
- ✅ Voice messages and voice notes
- ✅ Confidential recordings
- ✅ Private audio content
- ✅ One-time audio messages
- ❌ Music streaming (quality may be affected)
- ❌ Background audio playback

### Audio Quality
- Audio is processed through Android's voice communication pipeline
- Quality is optimized for speech, not music
- Perfect for voice content, acceptable for most audio

### Platform Support
- **Android**: Full support (API 21+)
- **iOS**: Not needed - iOS already prevents audio capture during screen recording

## Quick Start

### 1. Initialize the Plugin

```dart
import 'package:hush/hush.dart';

class MyAudioWidget extends StatefulWidget {
   @override
   _MyAudioWidgetState createState() => _MyAudioWidgetState();
}

class _MyAudioWidgetState extends State<MyAudioWidget> {
   @override
   void initState() {
      super.initState();
      _initializeSecureAudio();
   }

   Future<void> _initializeSecureAudio() async {
      try {
         // Check if device supports secure audio
         final isSupported = await Hush.isSupported();
         if (!isSupported) {
            print('Secure audio not supported on this device');
            return;
         }

         // Initialize the plugin
         await Hush.initialize();
         print('Secure audio initialized');

      } catch (e) {
         print('Error initializing secure audio: $e');
      }
   }

   @override
   void dispose() {
      Hush.dispose();
      super.dispose();
   }
}
```

### 2. Play Secure Audio

```dart
Future<void> _playSecureAudio() async {
   try {
      // Load from different sources
      final source = HushSource.file('/path/to/voice_message.mp3');
      // OR: HushSource.asset('assets/audio/secret.mp3');
      // OR: HushSource.url('https://example.com/audio.mp3');
      // OR: HushSource.bytes(audioByteArray);

      await Hush.load(source);
      await Hush.play();

   } catch (e) {
      print('Error playing secure audio: $e');
   }
}
```

### 3. Listen to Audio Events

```dart
void _setupAudioListeners() {
   // Listen to playback state changes
   Hush.onStateChanged.listen((state) {
      switch (state) {
         case HushState.idle:
            print('Player is idle');
            break;
         case HushState.loading:
            print('Loading audio...');
            break;
         case HushState.playing:
            print('Audio is playing securely');
            break;
         case HushState.paused:
            print('Audio paused');
            break;
         case HushState.completed:
            print('Audio finished');
            break;
         case HushState.error:
            print('Audio error occurred');
            break;
      }
   });

   // Listen to position updates
   Hush.onPositionChanged.listen((position) {
      print('Current position: ${position.inSeconds}s');
   });
}
```

## Complete Example

*Note: The following is a self-contained example widget. For a full demonstration, see the `main.dart` file in the `/example` folder.*

```dart
import 'package:flutter/material.dart';
import 'package:hush/hush.dart';

class SecureVoicePlayer extends StatefulWidget {
   final String voiceMessagePath;

   const SecureVoicePlayer({Key? key, required this.voiceMessagePath}) : super(key: key);

   @override
   _SecureVoicePlayerState createState() => _SecureVoicePlayerState();
}

class _SecureVoicePlayerState extends State<SecureVoicePlayer> {
   HushState _state = HushState.idle;
   Duration _position = Duration.zero;
   Duration _duration = Duration.zero;
   bool _isInitialized = false;

   @override
   void initState() {
      super.initState();
      _initializeAndLoad();
      _setupListeners();
   }

   Future<void> _initializeAndLoad() async {
      try {
         if (!await Hush.isSupported()) {
            _showError('Device does not support secure audio');
            return;
         }

         await Hush.initialize();
         final source = HushSource.file(widget.voiceMessagePath);
         await Hush.load(source);

         _duration = await Hush.getDuration();
         setState(() {
            _isInitialized = true;
         });

      } catch (e) {
         _showError('Failed to load voice message: $e');
      }
   }

   void _setupListeners() {
      Hush.onStateChanged.listen((state) {
         setState(() => _state = state);
      });

      Hush.onPositionChanged.listen((position) {
         setState(() => _position = position);
      });
   }

   Future<void> _togglePlayback() async {
      try {
         if (_state == HushState.playing) {
            await Hush.pause();
         } else {
            await Hush.play();
         }
      } catch (e) {
         _showError('Playback error: $e');
      }
   }

   void _showError(String message) {
      ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text(message), backgroundColor: Colors.red),
      );
   }

   @override
   void dispose() {
      Hush.dispose();
      super.dispose();
   }

   @override
   Widget build(BuildContext context) {
      if (!_isInitialized) {
         return const Center(child: CircularProgressIndicator());
      }

      return Card(
         child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
               mainAxisSize: MainAxisSize.min,
               children: [
                  Row(
                     children: [
                        Icon(Icons.security, color: Colors.red),
                        SizedBox(width: 8),
                        Text('🔒 Secure Voice Message',
                                style: TextStyle(fontWeight: FontWeight.bold)),
                     ],
                  ),
                  SizedBox(height: 16),

                  // Progress bar
                  LinearProgressIndicator(
                     value: _duration.inMilliseconds > 0
                             ? _position.inMilliseconds / _duration.inMilliseconds
                             : 0.0,
                  ),

                  SizedBox(height: 8),

                  // Time display
                  Row(
                     mainAxisAlignment: MainAxisAlignment.spaceBetween,
                     children: [
                        Text(_formatDuration(_position)),
                        Text(_formatDuration(_duration)),
                     ],
                  ),

                  SizedBox(height: 16),

                  // Play/Pause button
                  ElevatedButton.icon(
                     onPressed: _togglePlayback,
                     icon: Icon(_state == HushState.playing
                             ? Icons.pause
                             : Icons.play_arrow),
                     label: Text(_state == HushState.playing
                             ? 'Pause'
                             : 'Play Securely'),
                     style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red,
                        foregroundColor: Colors.white,
                     ),
                  ),

                  SizedBox(height: 8),

                  Text(
                     '🛡️ This audio cannot be screen recorded',
                     style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                        fontStyle: FontStyle.italic,
                     ),
                  ),
               ],
            ),
         ),
      );
   }

   String _formatDuration(Duration duration) {
      String twoDigits(int n) => n.toString().padLeft(2, '0');
      final minutes = twoDigits(duration.inMinutes.remainder(60));
      final seconds = twoDigits(duration.inSeconds.remainder(60));
      return '$minutes:$seconds';
   }
}
```

## API Reference

### Core Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `isSupported()` | Check if device supports secure audio | `Future<bool>` |
| `getAndroidVersion()` | Get the native Android SDK integer version | `Future<int>` |
| `initialize()` | Initialize the secure audio system | `Future<void>` |
| `load(HushSource)` | Load audio from source | `Future<void>` |
| `play()` | Start secure playback | `Future<void>` |
| `pause()` | Pause playback | `Future<void>` |
| `stop()` | Stop and reset playback | `Future<void>` |
| `seek(Duration)` | Seek to position | `Future<void>` |
| `setVolume(double)` | Set volume (0.0-1.0) | `Future<void>` |
| `getDuration()` | Get the total duration of the loaded audio | `Future<Duration>` |
| `getPosition()` | Get the current playback position | `Future<Duration>` |
| `getState()` | 	Get the current player state | `Future<HushState>` |
| `isSecureModeActive()` | Check if the secure audio mode is currently active | `Future<bool>` |
| `dispose()` | Clean up resources | `Future<void>` |

### Audio Sources

```dart
// From file path
HushSource.file('/path/to/audio.mp3')

// From app assets  
HushSource.asset('assets/audio/voice.mp3')

// From URL
HushSource.url('https://example.com/audio.mp3')

// From byte array
HushSource.bytes(Uint8List audioData)
```

### State Monitoring

```dart
// Listen to state changes
Hush.onStateChanged.listen((HushState state) {
// Handle state: idle, loading, playing, paused, completed, error
});

// Listen to position updates (every second)
Hush.onPositionChanged.listen((Duration position) {
// Update UI with current position
});
```

## Error Handling

```dart
try {
await Hush.play();
} on HushException catch (e) {
switch (e.code) {
case 'PLATFORM_NOT_SUPPORTED':
_showError('This feature requires Android');
break;
case 'PLAYER_ERROR':
_showError('Audio player error: ${e.message}');
break;
default:
_showError('Error: ${e.message}');
}
} catch (e) {
_showError('Unexpected error: $e');
}
```

## Testing the Protection

1. Load and play audio using this plugin
2. Start screen recording on your Android device
3. Notice that the audio is completely silent in the recording
4. The protection works even with:
   - Built-in screen recorders
   - Third-party recording apps
   - Root-level recording tools
   - System-level capture utilities that respect Android's audio policies

## How It Works

This plugin exploits Android's audio security model by:

1. **Voice Communication Mode**: Sets `AudioManager.MODE_IN_COMMUNICATION`
2. **Secure Audio Attributes**: Uses `USAGE_VOICE_COMMUNICATION` with `ALLOW_CAPTURE_BY_NONE`
3. **Audio Focus Management**: Requests `AUDIOFOCUS_GAIN` for voice calls
4. **System Protection**: Leverages Android's built-in call privacy protections

Android treats voice calls as private and prevents any recording - this plugin tricks the system into thinking your audio is a phone call!

## Device Compatibility

### Fully Supported
- Android 5.0+ (API 21+)
- All major OEMs (Samsung, Google, OnePlus, Xiaomi, etc.)
- Custom ROMs based on AOSP

###  Limitations
- iOS: Not needed (built-in protection)
- Android < 5.0: Not supported
- Some heavily modified ROMs may behave differently

## Acknowledgments

- Thanks to the Android audio team for the voice communication security model
- Inspired by the need for real privacy in messaging apps
- Built with love for privacy-first developers

## Troubleshooting

### Common Issues

#### "Secure audio not supported"
- **Cause**: Device running Android < 5.0 or missing audio hardware
- **Solution**: Check `await Hush.isSupported()` before initialization

#### "Failed to initialize secure audio"
- **Cause**: The AudioManager system service might be unavailable on some unusual or non-standard Android builds.
- **Solution**: The plugin requires the MODIFY_AUDIO_SETTINGS permission, which is included automatically.

#### "Player not initialized"
- **Cause**: Trying to play audio before calling `initialize()` and `load()`
- **Solution**: Always call `initialize()` → `load()` → `play()` in sequence

#### Audio quality sounds different
- **Cause**: Voice communication mode applies audio processing (AGC, noise suppression)
- **Solution**: This is expected behavior - the plugin prioritizes security over music quality

#### Conflicts with phone calls
- **Cause**: Incoming call during secure playback
- **Solution**: Plugin automatically handles this - playback pauses during calls

### Debug Information

```dart
// Get detailed device info
Future<void> _debugInfo() async {
   final supported = await Hush.isSupported();
   final androidVersion = await Hush.getAndroidVersion();
   final isActive = await Hush.isSecureModeActive();

   print('Supported: $supported');
   print('Android Version: $androidVersion');
   print('Secure Mode Active: $isActive');
}
```

### Performance Tips

1. **Initialize Once**: Call `initialize()` once per app session
2. **Dispose Properly**: Always call `dispose()` to free resources
3. **Avoid Background Play**: Don't use for background audio - it's not designed for that
4. **Batch Operations**: Load audio before showing UI to avoid loading states

## Advanced Usage

### Custom Error Handling

```dart
class SecureAudioManager {
   static Future<bool> playWithFallback(HushSource source) async {
      try {
         if (!await Hush.isSupported()) {
            // Fallback to regular audio player
            return _playWithRegularPlayer(source);
         }

         await Hush.initialize();
         await Hush.load(source);
         await Hush.play();
         return true;

      } on HushException catch (e) {
         print('Secure audio failed: ${e.code} - ${e.message}');
         return _playWithRegularPlayer(source);
      }
   }

   static bool _playWithRegularPlayer(HushSource source) {
      // Your fallback audio player implementation
      return false;
   }
}
```

### Batch Audio Loading

```dart
class VoiceMessageQueue {
   final List<String> _messageQueue = [];
   int _currentIndex = 0;

   Future<void> loadQueue(List<String> messagePaths) async {
      _messageQueue.clear();
      _messageQueue.addAll(messagePaths);
      _currentIndex = 0;

      if (_messageQueue.isNotEmpty) {
         await _loadCurrent();
      }
   }

   Future<void> playNext() async {
      if (_currentIndex < _messageQueue.length) {
         await Hush.play();
      }
   }

   Future<void> _loadCurrent() async {
      if (_currentIndex < _messageQueue.length) {
         final source = HushSource.file(_messageQueue[_currentIndex]);
         await Hush.load(source);
      }
   }

   Future<void> _onAudioComplete() async {
      _currentIndex++;
      if (_currentIndex < _messageQueue.length) {
         await _loadCurrent();
         // Auto-play next or wait for user input
      }
   }
}
```

### Integration with State Management

```dart
// Using Provider/Riverpod
class SecureAudioNotifier extends ChangeNotifier {
   HushState _state = HushState.idle;
   Duration _position = Duration.zero;
   Duration _duration = Duration.zero;
   String? _error;

   HushState get state => _state;
   Duration get position => _position;
   Duration get duration => _duration;
   String? get error => _error;

   Future<void> initialize() async {
      try {
         await Hush.initialize();
         _setupListeners();
      } catch (e) {
         _error = e.toString();
         notifyListeners();
      }
   }

   void _setupListeners() {
      Hush.onStateChanged.listen((state) {
         _state = state;
         notifyListeners();
      });

      Hush.onPositionChanged.listen((position) {
         _position = position;
         notifyListeners();
      });
   }

   Future<void> loadAndPlay(String filePath) async {
      try {
         _error = null;
         final source = HushSource.file(filePath);
         await Hush.load(source);
         _duration = await Hush.getDuration();
         await Hush.play();
         notifyListeners();
      } catch (e) {
         _error = e.toString();
         notifyListeners();
      }
   }
}
```

## Security Considerations

### What This Plugin Protects Against
- ✅ Screen recording apps (all known variants)
- ✅ Built-in device screen recorders
- ✅ Third-party recording software
- ✅ System-level recording utilities that respect Android's audio policies

### What This Plugin Cannot Protect Against
- ❌ Physical microphone recording
- ❌ Hardware-level audio interception
- ❌ Custom ROMs or root-level modifications that intentionally bypass Android's audio policies

### Best Practices
1. **Use for sensitive content only** - Don't overuse for regular audio
2. **Inform users** - Let users know their audio is protected
3. **Test thoroughly** - Always test on your target devices
4. **Have fallbacks** - Handle cases where secure mode isn't available
5. **Clean up properly** - Always dispose resources to avoid conflicts

## Real-World Use Cases

### 1. Secure Messaging Apps
```dart
// Perfect for "disappearing" voice messages
class DisappearingVoiceMessage extends StatelessWidget {
   final String audioPath;
   final VoidCallback onComplete;

// Play once and auto-delete
}
```

### 2. Confidential Business Audio
```dart
// For sensitive business communications
class ConfidentialAudioPlayer extends StatelessWidget {
   final String meetingRecording;
   final List<String> authorizedUsers;

// Only play for authorized users
}
```

### 3. Medical/Legal Audio
```dart
// For HIPAA/privacy compliant audio
class ComplianceAudioPlayer extends StatelessWidget {
   final String patientAudio;
   final bool requiresAuthentication;

// Secure patient audio playback
}
```

### 4. Educational Content Protection
```dart
// For protecting premium educational audio
class ProtectedLessonAudio extends StatelessWidget {
   final String lessonAudio;
   final bool isPremiumContent;

// Prevent piracy of audio lessons
}
```

## License

MIT License - see LICENSE file for details.