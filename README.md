# Audio Ducking Controller

An Android application built with Kotlin, Jetpack Compose, and Coroutines that continuously monitors real-time ambient microphone levels (`AudioRecord`) and automatically lowers and restores device media volume (`AudioManager.STREAM_MUSIC`) based on customizable acoustic thresholds.

## Features

1. **Real-Time Microphone Monitoring (`AudioDuckingEngine` & `DecibelCalculator`)**
   - Captures 16-bit PCM audio at a battery-efficient 16 kHz sample rate using `AudioRecord`.
   - Computes Root-Mean-Square (RMS) amplitude and converts it into smoothed decibel (`dB SPL` approximation) readings.
   - Displays a 32-bar live visual spectrum/level bar chart with a dashed threshold indicator line and peak/average telemetry.

2. **5 User Controls & Presets (`DuckingControlsPanel` & `DuckingPreferencesRepository`)**
   - **Master On/Off Toggle**: Starts or stops live microphone monitoring and the background foreground service.
   - **Sensitivity Slider (`10 dB – 80 dB`)**: Sets the ambient sound threshold that triggers ducking.
   - **Volume Reduction Slider (`0% – 100%`)**: Configures how much the media volume is reduced when triggered.
   - **Response Delay Slider (`0.0 s – 5.0 s`)**: Configures how long ambient sound must stay above the threshold before ducking starts.
   - **Recovery Time Slider (`0.5 s – 5.0 s`)**: Configures how long to wait after ambient sound drops below the threshold before restoring volume.
   - **Persistent Storage**: All settings are saved automatically in `SharedPreferences` (`audio_ducking_controller_prefs`) and synchronized immediately with the running service.

3. **Smooth Media Volume Fading (`MediaVolumeController`)**
   - Uses `AudioManager` (`STREAM_MUSIC`) to store the original media volume prior to ducking.
   - Smoothly fades volume down when the threshold hold completes and smoothly fades back up to the stored original volume after recovery.
   - Includes a built-in Reference Audio Stream (`AudioTrack` ambient chord on `STREAM_MUSIC`) so users can test volume ducking immediately without needing an external music player.

4. **Background Foreground Service (`AudioDuckingService`)**
   - Runs as a `microphone` foreground service with a persistent low-priority notification displaying the live dB reading, current status phase, and a quick **Stop Monitoring** action.

## Permissions

- `android.permission.RECORD_AUDIO`: Captures ambient sound amplitude from the microphone.
- `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_MICROPHONE`: Keeps monitoring active while the app is minimized.
- `android.permission.MODIFY_AUDIO_SETTINGS`: Controls and fades `STREAM_MUSIC` media volume.
- `android.permission.POST_NOTIFICATIONS`: Displays the live dB foreground service notification on Android 13+.
