package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.DecibelCalculator
import com.example.audio.MediaVolumeController
import com.example.data.DuckingPreferencesRepository
import com.example.model.DEFAULT_PRESETS
import com.example.model.DuckingSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ducky", appName)
    }

    @Test
    fun `decibel calculator computes rms and target volume accurately`() {
        val silentBuffer = ShortArray(256) { 0 }
        assertEquals(0f, DecibelCalculator.calculateDecibels(silentBuffer, silentBuffer.size), 0.01f)

        val loudBuffer = ShortArray(256) { 16384 }
        val loudDb = DecibelCalculator.calculateDecibels(loudBuffer, loudBuffer.size)
        assertTrue("Expected loud dB > 70, got $loudDb", loudDb > 70f)

        val duckedVol = DecibelCalculator.calculateTargetDuckedVolume(
            originalVolume = 10,
            reductionPercent = 60,
            maxVolume = 15
        )
        assertEquals(4, duckedVol)
    }

    @Test
    fun `preferences repository persists all five controls and custom preset profiles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = DuckingPreferencesRepository(context)
        repo1.updateSensitivityDb(64.5f)
        repo1.updateVolumeReductionPercent(75)
        repo1.updateResponseDelaySeconds(1.8f)
        repo1.updateRecoveryTimeSeconds(3.2f)
        repo1.updateMonitoringEnabled(true)

        // Create and save custom preset profile
        val savedPreset = repo1.saveCustomPreset(
            name = "My Studio Profile",
            sensitivityDb = 47.0f,
            volumeReductionPercent = 80,
            responseDelaySeconds = 1.2f,
            recoveryTimeSeconds = 2.8f
        )
        assertNotNull(savedPreset)

        // Verify persistence across repository instances using SharedPreferences
        val repo2 = DuckingPreferencesRepository(context)
        val loaded = repo2.loadSettings()
        assertEquals(64.5f, loaded.sensitivityDb, 0.05f)
        assertEquals(75, loaded.volumeReductionPercent)
        assertEquals(1.8f, loaded.responseDelaySeconds, 0.05f)
        assertEquals(3.2f, loaded.recoveryTimeSeconds, 0.05f)
        assertTrue(loaded.isMonitoringEnabled)

        val loadedPresets = repo2.loadPresets()
        assertTrue(loadedPresets.any { it.name == "My Studio Profile" })

        repo2.selectPreset("My Studio Profile")
        repo2.applySelectedPreset()
        val afterPresetApplied = repo2.loadSettings()
        assertEquals(47.0f, afterPresetApplied.sensitivityDb, 0.05f)
        assertEquals(80, afterPresetApplied.volumeReductionPercent)
        assertEquals(1.2f, afterPresetApplied.responseDelaySeconds, 0.05f)
        assertEquals(2.8f, afterPresetApplied.recoveryTimeSeconds, 0.05f)

        repo2.applyPreset(DEFAULT_PRESETS.first())
        assertEquals(DEFAULT_PRESETS.first().sensitivityDb, repo2.loadSettings().sensitivityDb, 0.05f)

        repo2.resetToDefaults()
        assertEquals(DuckingSettings.DEFAULT_SENSITIVITY_DB, repo2.loadSettings().sensitivityDb, 0.05f)
    }

    @Test
    fun `media volume controller fades down and restores original volume`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = MediaVolumeController(context)
        controller.setMediaVolumeImmediate(10)
        assertEquals(10, controller.getCurrentMediaVolume())

        val ducked = controller.fadeDownToReduction(reductionPercent = 50, stepDelayMs = 1L)
        assertEquals(5, ducked)
        assertEquals(10, controller.originalVolumeBeforeDuck)
        assertTrue(controller.isCurrentlyDucked)

        val restored = controller.fadeUpToOriginal(stepDelayMs = 1L)
        assertEquals(10, restored)
        assertEquals(null, controller.originalVolumeBeforeDuck)
    }
}
