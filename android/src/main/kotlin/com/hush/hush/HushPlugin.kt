package com.hush.hush

import android.content.Context
import android.media.AudioAttributes as MediaAudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileOutputStream

@UnstableApi
class HushPlugin : FlutterPlugin, MethodCallHandler {

  companion object {
    private const val TAG = "HushPlugin"
    private const val CHANNEL = "hush"
    private const val STATE_CHANNEL = "hush/state"
    private const val POSITION_CHANNEL = "hush/position"
  }

  private lateinit var channel: MethodChannel
  private lateinit var stateEventChannel: EventChannel
  private lateinit var positionEventChannel: EventChannel

  private var context: Context? = null
  private var audioManager: AudioManager? = null
  private var player: ExoPlayer? = null
  private var audioFocusRequest: AudioFocusRequest? = null
  private var originalAudioMode: Int = AudioManager.MODE_NORMAL
  private var isSecureModeActive: Boolean = false
  private var flutterAssets: FlutterPlugin.FlutterAssets? = null

  private var stateEventSink: EventChannel.EventSink? = null
  private var positionEventSink: EventChannel.EventSink? = null

  private val handler = Handler(Looper.getMainLooper())
  private var positionUpdateRunnable: Runnable? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    flutterAssets = flutterPluginBinding.flutterAssets
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL)
    channel.setMethodCallHandler(this)
    stateEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, STATE_CHANNEL)
    stateEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { stateEventSink = events }
      override fun onCancel(arguments: Any?) { stateEventSink = null }
    })
    positionEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, POSITION_CHANNEL)
    positionEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { positionEventSink = events }
      override fun onCancel(arguments: Any?) { positionEventSink = null }
    })
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    stateEventChannel.setStreamHandler(null)
    positionEventChannel.setStreamHandler(null)
    context = null
    flutterAssets = null
    dispose()
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    try {
      when (call.method) {
        "isSupported" -> result.success(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        "getAndroidVersion" -> result.success(Build.VERSION.SDK_INT)
        "initialize" -> initialize(result)
        "load" -> {
          val sourceMap = call.arguments as? Map<String, Any>
          if (sourceMap != null) {
            load(sourceMap, result)
          } else {
            result.error("INVALID_ARGUMENTS", "Audio source map is required", null)
          }
        }
        "play" -> play(result)
        "pause" -> pause(result)
        "stop" -> stop(result)
        "seek" -> {
          val position = call.argument<Long>("position")
          if (position != null) {
            seek(position, result)
          } else {
            result.error("INVALID_ARGUMENTS", "Position is required", null)
          }
        }
        "setVolume" -> {
          val volume = call.argument<Double>("volume")
          if (volume != null) {
            setVolume(volume.toFloat(), result)
          } else {
            result.error("INVALID_ARGUMENTS", "Volume is required", null)
          }
        }
        "getPosition" -> getPosition(result)
        "getDuration" -> getDuration(result)
        "getState" -> getState(result)
        "dispose" -> {
          dispose()
          result.success(null)
        }
        "isSecureModeActive" -> result.success(isSecureModeActive)
        "getCurrentDevice" -> getCurrentDevice(result)
        else -> result.notImplemented()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling method call: ${call.method}", e)
      result.error("PLUGIN_ERROR", e.message, e.toString())
    }
  }

  private fun initialize(result: Result) {
    context?.let { ctx ->
      audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      audioManager?.let { am ->
        originalAudioMode = am.mode
        result.success(null)
      } ?: result.error("INITIALIZATION_ERROR", "Could not get AudioManager", null)
    } ?: result.error("INITIALIZATION_ERROR", "Context is null", null)
  }

  private fun activateSecureMode() {
    if (isSecureModeActive) return
    audioManager?.let { am ->
      originalAudioMode = am.mode
      am.mode = AudioManager.MODE_IN_COMMUNICATION

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val devices = am.availableCommunicationDevices
        val preferredDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
          ?: devices.find { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
          ?: devices.find { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
          ?: devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
          ?: devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        preferredDevice?.let { am.setCommunicationDevice(it) }
      } else {
        @Suppress("DEPRECATION")
        if (!am.isWiredHeadsetOn && !am.isBluetoothA2dpOn && !am.isBluetoothScoOn) {
          am.isSpeakerphoneOn = true
        } else {
          am.isSpeakerphoneOn = false
        }
      }
      isSecureModeActive = true
    }
  }

  private fun deactivateSecureMode() {
    if (!isSecureModeActive) return
    audioManager?.let { am ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        am.clearCommunicationDevice()
      } else {
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
      }
      am.mode = originalAudioMode
      isSecureModeActive = false
    }
  }

  private fun getCurrentDevice(result: Result) {
    audioManager?.let { am ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val device = am.communicationDevice
        if (device != null) {
          result.success(mapOf("name" to device.productName, "type" to device.type))
        } else {
          result.success(null)
        }
      } else {
        val deviceName: String
        val deviceType: Int
        when {
          @Suppress("DEPRECATION")
          am.isBluetoothA2dpOn || am.isBluetoothScoOn -> {
            deviceName = "Bluetooth Device"
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
          }
          @Suppress("DEPRECATION")
          am.isWiredHeadsetOn -> {
            deviceName = "Wired Headset"
            deviceType = AudioDeviceInfo.TYPE_WIRED_HEADSET
          }
          @Suppress("DEPRECATION")
          am.isSpeakerphoneOn -> {
            deviceName = "Speakerphone"
            deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
          }
          else -> {
            deviceName = "Earpiece"
            deviceType = AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
          }
        }
        result.success(mapOf("name" to deviceName, "type" to deviceType))
      }
    } ?: result.error("NOT_INITIALIZED", "AudioManager is not available.", null)
  }

  private fun load(sourceMap: Map<String, Any>, result: Result) {
    context?.let { ctx ->
      val type = sourceMap["type"] as? String ?: return result.error("INVALID_SOURCE", "Source type is required", null)
      val mediaItem = when (type) {
        "file" -> {
          val path = sourceMap["path"] as? String ?: return result.error("INVALID_SOURCE", "File path is required", null)
          MediaItem.fromUri("file://$path")
        }
        "asset" -> {
          val path = sourceMap["path"] as? String ?: return result.error("INVALID_SOURCE", "Asset path is required", null)
          val assetKey = flutterAssets?.getAssetFilePathByName(path) ?: return result.error("ASSET_NOT_FOUND", "Asset '$path' not found", null)
          MediaItem.fromUri("asset:///$assetKey")
        }
        "url" -> {
          val url = sourceMap["url"] as? String ?: return result.error("INVALID_SOURCE", "URL is required", null)
          MediaItem.fromUri(url)
        }
        "bytes" -> {
          val bytes = sourceMap["bytes"] as? ByteArray ?: return result.error("INVALID_SOURCE", "Bytes array is required", null)
          val tempFile = File.createTempFile("hush_audio_", ".tmp", ctx.cacheDir)
          FileOutputStream(tempFile).use { it.write(bytes) }
          MediaItem.fromUri("file://${tempFile.absolutePath}")
        }
        else -> return result.error("UNSUPPORTED_SOURCE", "Source type '$type' is not supported", null)
      }
      setupPlayer(ctx)
      player?.setMediaItem(mediaItem)
      player?.prepare()
      sendStateUpdate("loading")
      result.success(null)
    } ?: result.error("LOAD_ERROR", "Context is null", null)
  }

  private fun setupPlayer(context: Context) {
    if (player != null) return
    requestAudioFocus()
    activateSecureMode()
    player = ExoPlayer.Builder(context).build().apply {
      val secureAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_VOICE_COMMUNICATION)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
        .build()
      setAudioAttributes(secureAudioAttributes, false)
      setHandleAudioBecomingNoisy(true)
      volume = 1.0f
      addListener(playerListener)
    }
  }

  private fun requestAudioFocus() {
    audioManager?.let { am ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val voiceAttributes = MediaAudioAttributes.Builder()
          .setUsage(MediaAudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(MediaAudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(voiceAttributes)
          .setAcceptsDelayedFocusGain(true)
          .setOnAudioFocusChangeListener(audioFocusChangeListener)
          .build()
        am.requestAudioFocus(audioFocusRequest!!)
      } else {
        @Suppress("DEPRECATION")
        am.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
      }
    }
  }

  private fun releaseAudioFocus() {
    audioManager?.let { am ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
      } else {
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(audioFocusChangeListener)
      }
    }
  }

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        player?.pause()
        sendStateUpdate("paused")
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.2f
      AudioManager.AUDIOFOCUS_GAIN -> player?.volume = 1f
    }
  }

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (isPlaying) {
        startPositionUpdates()
        sendStateUpdate("playing")
      } else {
        stopPositionUpdates()
        if (player?.playbackState != Player.STATE_ENDED) {
          sendStateUpdate("paused")
        }
      }
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
      when (playbackState) {
        Player.STATE_IDLE -> sendStateUpdate("idle")
        Player.STATE_BUFFERING -> sendStateUpdate("loading")
        Player.STATE_READY -> sendStateUpdate(if (player?.isPlaying == true) "playing" else "paused")
        Player.STATE_ENDED -> sendStateUpdate("completed")
      }
    }
    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "Player error: ${error.message}", error)
      sendStateUpdate("error")
    }
  }

  private fun play(result: Result) {
    player?.play()
    result.success(null)
  }
  private fun pause(result: Result) {
    player?.pause()
    result.success(null)
  }
  private fun stop(result: Result) {
    player?.stop()
    stopPositionUpdates()
    sendStateUpdate("idle")
    result.success(null)
  }
  private fun seek(position: Long, result: Result) {
    player?.seekTo(position)
    result.success(null)
  }
  private fun setVolume(volume: Float, result: Result) {
    player?.volume = volume
    result.success(null)
  }
  private fun getPosition(result: Result) { result.success((player?.currentPosition ?: 0).toInt()) }
  private fun getDuration(result: Result) {
    val duration = player?.duration ?: 0
    result.success(if (duration == C.TIME_UNSET) 0 else duration.toInt())
  }
  private fun getState(result: Result) {
    val state = when {
      player == null -> "idle"
      player!!.isPlaying -> "playing"
      player!!.playbackState == Player.STATE_ENDED -> "completed"
      player!!.playbackState == Player.STATE_BUFFERING -> "loading"
      else -> "paused"
    }
    result.success(state)
  }
  private fun dispose() {
    stopPositionUpdates()
    player?.release()
    player = null
    releaseAudioFocus()
    deactivateSecureMode()
  }
  private fun startPositionUpdates() {
    stopPositionUpdates()
    positionUpdateRunnable = object : Runnable {
      override fun run() {
        player?.let { p -> positionEventSink?.success(p.currentPosition.toInt()) }
        handler.postDelayed(this, 1000)
      }
    }
    positionUpdateRunnable?.let { handler.post(it) }
  }
  private fun stopPositionUpdates() {
    positionUpdateRunnable?.let { handler.removeCallbacks(it) }
    positionUpdateRunnable = null
  }
  private fun sendStateUpdate(state: String) { stateEventSink?.success(state) }
}