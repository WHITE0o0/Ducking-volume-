package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.DEFAULT_PRESETS
import com.example.model.DuckingPreset
import com.example.model.DuckingSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Persists and observes all 5 user controls as well as custom & default Preset Profiles
 * in SharedPreferences so settings and presets persist across sessions.
 */
class DuckingPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<DuckingSettings> = _settingsFlow.asStateFlow()

    private val _presetsFlow = MutableStateFlow(loadPresets())
    val presetsFlow: StateFlow<List<DuckingPreset>> = _presetsFlow.asStateFlow()

    private val _selectedPresetNameFlow = MutableStateFlow(loadSelectedPresetName())
    val selectedPresetNameFlow: StateFlow<String> = _selectedPresetNameFlow.asStateFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_SAVED_PRESETS_JSON -> _presetsFlow.value = loadPresets()
                KEY_SELECTED_PRESET_NAME -> _selectedPresetNameFlow.value = loadSelectedPresetName()
                else -> _settingsFlow.value = loadSettings()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun loadSettings(): DuckingSettings {
        val sensitivity = prefs.getFloat(
            KEY_SENSITIVITY_DB,
            DuckingSettings.DEFAULT_SENSITIVITY_DB
        ).coerceIn(DuckingSettings.MIN_SENSITIVITY_DB, DuckingSettings.MAX_SENSITIVITY_DB)

        val reduction = prefs.getInt(
            KEY_VOLUME_REDUCTION_PERCENT,
            DuckingSettings.DEFAULT_VOLUME_REDUCTION_PERCENT
        ).coerceIn(
            DuckingSettings.MIN_VOLUME_REDUCTION_PERCENT,
            DuckingSettings.MAX_VOLUME_REDUCTION_PERCENT
        )

        val responseDelay = prefs.getFloat(
            KEY_RESPONSE_DELAY_SECONDS,
            DuckingSettings.DEFAULT_RESPONSE_DELAY_SECONDS
        ).coerceIn(
            DuckingSettings.MIN_RESPONSE_DELAY_SECONDS,
            DuckingSettings.MAX_RESPONSE_DELAY_SECONDS
        )

        val recoveryTime = prefs.getFloat(
            KEY_RECOVERY_TIME_SECONDS,
            DuckingSettings.DEFAULT_RECOVERY_TIME_SECONDS
        ).coerceIn(
            DuckingSettings.MIN_RECOVERY_TIME_SECONDS,
            DuckingSettings.MAX_RECOVERY_TIME_SECONDS
        )

        val monitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, false)

        return DuckingSettings(
            sensitivityDb = (sensitivity * 10f).roundToInt() / 10f,
            volumeReductionPercent = reduction,
            responseDelaySeconds = (responseDelay * 10f).roundToInt() / 10f,
            recoveryTimeSeconds = (recoveryTime * 10f).roundToInt() / 10f,
            isMonitoringEnabled = monitoringEnabled
        )
    }

    fun loadPresets(): List<DuckingPreset> {
        val rawJson = prefs.getString(KEY_SAVED_PRESETS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            return DEFAULT_PRESETS
        }
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<DuckingPreset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    val sensitivity = obj.optDouble(
                        "sensitivityDb",
                        DuckingSettings.DEFAULT_SENSITIVITY_DB.toDouble()
                    ).toFloat().coerceIn(
                        DuckingSettings.MIN_SENSITIVITY_DB,
                        DuckingSettings.MAX_SENSITIVITY_DB
                    )
                    val reduction = obj.optInt(
                        "volumeReductionPercent",
                        DuckingSettings.DEFAULT_VOLUME_REDUCTION_PERCENT
                    ).coerceIn(
                        DuckingSettings.MIN_VOLUME_REDUCTION_PERCENT,
                        DuckingSettings.MAX_VOLUME_REDUCTION_PERCENT
                    )
                    val delay = obj.optDouble(
                        "responseDelaySeconds",
                        DuckingSettings.DEFAULT_RESPONSE_DELAY_SECONDS.toDouble()
                    ).toFloat().coerceIn(
                        DuckingSettings.MIN_RESPONSE_DELAY_SECONDS,
                        DuckingSettings.MAX_RESPONSE_DELAY_SECONDS
                    )
                    val recovery = obj.optDouble(
                        "recoveryTimeSeconds",
                        DuckingSettings.DEFAULT_RECOVERY_TIME_SECONDS.toDouble()
                    ).toFloat().coerceIn(
                        DuckingSettings.MIN_RECOVERY_TIME_SECONDS,
                        DuckingSettings.MAX_RECOVERY_TIME_SECONDS
                    )
                    val isCustom = obj.optBoolean("isCustom", false)
                    list.add(
                        DuckingPreset(
                            name = name,
                            subtitle = DuckingPreset.formatSummary(
                                sensitivity,
                                reduction,
                                delay,
                                recovery
                            ),
                            sensitivityDb = (sensitivity * 10f).roundToInt() / 10f,
                            volumeReductionPercent = reduction,
                            responseDelaySeconds = (delay * 10f).roundToInt() / 10f,
                            recoveryTimeSeconds = (recoveryTimeRound(recovery)),
                            isCustom = isCustom
                        )
                    )
                }
            }
            if (list.isEmpty()) DEFAULT_PRESETS else list
        } catch (_: Exception) {
            DEFAULT_PRESETS
        }
    }

    private fun recoveryTimeRound(value: Float): Float = (value * 10f).roundToInt() / 10f

    fun loadSelectedPresetName(): String {
        val currentPresets = loadPresets()
        val savedName = prefs.getString(KEY_SELECTED_PRESET_NAME, null)
        if (!savedName.isNullOrBlank() && currentPresets.any { it.name.equals(savedName, ignoreCase = true) }) {
            return currentPresets.first { it.name.equals(savedName, ignoreCase = true) }.name
        }
        return currentPresets.firstOrNull()?.name ?: DEFAULT_PRESETS.first().name
    }

    fun selectPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_SELECTED_PRESET_NAME, trimmed).apply()
        _selectedPresetNameFlow.value = trimmed
    }

    fun saveCustomPreset(
        name: String,
        sensitivityDb: Float = _settingsFlow.value.sensitivityDb,
        volumeReductionPercent: Int = _settingsFlow.value.volumeReductionPercent,
        responseDelaySeconds: Float = _settingsFlow.value.responseDelaySeconds,
        recoveryTimeSeconds: Float = _settingsFlow.value.recoveryTimeSeconds
    ): DuckingPreset? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null

        val clampedSensitivity = ((sensitivityDb.coerceIn(
            DuckingSettings.MIN_SENSITIVITY_DB,
            DuckingSettings.MAX_SENSITIVITY_DB
        ) * 10f).roundToInt() / 10f)
        val clampedReduction = volumeReductionPercent.coerceIn(
            DuckingSettings.MIN_VOLUME_REDUCTION_PERCENT,
            DuckingSettings.MAX_VOLUME_REDUCTION_PERCENT
        )
        val clampedDelay = ((responseDelaySeconds.coerceIn(
            DuckingSettings.MIN_RESPONSE_DELAY_SECONDS,
            DuckingSettings.MAX_RESPONSE_DELAY_SECONDS
        ) * 10f).roundToInt() / 10f)
        val clampedRecovery = ((recoveryTimeSeconds.coerceIn(
            DuckingSettings.MIN_RECOVERY_TIME_SECONDS,
            DuckingSettings.MAX_RECOVERY_TIME_SECONDS
        ) * 10f).roundToInt() / 10f)

        val newPreset = DuckingPreset(
            name = cleanName,
            subtitle = DuckingPreset.formatSummary(
                clampedSensitivity,
                clampedReduction,
                clampedDelay,
                clampedRecovery
            ),
            sensitivityDb = clampedSensitivity,
            volumeReductionPercent = clampedReduction,
            responseDelaySeconds = clampedDelay,
            recoveryTimeSeconds = clampedRecovery,
            isCustom = true
        )

        val updatedList = loadPresets().toMutableList()
        val existingIndex = updatedList.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        if (existingIndex >= 0) {
            updatedList[existingIndex] = newPreset
        } else {
            updatedList.add(newPreset)
        }

        persistPresetsList(updatedList)
        selectPreset(newPreset.name)
        return newPreset
    }

    fun deletePreset(name: String) {
        val current = loadPresets().toMutableList()
        val removed = current.removeAll { it.name.equals(name.trim(), ignoreCase = true) }
        if (removed) {
            val finalList = if (current.isEmpty()) DEFAULT_PRESETS else current
            persistPresetsList(finalList)
            if (_selectedPresetNameFlow.value.equals(name.trim(), ignoreCase = true)) {
                selectPreset(finalList.first().name)
            }
        }
    }

    private fun persistPresetsList(presets: List<DuckingPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject().apply {
                put("name", preset.name)
                put("sensitivityDb", preset.sensitivityDb.toDouble())
                put("volumeReductionPercent", preset.volumeReductionPercent)
                put("responseDelaySeconds", preset.responseDelaySeconds.toDouble())
                put("recoveryTimeSeconds", preset.recoveryTimeSeconds.toDouble())
                put("isCustom", preset.isCustom)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_PRESETS_JSON, array.toString()).apply()
        _presetsFlow.value = presets
    }

    fun updateSensitivityDb(value: Float) {
        val clamped = ((value.coerceIn(
            DuckingSettings.MIN_SENSITIVITY_DB,
            DuckingSettings.MAX_SENSITIVITY_DB
        ) * 10f).roundToInt() / 10f)
        prefs.edit().putFloat(KEY_SENSITIVITY_DB, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(sensitivityDb = clamped)
    }

    fun updateVolumeReductionPercent(value: Int) {
        val clamped = value.coerceIn(
            DuckingSettings.MIN_VOLUME_REDUCTION_PERCENT,
            DuckingSettings.MAX_VOLUME_REDUCTION_PERCENT
        )
        prefs.edit().putInt(KEY_VOLUME_REDUCTION_PERCENT, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(volumeReductionPercent = clamped)
    }

    fun updateResponseDelaySeconds(value: Float) {
        val clamped = ((value.coerceIn(
            DuckingSettings.MIN_RESPONSE_DELAY_SECONDS,
            DuckingSettings.MAX_RESPONSE_DELAY_SECONDS
        ) * 10f).roundToInt() / 10f)
        prefs.edit().putFloat(KEY_RESPONSE_DELAY_SECONDS, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(responseDelaySeconds = clamped)
    }

    fun updateRecoveryTimeSeconds(value: Float) {
        val clamped = ((value.coerceIn(
            DuckingSettings.MIN_RECOVERY_TIME_SECONDS,
            DuckingSettings.MAX_RECOVERY_TIME_SECONDS
        ) * 10f).roundToInt() / 10f)
        prefs.edit().putFloat(KEY_RECOVERY_TIME_SECONDS, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(recoveryTimeSeconds = clamped)
    }

    fun updateMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isMonitoringEnabled = enabled)
    }

    fun applyPreset(preset: DuckingPreset) {
        prefs.edit()
            .putFloat(KEY_SENSITIVITY_DB, preset.sensitivityDb)
            .putInt(KEY_VOLUME_REDUCTION_PERCENT, preset.volumeReductionPercent)
            .putFloat(KEY_RESPONSE_DELAY_SECONDS, preset.responseDelaySeconds)
            .putFloat(KEY_RECOVERY_TIME_SECONDS, preset.recoveryTimeSeconds)
            .putString(KEY_SELECTED_PRESET_NAME, preset.name)
            .apply()
        _selectedPresetNameFlow.value = preset.name
        _settingsFlow.value = loadSettings()
    }

    fun applySelectedPreset(): DuckingPreset? {
        val selectedName = _selectedPresetNameFlow.value
        val preset = _presetsFlow.value.firstOrNull {
            it.name.equals(selectedName, ignoreCase = true)
        } ?: _presetsFlow.value.firstOrNull()
        if (preset != null) {
            applyPreset(preset)
        }
        return preset
    }

    fun resetToDefaults() {
        val keepMonitoring = _settingsFlow.value.isMonitoringEnabled
        prefs.edit()
            .putFloat(KEY_SENSITIVITY_DB, DuckingSettings.DEFAULT_SENSITIVITY_DB)
            .putInt(KEY_VOLUME_REDUCTION_PERCENT, DuckingSettings.DEFAULT_VOLUME_REDUCTION_PERCENT)
            .putFloat(KEY_RESPONSE_DELAY_SECONDS, DuckingSettings.DEFAULT_RESPONSE_DELAY_SECONDS)
            .putFloat(KEY_RECOVERY_TIME_SECONDS, DuckingSettings.DEFAULT_RECOVERY_TIME_SECONDS)
            .putBoolean(KEY_MONITORING_ENABLED, keepMonitoring)
            .apply()
        _settingsFlow.value = loadSettings()
    }

    companion object {
        const val PREFS_NAME = "audio_ducking_controller_prefs"
        const val KEY_SENSITIVITY_DB = "sensitivity_db"
        const val KEY_VOLUME_REDUCTION_PERCENT = "volume_reduction_percent"
        const val KEY_RESPONSE_DELAY_SECONDS = "response_delay_seconds"
        const val KEY_RECOVERY_TIME_SECONDS = "recovery_time_seconds"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_SAVED_PRESETS_JSON = "saved_presets_json"
        const val KEY_SELECTED_PRESET_NAME = "selected_preset_name"
    }
}
