# hush

A Flutter plugin that prevents audio from being captured during screen recording on Android devices.

## Why This Plugin?

It's nearly impossible to detect if the user is recording their screen on Android. Even if you use `FLAG_SECURE`, it only protects the screen; the audio can still be recorded. While some devices prevent audio recording by default, that's not the case for the majority of devices.

This plugin leverages Android's native audio security model by routing audio through the voice communication stream. The system treats this stream as a private phone call, effectively preventing any app—including system-level screen recorders—from capturing the audio output.

Basically, it tricks Android into thinking the audio is coming from a call.

## Features

-   **Complete Audio Protection** - Prevents ALL screen recording apps from capturing audio.
-   **Intelligent Audio Routing** - Automatically plays on headphones if connected, otherwise defaults to the speaker.
-   **Voice Communication Mode** - Uses Android's secure voice call audio path.
-   **System-Level Protection** - Works against built-in screen recorders.
-   **Real-time Monitoring** - State and position streaming.
-   **Flexible Audio Sources** - Supports files, assets, URLs, and byte arrays.
-   **Performance Optimized** - Minimal battery and CPU impact.

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
   hush: ^0.0.2
```

## Note on File Permissions:

When using HushSource.file() on Android 12 (API 32) or lower to access files outside
of your app's private directory, you must request the READ_EXTERNAL_STORAGE permission
at runtime. The plugin includes the necessary declaration in its manifest.

## For Native Android (Kotlin) Developers

The core of this plugin is a native Android technique. If you're a native developer, you can apply the same principle to secure your ExoPlayer audio playback.

Instead of a standard media setup, configure your player for voice communication:

```kotlin
// Before: Standard media playback (recordable)
val audioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
exoPlayer.setAudioAttributes(audioAttributes, true)


// After: Secure voice playback (not recordable)
val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

val secureAudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_VOICE_COMMUNICATION)
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE) // The key to preventing capture
    .build()

// Set handleAudioFocus to false as we manage it manually for voice call mode.
exoPlayer.setAudioAttributes(secureAudioAttributes, false)

// Then, intelligently route the audio to headphones or speaker.
// (See the plugin's source code for a full implementation of device selection).
```

## Important Notes

### Use Cases
This plugin is designed for **sensitive, private audio content**:
-   ✅ Voice messages and voice notes
-   ✅ Confidential recordings
-   ✅ Private audio content
-   ✅ One-time audio messages
-   ❌ Music streaming (quality will be affected)
-   ❌ General-purpose background audio playback

### Audio Quality
-   Audio is processed through Android's voice communication pipeline, which may apply effects like automatic gain control or noise suppression.
-   Quality is optimized for speech, not high-fidelity music.
-   Perfect for voice content, acceptable for most other non-music audio.

### Platform Support
-   **Android**: Full support (API 21+)
-   **iOS**: Not needed - iOS already prevents audio capture during screen recording by default.

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
         // Check if the device supports the required Android version
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
         if (mounted) {
            setState(() => _state = state);
         }
      });

      Hush.onPositionChanged.listen((position) {
         if (mounted) {
            setState(() => _position = position);
         }
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
      if (mounted) {
         ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(message), backgroundColor: Colors.red),
         );
      }
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
                  LinearProgressIndicator(
                     value: _duration.inMilliseconds > 0
                             ? _position.inMilliseconds / _duration.inMilliseconds
                             : 0.0,
                  ),
                  SizedBox(height: 8),
                  Row(
                     mainAxisAlignment: MainAxisAlignment.spaceBetween,
                     children: [
                        Text(_formatDuration(_position)),
                        Text(_formatDuration(_duration)),
                     ],
                  ),
                  SizedBox(height: 16),
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
                        color: Colors.grey,
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
| `isSupported()` | Check if the device's Android version is sufficient (API 21+). | `Future<bool>` |
| `getAndroidVersion()` | Get the native Android SDK integer version. | `Future<int>` |
| `initialize()` | Prepare the secure audio system. Must be called before loading. | `Future<void>` |
| `load(HushSource)` | Load audio from a specified source. | `Future<void>` |
| `play()` | Start or resume secure playback. | `Future<void>` |
| `pause()` | Pause the current playback. | `Future<void>` |
| `stop()` | Stop playback and reset the position to the beginning. | `Future<void>` |
| `seek(Duration)` | Seek to a specific position in the audio. | `Future<void>` |
| `setVolume(double)` | Set the player volume (from 0.0 to 1.0). | `Future<void>` |
| `getDuration()` | Get the total duration of the loaded audio. | `Future<Duration>` |
| `getPosition()` | Get the current playback position. | `Future<Duration>` |
| `getState()` | Get the current player state (`HushState`). | `Future<HushState>` |
| `isSecureModeActive()` | Check if the secure audio mode is currently active. | `Future<bool>` |
| `getCurrentDevice()` | Get information about the current audio output device. | `Future<Map<String, dynamic>?>` |
| `dispose()` | Clean up all native resources. Call this when done. | `Future<void>` |

### Audio Sources

```dart
// From a file path
HushSource.file('/path/to/audio.mp3')

// From app assets declared in pubspec.yaml
HushSource.asset('assets/audio/voice.mp3')

// From a network URL
HushSource.url('https://example.com/audio.mp3')

// From a raw byte array
HushSource.bytes(Uint8List audioData)
```

### State Monitoring

```dart
// Listen to state changes
Hush.onStateChanged.listen((HushState state) {
// Handle state: idle, loading, playing, paused, completed, error
});

// Listen to position updates (streams roughly every second during playback)
Hush.onPositionChanged.listen((Duration position) {
// Update UI with the current position
});
```

## Security & How It Works

This plugin exploits Android's audio security model by:

1.  **Setting Voice Communication Mode**: It tells the system to behave as if it's in a phone call by setting `AudioManager.MODE_IN_COMMUNICATION`.
2.  **Using Secure Audio Attributes**: It configures the audio player with `USAGE_VOICE_COMMUNICATION` and, most importantly, `setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE)`. This explicitly forbids other apps from capturing the audio.
3.  **Intelligent Routing**: It detects if headphones (wired or Bluetooth) are connected and directs audio to them. If not, it uses the main speaker, bypassing the earpiece for a better user experience.
4.  **Leveraging System Protection**: Android inherently protects the voice call stream to ensure user privacy, and this plugin makes your audio a part of that protected system.

## Troubleshooting

### Common Issues

#### Audio quality sounds different or like a phone call.
-   **Cause**: This is expected. The voice communication mode often applies audio processing like automatic gain control (AGC) or noise suppression.
-   **Solution**: This is a trade-off for security. The plugin prioritizes protection over high-fidelity music quality.

#### "Player not initialized" or other errors.
-   **Cause**: Trying to play audio before calling `initialize()` and `load()`.
-   **Solution**: Ensure you always call `initialize()` → `load()` → `play()` in sequence.

#### Conflicts with phone calls.
-   **Cause**: An incoming phone call will interrupt any audio playback.
-   **Solution**: The plugin correctly handles audio focus, automatically pausing playback during a call and allowing it to be resumed after.

## License

MIT License - see the LICENSE file for details.