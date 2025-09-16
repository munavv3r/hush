## 0.0.1

*   Initial release of the Hush plugin.
*   Implemented secure audio playback on Android to prevent screen recording capture.
*   Support for file, asset, URL, and byte array sources.
*   Real-time state and position streaming.

## 0.0.2

*   **FEAT**: Implemented intelligent audio routing. The plugin now automatically prioritizes headphones (wired or Bluetooth) over the speaker for a better user experience.
*   **FEAT**: Added `Hush.getCurrentDevice()` to get information on the active audio output device.
*   **FIX**: Resolved build errors for developers on different NDK versions by setting a compatible NDK directly within the plugin's build configuration. Users no longer need to edit their `build.gradle` file.
*   **FIX**: Improved the reliability of player state updates by cleaning up listener logic to prevent potential race conditions.
*   **REFACTOR**: Updated native Android code to use the modern `setCommunicationDevice` API on Android 12+ while maintaining backward compatibility with the deprecated `isSpeakerphoneOn` for older versions.
*   **DOCS**: Overhauled the README with updated features, a more robust example, and clearer API documentation.