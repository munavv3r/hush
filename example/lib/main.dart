import 'package:flutter/material.dart';
import 'package:hush/hush.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Hush Demo',
      theme: ThemeData(
        primarySwatch: Colors.deepPurple,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: const HushDemoPage(),
    );
  }
}

class HushDemoPage extends StatefulWidget {
  const HushDemoPage({super.key});

  @override
  State<HushDemoPage> createState() => _HushDemoPageState();
}

class _HushDemoPageState extends State<HushDemoPage> {
  HushState _playerState = HushState.idle;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  bool _isSupported = false;
  String _statusMessage = 'Initializing...';

  @override
  void initState() {
    super.initState();
    _initializePlugin();
    _setupListeners();
  }

  @override
  void dispose() {
    Hush.dispose();
    super.dispose();
  }

  Future<void> _initializePlugin() async {
    try {
      final supported = await Hush.isSupported();
      setState(() => _isSupported = supported);

      if (!supported) {
        setState(() => _statusMessage = 'Hush is not supported on this device.');
        return;
      }
      await Hush.initialize();
      setState(() => _statusMessage = 'Plugin initialized. Load an audio file.');
    } catch (e) {
      setState(() => _statusMessage = 'Error: ${e.toString()}');
    }
  }

  void _setupListeners() {
    Hush.onStateChanged.listen((state) {
      setState(() => _playerState = state);
    });
    Hush.onPositionChanged.listen((position) {
      setState(() => _position = position);
    });
  }

  Future<void> _loadAndPlayAsset() async {
    try {
      setState(() => _statusMessage = 'Loading asset...');
      final source = HushSource.asset('assets/samplelights.opus');
      await Hush.load(source);
      _duration = await Hush.getDuration();
      setState(() => _statusMessage = 'Asset loaded. Playing...');
      await Hush.play();
    } catch (e) {
      setState(() => _statusMessage = 'Error loading asset: ${e.toString()}');
    }
  }

  Future<void> _togglePlayback() async {
    try {
      if (_playerState == HushState.playing) {
        await Hush.pause();
      } else {
        await Hush.play();
      }
    } catch (e) {
      setState(() => _statusMessage = 'Playback error: ${e.toString()}');
    }
  }

  Future<void> _stopPlayback() async {
    try {
      await Hush.stop();
      setState(() => _position = Duration.zero);
    } catch (e) {
      setState(() => _statusMessage = 'Stop error: ${e.toString()}');
    }
  }

  Future<void> _seekTo(double value) async {
    try {
      await Hush.seek(Duration(milliseconds: value.toInt()));
    } catch (e) {
      setState(() => _statusMessage = 'Seek error: ${e.toString()}');
    }
  }

  String _formatDuration(Duration d) {
    final min = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final sec = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$min:$sec';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Hush Demo')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              elevation: 4,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Text('Hush Player', style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 8),
                    Text('Status: ${_playerState.name.toUpperCase()}', style: const TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),
                    if (_duration.inMilliseconds > 0)
                      Column(
                        children: [
                          Slider(
                            value: _position.inMilliseconds.clamp(0, _duration.inMilliseconds).toDouble(),
                            max: _duration.inMilliseconds.toDouble(),
                            onChanged: _playerState != HushState.idle ? _seekTo : null,
                          ),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(_formatDuration(_position)),
                              Text(_formatDuration(_duration)),
                            ],
                          ),
                        ],
                      ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        IconButton(
                          icon: Icon(_playerState == HushState.playing ? Icons.pause_circle_filled : Icons.play_circle_filled),
                          onPressed: _playerState != HushState.idle && _playerState != HushState.loading ? _togglePlayback : null,
                          iconSize: 64,
                          color: Theme.of(context).primaryColor,
                        ),
                        IconButton(
                          icon: const Icon(Icons.stop_circle),
                          onPressed: _playerState != HushState.idle ? _stopPlayback : null,
                          iconSize: 64,
                          color: Colors.grey,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              icon: const Icon(Icons.music_note),
              label: const Text('Load & Play Sample Asset'),
              onPressed: _isSupported ? _loadAndPlayAsset : null,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
            ),
            const SizedBox(height: 24),
            Card(
              color: Colors.red.withOpacity(0.1),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.security, color: Colors.red),
                        SizedBox(width: 8),
                        Text('Security Notice', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.red)),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Try screen recording this app. The audio played via Hush will be silent in the recording!',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.red.shade900),
                    ),
                  ],
                ),
              ),
            ),
            const Spacer(),
            Center(child: Text(_statusMessage, style: const TextStyle(color: Colors.grey))),
          ],
        ),
      ),
    );
  }
}