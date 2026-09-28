package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DEFAULT_PRESETS
import com.example.model.DuckingPreset
import com.example.model.DuckingSettings
import com.example.ui.theme.MinimalAccentLightGray
import com.example.ui.theme.MinimalBackgroundWhite
import com.example.ui.theme.MinimalCardLightGray
import com.example.ui.theme.MinimalTextDarkGray
import com.example.ui.theme.MinimalThresholdRed
import com.example.ui.theme.MinimalTrackBorderGray
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DuckingControlsPanel(
    settings: DuckingSettings,
    presets: List<DuckingPreset> = DEFAULT_PRESETS,
    selectedPresetName: String = presets.firstOrNull()?.name ?: "house",
    isTestTonePlaying: Boolean = false,
    onToggleMonitoring: (Boolean) -> Unit = {},
    onSensitivityChange: (Float) -> Unit,
    onVolumeReductionChange: (Int) -> Unit,
    onResponseDelayChange: (Float) -> Unit,
    onRecoveryTimeChange: (Float) -> Unit,
    onSelectPresetName: (String) -> Unit = {},
    onApplySelectedPreset: () -> Unit = {},
    onSelectPreset: (DuckingPreset) -> Unit,
    onSaveCustomPreset: (String) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onResetDefaults: () -> Unit,
    onToggleTestTone: () -> Unit = {},
    onStepMediaVolume: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_panel_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MinimalCardLightGray
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Presets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextDarkGray
                )

                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("reset_defaults_button")
                ) {
                    Text(
                        text = stringResource(R.string.reset_defaults),
                        color = MinimalAccentLightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Master Monitoring Toggle row inside settings panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.master_monitoring_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalTextDarkGray
                    )
                    Text(
                        text = if (settings.isMonitoringEnabled) "Monitoring active" else "Monitoring paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MinimalAccentLightGray
                    )
                }

                Switch(
                    checked = settings.isMonitoringEnabled,
                    onCheckedChange = onToggleMonitoring,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MinimalBackgroundWhite,
                        checkedTrackColor = MinimalThresholdRed,
                        uncheckedThumbColor = MinimalAccentLightGray,
                        uncheckedTrackColor = MinimalTrackBorderGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MinimalTrackBorderGray)
            Spacer(modifier = Modifier.height(14.dp))

            // Preset Profiles Manager (Dropdown, List View, Apply Selected, Create & Save Custom)
            PresetProfilesManagerCard(
                presets = presets,
                selectedPresetName = selectedPresetName,
                currentSettings = settings,
                onSelectPresetName = onSelectPresetName,
                onApplySelectedPreset = onApplySelectedPreset,
                onApplyPresetDirect = onSelectPreset,
                onSaveCustomPreset = onSaveCustomPreset,
                onDeletePreset = onDeletePreset
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MinimalTrackBorderGray)
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Sensitivity Slider (10dB - 80dB)
            MinimalSliderControl(
                title = stringResource(R.string.sensitivity_label),
                subtitle = stringResource(R.string.sensitivity_desc),
                valueBadge = "${settings.sensitivityDb.roundToInt()} dB",
                rangeMinLabel = "10 dB",
                rangeMaxLabel = "80 dB",
                value = settings.sensitivityDb,
                valueRange = DuckingSettings.MIN_SENSITIVITY_DB..DuckingSettings.MAX_SENSITIVITY_DB,
                steps = 69,
                activeColor = MinimalThresholdRed,
                testTag = "sensitivity_slider",
                onValueChange = onSensitivityChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Volume Reduction Slider (0% - 100%)
            MinimalSliderControl(
                title = stringResource(R.string.volume_reduction_label),
                subtitle = stringResource(R.string.volume_reduction_desc),
                valueBadge = "${settings.volumeReductionPercent}%",
                rangeMinLabel = "0%",
                rangeMaxLabel = "100%",
                value = settings.volumeReductionPercent.toFloat(),
                valueRange = DuckingSettings.MIN_VOLUME_REDUCTION_PERCENT.toFloat()..DuckingSettings.MAX_VOLUME_REDUCTION_PERCENT.toFloat(),
                steps = 19,
                activeColor = MinimalTextDarkGray,
                testTag = "volume_reduction_slider",
                onValueChange = { onVolumeReductionChange(it.roundToInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Response Delay Slider (0s - 5s)
            MinimalSliderControl(
                title = stringResource(R.string.response_delay_label),
                subtitle = stringResource(R.string.response_delay_desc),
                valueBadge = String.format(Locale.US, "%.1f s", settings.responseDelaySeconds),
                rangeMinLabel = "0.0 s",
                rangeMaxLabel = "5.0 s",
                value = settings.responseDelaySeconds,
                valueRange = DuckingSettings.MIN_RESPONSE_DELAY_SECONDS..DuckingSettings.MAX_RESPONSE_DELAY_SECONDS,
                steps = 49,
                activeColor = MinimalTextDarkGray,
                testTag = "response_delay_slider",
                onValueChange = onResponseDelayChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Recovery Time Slider (0.5s - 5s)
            MinimalSliderControl(
                title = stringResource(R.string.recovery_time_label),
                subtitle = stringResource(R.string.recovery_time_desc),
                valueBadge = String.format(Locale.US, "%.1f s", settings.recoveryTimeSeconds),
                rangeMinLabel = "0.5 s",
                rangeMaxLabel = "5.0 s",
                value = settings.recoveryTimeSeconds,
                valueRange = DuckingSettings.MIN_RECOVERY_TIME_SECONDS..DuckingSettings.MAX_RECOVERY_TIME_SECONDS,
                steps = 44,
                activeColor = MinimalTextDarkGray,
                testTag = "recovery_time_slider",
                onValueChange = onRecoveryTimeChange
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MinimalTrackBorderGray)
            Spacer(modifier = Modifier.height(14.dp))

            // Built-in test audio stream controls inside settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onToggleTestTone,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_test_tone_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MinimalBackgroundWhite,
                        contentColor = MinimalTextDarkGray
                    )
                ) {
                    Icon(
                        imageVector = if (isTestTonePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTestTonePlaying) "Stop Test Audio" else "Play Test Audio")
                }

                OutlinedButton(
                    onClick = { onStepMediaVolume(-1) },
                    modifier = Modifier.testTag("volume_minus_button")
                ) {
                    Text("Vol -", color = MinimalTextDarkGray)
                }

                OutlinedButton(
                    onClick = { onStepMediaVolume(1) },
                    modifier = Modifier.testTag("volume_plus_button")
                ) {
                    Text("Vol +", color = MinimalTextDarkGray)
                }
            }
        }
    }
}

@Composable
private fun MinimalSliderControl(
    title: String,
    subtitle: String,
    valueBadge: String,
    rangeMinLabel: String,
    rangeMaxLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    activeColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimalTextDarkGray
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MinimalAccentLightGray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = valueBadge,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                modifier = Modifier.testTag("${testTag}_value")
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = MinimalTrackBorderGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics { contentDescription = title }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = rangeMinLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MinimalAccentLightGray
            )
            Text(
                text = rangeMaxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MinimalAccentLightGray
            )
        }
    }
}
