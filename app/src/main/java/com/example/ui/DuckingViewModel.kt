package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.audio.AudioDuckingEngine
import com.example.model.DuckingPreset
import com.example.model.DuckingSettings
import com.example.model.DuckingTelemetryState
import com.example.service.AudioDuckingService
import kotlinx.coroutines.flow.StateFlow

class DuckingViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioDuckingEngine.getInstance(application)
    private val preferences = engine.preferencesRepository

    val settingsFlow: StateFlow<DuckingSettings> = engine.settingsFlow
    val telemetryFlow: StateFlow<DuckingTelemetryState> = engine.telemetryFlow
    val presetsFlow: StateFlow<List<DuckingPreset>> = preferences.presetsFlow
    val selectedPresetNameFlow: StateFlow<String> = preferences.selectedPresetNameFlow

    init {
        engine.refreshPermissionAndVolumeState()
        if (settingsFlow.value.isMonitoringEnabled && engine.hasAudioPermission()) {
            AudioDuckingService.startService(getApplication())
        }
    }

    fun onPermissionResult(granted: Boolean) {
        engine.refreshPermissionAndVolumeState()
        if (granted) {
            setMonitoringEnabled(true)
        } else {
            preferences.updateMonitoringEnabled(false)
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        if (enabled) {
            if (engine.hasAudioPermission()) {
                preferences.updateMonitoringEnabled(true)
                AudioDuckingService.startService(getApplication())
                engine.startMonitoring()
            } else {
                preferences.updateMonitoringEnabled(false)
            }
        } else {
            preferences.updateMonitoringEnabled(false)
            engine.stopMonitoring()
            AudioDuckingService.stopService(getApplication())
        }
    }

    fun updateSensitivityDb(value: Float) {
        preferences.updateSensitivityDb(value)
    }

    fun updateVolumeReductionPercent(value: Int) {
        preferences.updateVolumeReductionPercent(value)
        engine.syncMediaVolumeIfUnducked(value)
    }

    fun updateResponseDelaySeconds(value: Float) {
        preferences.updateResponseDelaySeconds(value)
    }

    fun updateRecoveryTimeSeconds(value: Float) {
        preferences.updateRecoveryTimeSeconds(value)
    }

    fun selectPreset(name: String) {
        preferences.selectPreset(name)
    }

    fun applyPreset(preset: DuckingPreset) {
        preferences.applyPreset(preset)
        engine.syncMediaVolumeIfUnducked(preset.volumeReductionPercent)
    }

    fun applySelectedPreset() {
        val applied = preferences.applySelectedPreset()
        if (applied != null) {
            engine.syncMediaVolumeIfUnducked(applied.volumeReductionPercent)
        }
    }

    fun saveCustomPreset(
        name: String,
        sensitivityDb: Float = settingsFlow.value.sensitivityDb,
        volumeReductionPercent: Int = settingsFlow.value.volumeReductionPercent,
        responseDelaySeconds: Float = settingsFlow.value.responseDelaySeconds,
        recoveryTimeSeconds: Float = settingsFlow.value.recoveryTimeSeconds
    ): DuckingPreset? {
        return preferences.saveCustomPreset(
            name = name,
            sensitivityDb = sensitivityDb,
            volumeReductionPercent = volumeReductionPercent,
            responseDelaySeconds = responseDelaySeconds,
            recoveryTimeSeconds = recoveryTimeSeconds
        )
    }

    fun deletePreset(name: String) {
        preferences.deletePreset(name)
    }

    fun resetSettingsToDefaults() {
        preferences.resetToDefaults()
        engine.syncMediaVolumeIfUnducked(DuckingSettings.DEFAULT_VOLUME_REDUCTION_PERCENT)
    }

    fun resetPeakStats() {
        engine.resetPeakStats()
    }

    fun toggleTestTone() {
        engine.toggleTestReferenceAudio()
    }

    fun stepMediaVolume(delta: Int) {
        val current = engine.volumeController.getCurrentMediaVolume()
        engine.volumeController.setMediaVolumeImmediate(current + delta)
        engine.refreshPermissionAndVolumeState()
    }

    override fun onCleared() {
        super.onCleared()
        engine.volumeController.stopTestTone()
    }
}
