package com.example.model

import java.util.Locale
import kotlin.math.roundToInt

data class DuckingSettings(
    val sensitivityDb: Float = DEFAULT_SENSITIVITY_DB,
    val volumeReductionPercent: Int = DEFAULT_VOLUME_REDUCTION_PERCENT,
    val responseDelaySeconds: Float = DEFAULT_RESPONSE_DELAY_SECONDS,
    val recoveryTimeSeconds: Float = DEFAULT_RECOVERY_TIME_SECONDS,
    val isMonitoringEnabled: Boolean = false
) {
    companion object {
        const val MIN_SENSITIVITY_DB = 10f
        const val MAX_SENSITIVITY_DB = 80f
        const val DEFAULT_SENSITIVITY_DB = 52f

        const val MIN_VOLUME_REDUCTION_PERCENT = 0
        const val MAX_VOLUME_REDUCTION_PERCENT = 100
        const val DEFAULT_VOLUME_REDUCTION_PERCENT = 65

        const val MIN_RESPONSE_DELAY_SECONDS = 0.0f
        const val MAX_RESPONSE_DELAY_SECONDS = 5.0f
        const val DEFAULT_RESPONSE_DELAY_SECONDS = 0.8f

        const val MIN_RECOVERY_TIME_SECONDS = 0.5f
        const val MAX_RECOVERY_TIME_SECONDS = 5.0f
        const val DEFAULT_RECOVERY_TIME_SECONDS = 2.0f
    }
}

enum class DuckingPhase(val displayLabel: String) {
    STANDBY("Standby"),
    MONITORING("Monitoring"),
    THRESHOLD_HOLD("Detecting Sound"),
    DUCKING_ACTIVE("Volume Ducked"),
    RECOVERING("Recovering Volume")
}

data class DuckingEvent(
    val id: Long,
    val timestampMillis: Long,
    val message: String,
    val triggerDb: Float,
    val isDuckStart: Boolean
)

data class DuckingPreset(
    val name: String,
    val subtitle: String,
    val sensitivityDb: Float,
    val volumeReductionPercent: Int,
    val responseDelaySeconds: Float,
    val recoveryTimeSeconds: Float,
    val isCustom: Boolean = false
) {
    companion object {
        fun formatSummary(
            sensitivityDb: Float,
            volumeReductionPercent: Int,
            responseDelaySeconds: Float,
            recoveryTimeSeconds: Float
        ): String {
            return String.format(
                Locale.US,
                "%d dB • -%d%% • %.1fs delay • %.1fs rec",
                sensitivityDb.roundToInt(),
                volumeReductionPercent,
                responseDelaySeconds,
                recoveryTimeSeconds
            )
        }
    }
}

val DEFAULT_PRESETS = listOf(
    DuckingPreset(
        name = "house",
        subtitle = DuckingPreset.formatSummary(42f, 70, 0.5f, 2.0f),
        sensitivityDb = 42f,
        volumeReductionPercent = 70,
        responseDelaySeconds = 0.5f,
        recoveryTimeSeconds = 2.0f,
        isCustom = false
    ),
    DuckingPreset(
        name = "house 2",
        subtitle = DuckingPreset.formatSummary(52f, 65, 0.8f, 2.5f),
        sensitivityDb = 52f,
        volumeReductionPercent = 65,
        responseDelaySeconds = 0.8f,
        recoveryTimeSeconds = 2.5f,
        isCustom = false
    ),
    DuckingPreset(
        name = "gym",
        subtitle = DuckingPreset.formatSummary(68f, 50, 1.5f, 3.0f),
        sensitivityDb = 68f,
        volumeReductionPercent = 50,
        responseDelaySeconds = 1.5f,
        recoveryTimeSeconds = 3.0f,
        isCustom = false
    )
)

data class DuckingTelemetryState(
    val currentDb: Float = 0f,
    val peakDb: Float = 0f,
    val averageDb: Float = 0f,
    val barHistory: List<Float> = List(BAR_HISTORY_SIZE) { 0f },
    val phase: DuckingPhase = DuckingPhase.STANDBY,
    val currentMediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val originalMediaVolume: Int? = null,
    val holdProgress: Float = 0f,
    val recoveryProgress: Float = 0f,
    val triggerCount: Int = 0,
    val recentEvents: List<DuckingEvent> = emptyList(),
    val isTestTonePlaying: Boolean = false,
    val hasRecordPermission: Boolean = false,
    val errorMessage: String? = null
) {
    val currentVolumePercent: Int
        get() = if (maxMediaVolume > 0) {
            ((currentMediaVolume.toFloat() / maxMediaVolume.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else 0

    val originalVolumePercent: Int?
        get() = originalMediaVolume?.let { orig ->
            if (maxMediaVolume > 0) {
                ((orig.toFloat() / maxMediaVolume.toFloat()) * 100f).toInt().coerceIn(0, 100)
            } else 0
        }

    companion object {
        const val BAR_HISTORY_SIZE = 32
    }
}
