package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuckingPreset
import com.example.model.DuckingSettings
import com.example.ui.theme.MinimalAccentLightGray
import com.example.ui.theme.MinimalBackgroundWhite
import com.example.ui.theme.MinimalCardLightGray
import com.example.ui.theme.MinimalCircleGray
import com.example.ui.theme.MinimalFrameDarkCharcoal
import com.example.ui.theme.MinimalTextDarkGray
import com.example.ui.theme.MinimalThresholdRed
import com.example.ui.theme.MinimalTrackBorderGray
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-width rounded gray pill bar (#9C9C9D) matching the reference image:
 * - Left: scrollable white rounded-rect buttons ("house", "house 2", "gym", and saved custom presets)
 * - Right: "presets" white rounded-rect button with a crisp white underline bar below it
 */
@Composable
fun MinimalPresetStripBar(
    presets: List<DuckingPreset>,
    selectedPresetName: String,
    isPresetsPanelOpen: Boolean,
    onQuickSelectAndApply: (DuckingPreset) -> Unit,
    onTogglePresetsPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preset_strip_bar"),
        shape = RoundedCornerShape(32.dp),
        color = MinimalCircleGray
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { preset ->
                    val isSelected = preset.name.equals(selectedPresetName, ignoreCase = true)
                    val safeTag = preset.name.lowercase(Locale.US).replace(" ", "_")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MinimalBackgroundWhite)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MinimalFrameDarkCharcoal,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onQuickSelectAndApply(preset) }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                            .testTag("preset_chip_$safeTag"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            ),
                            color = if (isSelected) MinimalFrameDarkCharcoal else MinimalCircleGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right-side "presets" button with white underline bar underneath, matching reference image
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTogglePresetsPanel() }
                    .testTag("open_presets_manager_button")
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(MinimalBackgroundWhite)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "presets",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        ),
                        color = if (isPresetsPanelOpen) MinimalFrameDarkCharcoal else MinimalCircleGray
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .background(
                            if (isPresetsPanelOpen) MinimalThresholdRed else MinimalBackgroundWhite
                        )
                )
            }
        }
    }
}

/**
 * Complete Preset Profiles Manager allowing users to:
 * 1. Select a preset from a Dropdown or List View
 * 2. Click "Apply Selected Preset" to load its 4 parameters into active DuckingSettings
 * 3. Name and Save custom combinations of Sensitivity, Volume Reduction, Response Delay, and Recovery Time
 *    persistently into SharedPreferences.
 */
@Composable
fun PresetProfilesManagerCard(
    presets: List<DuckingPreset>,
    selectedPresetName: String,
    currentSettings: DuckingSettings,
    onSelectPresetName: (String) -> Unit,
    onApplySelectedPreset: () -> Unit,
    onApplyPresetDirect: (DuckingPreset) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var presetNameInput by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    val selectedPreset = presets.firstOrNull {
        it.name.equals(selectedPresetName, ignoreCase = true)
    } ?: presets.firstOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preset_profiles_manager_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalCardLightGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Preset Profiles",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MinimalTextDarkGray
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Dropdown Selector + "Apply Preset" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("preset_dropdown_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPreset?.name ?: "Select preset",
                                color = MinimalTextDarkGray,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open preset dropdown",
                                tint = MinimalTextDarkGray
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.testTag("preset_dropdown_menu")
                    ) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = preset.name,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalTextDarkGray
                                        )
                                        Text(
                                            text = preset.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MinimalAccentLightGray
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectPresetName(preset.name)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onApplySelectedPreset()
                        statusFeedback = "Applied \"${selectedPreset?.name ?: ""}\""
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("apply_selected_preset_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinimalFrameDarkCharcoal,
                        contentColor = MinimalBackgroundWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Saved Presets List View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preset_list_view"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset.name.equals(selectedPresetName, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinimalBackgroundWhite)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        1.5.dp,
                                        MinimalFrameDarkCharcoal,
                                        RoundedCornerShape(12.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                onSelectPresetName(preset.name)
                                onApplyPresetDirect(preset)
                                statusFeedback = "Loaded \"${preset.name}\""
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MinimalTextDarkGray
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MinimalAccentLightGray
                            )
                        }

                        if (presets.size > 1) {
                            IconButton(
                                onClick = { onDeletePreset(preset.name) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag(
                                        "delete_preset_${preset.name.lowercase(Locale.US).replace(" ", "_")}"
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete preset ${preset.name}",
                                    tint = MinimalAccentLightGray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MinimalTrackBorderGray)
            Spacer(modifier = Modifier.height(10.dp))

            // 3. Create & Save Custom Preset Section
            Text(
                text = String.format(
                    Locale.US,
                    "Save Current (%d dB • -%d%% • %.1fs delay • %.1fs rec)",
                    currentSettings.sensitivityDb.roundToInt(),
                    currentSettings.volumeReductionPercent,
                    currentSettings.responseDelaySeconds,
                    currentSettings.recoveryTimeSeconds
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MinimalAccentLightGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    placeholder = { Text("Preset name (e.g. studio)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preset_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MinimalBackgroundWhite,
                        unfocusedContainerColor = MinimalBackgroundWhite,
                        focusedBorderColor = MinimalFrameDarkCharcoal,
                        unfocusedBorderColor = MinimalTrackBorderGray
                    )
                )

                Button(
                    onClick = {
                        val trimmed = presetNameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onSaveCustomPreset(trimmed)
                            statusFeedback = "Saved preset \"$trimmed\""
                            presetNameInput = ""
                        }
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("save_preset_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinimalThresholdRed,
                        contentColor = MinimalBackgroundWhite
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            statusFeedback?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MinimalFrameDarkCharcoal,
                    modifier = Modifier.testTag("preset_status_feedback")
                )
            }
        }
    }
}
