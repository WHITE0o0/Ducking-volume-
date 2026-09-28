package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.data.DuckingPreferencesRepository
import com.example.model.DuckingEvent
import com.example.model.DuckingPhase
import com.example.model.DuckingSettings
import com.example.model.DuckingTelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Coordinates continuous battery-efficient AudioRecord microphone monitoring,
 * decibel smoothing, response delay timing, media volume ducking, and recovery timing.
 */
class AudioDuckingEngine(
    private val appContext: Context,
    val preferencesRepository: DuckingPreferencesRepository,
    val volumeController: MediaVolumeController = MediaVolumeController(appContext)
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null
    private var fadeJob: Job? = null

    val settingsFlow: StateFlow<DuckingSettings> = preferencesRepository.settingsFlow

    private val _telemetryFlow = MutableStateFlow(
        DuckingTelemetryState(
            currentMediaVolume = volumeController.getCurrentMediaVolume(),
            maxMediaVolume = volumeController.getMaxMediaVolume(),
            hasRecordPermission = hasAudioPermission()
        )
    )
    val telemetryFlow: StateFlow<DuckingTelemetryState> = _telemetryFlow.asStateFlow()

    private var eventIdCounter = 1L

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshPermissionAndVolumeState() {
        _telemetryFlow.update { current ->
            current.copy(
                hasRecordPermission = hasAudioPermission(),
                currentMediaVolume = volumeController.getCurrentMediaVolume(),
                maxMediaVolume = volumeController.getMaxMediaVolume(),
                originalMediaVolume = volumeController.originalVolumeBeforeDuck
            )
        }
    }

    fun syncMediaVolumeIfUnducked(newReductionPercent: Int) {
        if (volumeController.isCurrentlyDucked) {
            fadeJob?.cancel()
            fadeJob = engineScope.launch {
                volumeController.fadeDownToReduction(newReductionPercent) { stepVol ->
                    _telemetryFlow.update {
                        it.copy(
                            currentMediaVolume = stepVol,
                            originalMediaVolume = volumeController.originalVolumeBeforeDuck
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (!hasAudioPermission()) {
            preferencesRepository.updateMonitoringEnabled(false)
            _telemetryFlow.update {
                it.copy(
                    hasRecordPermission = false,
                    phase = DuckingPhase.STANDBY,
                    errorMessage = "Microphone permission is required to monitor ambient sound."
                )
            }
            return
        }

        if (monitoringJob?.isActive == true) return
        preferencesRepository.updateMonitoringEnabled(true)

        monitoringJob = engineScope.launch(Dispatchers.IO) {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE_HZ / 5 * 2)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferBytes
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                preferencesRepository.updateMonitoringEnabled(false)
                _telemetryFlow.update {
                    it.copy(
                        phase = DuckingPhase.STANDBY,
                        errorMessage = "Unable to initialize device microphone."
                    )
                }
                return@launch
            }

            val readSamplesCount = 1280 // ~80ms window at 16 kHz for low-power ~12.5 Hz updates
            val sampleBuffer = ShortArray(readSamplesCount)
            var smoothedDb = 25f
            var peakDb = 0f
            var dbSum = 0.0
            var dbFrames = 0L

            var thresholdExceededSinceMs: Long? = null
            var soundDroppedSinceMs: Long? = null

            try {
                recorder.startRecording()
                _telemetryFlow.update {
                    it.copy(
                        phase = DuckingPhase.MONITORING,
                        hasRecordPermission = true,
                        errorMessage = null,
                        currentMediaVolume = volumeController.getCurrentMediaVolume(),
                        maxMediaVolume = volumeController.getMaxMediaVolume()
                    )
                }

                while (isActive) {
                    val readCount = recorder.read(sampleBuffer, 0, readSamplesCount)
                    if (readCount > 0) {
                        val rawDb = DecibelCalculator.calculateDecibels(sampleBuffer, readCount)
                        smoothedDb = DecibelCalculator.smoothDecibels(smoothedDb, rawDb)
                        peakDb = max(peakDb, smoothedDb)
                        dbSum += smoothedDb
                        dbFrames++
                        val avgDb = ((dbSum / dbFrames).toFloat() * 10f).roundToInt() / 10f

                        val settings = settingsFlow.value
                        val nowMs = SystemClock.elapsedRealtime()
                        val isAboveThreshold = smoothedDb >= settings.sensitivityDb

                        var nextPhase = _telemetryFlow.value.phase
                        var holdProgress = 0f
                        var recoveryProgress = 0f

                        if (isAboveThreshold) {
                            soundDroppedSinceMs = null
                            if (!volumeController.isCurrentlyDucked) {
                                val startHold = thresholdExceededSinceMs ?: nowMs.also {
                                    thresholdExceededSinceMs = it
                                }
                                val requiredDelayMs = (settings.responseDelaySeconds * 1000f).toLong()
                                val elapsedHoldMs = nowMs - startHold

                                if (elapsedHoldMs >= requiredDelayMs) {
                                    thresholdExceededSinceMs = null
                                    holdProgress = 1f
                                    nextPhase = DuckingPhase.DUCKING_ACTIVE
                                    triggerVolumeDuck(smoothedDb, settings.volumeReductionPercent)
                                } else {
                                    holdProgress = if (requiredDelayMs > 0L) {
                                        (elapsedHoldMs.toFloat() / requiredDelayMs.toFloat()).coerceIn(0f, 1f)
                                    } else 1f
                                    nextPhase = DuckingPhase.THRESHOLD_HOLD
                                }
                            } else {
                                nextPhase = DuckingPhase.DUCKING_ACTIVE
                            }
                        } else {
                            thresholdExceededSinceMs = null
                            if (volumeController.isCurrentlyDucked) {
                                val startRecovery = soundDroppedSinceMs ?: nowMs.also {
                                    soundDroppedSinceMs = it
                                }
                                val requiredRecoveryMs =
                                    (settings.recoveryTimeSeconds * 1000f).toLong().coerceAtLeast(500L)
                                val elapsedRecoveryMs = nowMs - startRecovery

                                if (elapsedRecoveryMs >= requiredRecoveryMs) {
                                    soundDroppedSinceMs = null
                                    recoveryProgress = 1f
                                    nextPhase = DuckingPhase.MONITORING
                                    triggerVolumeRestore(smoothedDb)
                                } else {
                                    recoveryProgress =
                                        (elapsedRecoveryMs.toFloat() / requiredRecoveryMs.toFloat())
                                            .coerceIn(0f, 1f)
                                    nextPhase = DuckingPhase.RECOVERING
                                }
                            } else {
                                nextPhase = DuckingPhase.MONITORING
                            }
                        }

                        _telemetryFlow.update { state ->
                            val updatedBars = (state.barHistory.drop(1) + smoothedDb)
                            state.copy(
                                currentDb = smoothedDb,
                                peakDb = peakDb,
                                averageDb = avgDb,
                                barHistory = updatedBars,
                                phase = nextPhase,
                                holdProgress = holdProgress,
                                recoveryProgress = recoveryProgress,
                                currentMediaVolume = volumeController.getCurrentMediaVolume(),
                                maxMediaVolume = volumeController.getMaxMediaVolume(),
                                originalMediaVolume = volumeController.originalVolumeBeforeDuck
                            )
                        }
                    }
                    delay(20L)
                }
            } catch (e: SecurityException) {
                preferencesRepository.updateMonitoringEnabled(false)
                _telemetryFlow.update {
                    it.copy(
                        phase = DuckingPhase.STANDBY,
                        errorMessage = "Microphone access denied: ${e.localizedMessage}"
                    )
                }
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                    recorder.release()
                }
            }
        }
    }

    fun stopMonitoring() {
        preferencesRepository.updateMonitoringEnabled(false)
        monitoringJob?.cancel()
        monitoringJob = null
        fadeJob?.cancel()
        fadeJob = null
        val restoredVol = volumeController.restoreOriginalImmediate()
        _telemetryFlow.update { state ->
            state.copy(
                currentDb = 0f,
                phase = DuckingPhase.STANDBY,
                holdProgress = 0f,
                recoveryProgress = 0f,
                currentMediaVolume = restoredVol,
                originalMediaVolume = null
            )
        }
    }

    fun resetPeakStats() {
        _telemetryFlow.update { state ->
            state.copy(
                peakDb = state.currentDb,
                triggerCount = 0,
                recentEvents = emptyList()
            )
        }
    }

    fun toggleTestReferenceAudio() {
        val enable = !_telemetryFlow.value.isTestTonePlaying
        volumeController.toggleTestTone(engineScope, enable) { playing ->
            _telemetryFlow.update { it.copy(isTestTonePlaying = playing) }
        }
    }

    private fun triggerVolumeDuck(triggerDb: Float, reductionPercent: Int) {
        fadeJob?.cancel()
        fadeJob = engineScope.launch {
            val finalVol = volumeController.fadeDownToReduction(reductionPercent) { stepVol ->
                _telemetryFlow.update {
                    it.copy(
                        currentMediaVolume = stepVol,
                        originalMediaVolume = volumeController.originalVolumeBeforeDuck
                    )
                }
            }
            val origVol = volumeController.originalVolumeBeforeDuck ?: finalVol
            appendEvent(
                message = "Ducked media volume ($origVol → $finalVol) at ${triggerDb.roundToInt()} dB",
                triggerDb = triggerDb,
                isDuckStart = true
            )
        }
    }

    private fun triggerVolumeRestore(currentDb: Float) {
        fadeJob?.cancel()
        fadeJob = engineScope.launch {
            val restoredVol = volumeController.fadeUpToOriginal { stepVol ->
                _telemetryFlow.update {
                    it.copy(
                        currentMediaVolume = stepVol,
                        originalMediaVolume = volumeController.originalVolumeBeforeDuck
                    )
                }
            }
            appendEvent(
                message = "Restored media volume to $restoredVol (${currentDb.roundToInt()} dB ambient)",
                triggerDb = currentDb,
                isDuckStart = false
            )
        }
    }

    private fun appendEvent(message: String, triggerDb: Float, isDuckStart: Boolean) {
        val event = DuckingEvent(
            id = eventIdCounter++,
            timestampMillis = System.currentTimeMillis(),
            message = message,
            triggerDb = triggerDb,
            isDuckStart = isDuckStart
        )
        _telemetryFlow.update { state ->
            state.copy(
                triggerCount = if (isDuckStart) state.triggerCount + 1 else state.triggerCount,
                recentEvents = (listOf(event) + state.recentEvents).take(8)
            )
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16000

        @Volatile
        private var instance: AudioDuckingEngine? = null

        fun getInstance(context: Context): AudioDuckingEngine {
            return instance ?: synchronized(this) {
                instance ?: AudioDuckingEngine(
                    appContext = context.applicationContext,
                    preferencesRepository = DuckingPreferencesRepository(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}
