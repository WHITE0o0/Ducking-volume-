package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DuckingViewModel
import com.example.ui.components.DuckingControlsPanel
import com.example.ui.components.LiveDbMeterCard
import com.example.ui.components.MinimalBottomActionRow
import com.example.ui.components.MinimalPresetStripBar
import com.example.ui.components.PresetProfilesManagerCard
import com.example.ui.theme.MinimalBackgroundOffWhite
import com.example.ui.theme.MinimalBackgroundWhite
import com.example.ui.theme.MinimalThresholdRed
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AudioDuckingControllerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDuckingControllerApp(
    viewModel: DuckingViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetryFlow.collectAsStateWithLifecycle()
    val presets by viewModel.presetsFlow.collectAsStateWithLifecycle()
    val selectedPresetName by viewModel.selectedPresetNameFlow.collectAsStateWithLifecycle()

    var isSettingsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isPresetsDrawerOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSettingsSheetOpen || isPresetsDrawerOpen) {
        if (isSettingsSheetOpen) {
            isSettingsSheetOpen = false
        } else {
            isPresetsDrawerOpen = false
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val recordGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(recordGranted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm != telemetry.hasRecordPermission) {
                    viewModel.onPermissionResult(hasPerm)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPermissionsAndStart = {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MinimalBackgroundOffWhite,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
                    .testTag("main_scroll_container"),
                verticalArrangement = Arrangement.Bottom
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Expandable Preset Profiles Manager in the upper area when "presets" is tapped
                AnimatedVisibility(
                    visible = isPresetsDrawerOpen,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    PresetProfilesManagerCard(
                        presets = presets,
                        selectedPresetName = selectedPresetName,
                        currentSettings = settings,
                        onSelectPresetName = viewModel::selectPreset,
                        onApplySelectedPreset = viewModel::applySelectedPreset,
                        onApplyPresetDirect = viewModel::applyPreset,
                        onSaveCustomPreset = { name -> viewModel.saveCustomPreset(name) },
                        onDeletePreset = viewModel::deletePreset
                    )
                }

                // BAND 1: Full-width gray pill preset bar ("house", "house 2", "gym", "presets")
                MinimalPresetStripBar(
                    presets = presets,
                    selectedPresetName = selectedPresetName,
                    isPresetsPanelOpen = isPresetsDrawerOpen,
                    onQuickSelectAndApply = { preset ->
                        viewModel.applyPreset(preset)
                    },
                    onTogglePresetsPanel = {
                        isPresetsDrawerOpen = !isPresetsDrawerOpen
                    },
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // BAND 2: Dark-framed split meter box (left: vertical bars, right: "DB spl 00", red threshold line)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                ) {
                    LiveDbMeterCard(
                        telemetry = telemetry,
                        settings = settings,
                        onResetPeak = viewModel::resetPeakStats
                    )

                    telemetry.errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MinimalThresholdRed
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // BAND 3: Bottom two rounded-square buttons (left: 3-vertical-sliders Settings, right: red "on")
                    MinimalBottomActionRow(
                        isMonitoringEnabled = settings.isMonitoringEnabled,
                        onOpenSettings = { isSettingsSheetOpen = true },
                        onToggleMonitoring = { enable ->
                            if (enable && !telemetry.hasRecordPermission) {
                                requestPermissionsAndStart()
                            } else {
                                viewModel.setMonitoringEnabled(enable)
                            }
                        }
                    )
                }
            }
        }

        if (isSettingsSheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { isSettingsSheetOpen = false },
                sheetState = sheetState,
                containerColor = MinimalBackgroundWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    DuckingControlsPanel(
                        settings = settings,
                        presets = presets,
                        selectedPresetName = selectedPresetName,
                        isTestTonePlaying = telemetry.isTestTonePlaying,
                        onToggleMonitoring = { enable ->
                            if (enable && !telemetry.hasRecordPermission) {
                                requestPermissionsAndStart()
                            } else {
                                viewModel.setMonitoringEnabled(enable)
                            }
                        },
                        onSensitivityChange = viewModel::updateSensitivityDb,
                        onVolumeReductionChange = viewModel::updateVolumeReductionPercent,
                        onResponseDelayChange = viewModel::updateResponseDelaySeconds,
                        onRecoveryTimeChange = viewModel::updateRecoveryTimeSeconds,
                        onSelectPresetName = viewModel::selectPreset,
                        onApplySelectedPreset = viewModel::applySelectedPreset,
                        onSelectPreset = viewModel::applyPreset,
                        onSaveCustomPreset = { name -> viewModel.saveCustomPreset(name) },
                        onDeletePreset = viewModel::deletePreset,
                        onResetDefaults = viewModel::resetSettingsToDefaults,
                        onToggleTestTone = viewModel::toggleTestTone,
                        onStepMediaVolume = viewModel::stepMediaVolume
                    )
                }
            }
        }
    }
}
