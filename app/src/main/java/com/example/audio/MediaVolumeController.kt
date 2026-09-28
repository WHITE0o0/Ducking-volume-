package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Controls device media stream (STREAM_MUSIC) volume via AudioManager.
 * Stores original volume before ducking and performs smooth multi-step fades
 * down and back up using Coroutines.
 */
class MediaVolumeController(context: Context) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    var originalVolumeBeforeDuck: Int? = null
        private set

    @Volatile
    var isCurrentlyDucked: Boolean = false
        private set

    private var testToneJob: Job? = null
    private var testAudioTrack: AudioTrack? = null

    fun getCurrentMediaVolume(): Int =
        runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)

    fun getMaxMediaVolume(): Int =
        runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15)
            .coerceAtLeast(1)

    fun setMediaVolumeImmediate(volumeIndex: Int) {
        val clamped = volumeIndex.coerceIn(0, getMaxMediaVolume())
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        }
    }

    /**
     * Captures the current media volume (if not already ducked) and smoothly fades
     * down to the target reduced volume.
     */
    suspend fun fadeDownToReduction(
        reductionPercent: Int,
        stepDelayMs: Long = 45L,
        onStep: (Int) -> Unit = {}
    ): Int {
        val maxVol = getMaxMediaVolume()
        val currentVol = getCurrentMediaVolume()
        val baseOriginal = originalVolumeBeforeDuck ?: currentVol.also {
            originalVolumeBeforeDuck = it
        }
        isCurrentlyDucked = true

        val targetVol = DecibelCalculator.calculateTargetDuckedVolume(
            originalVolume = baseOriginal,
            reductionPercent = reductionPercent,
            maxVolume = maxVol
        )

        smoothTransitionTo(targetVol, stepDelayMs, onStep)
        return getCurrentMediaVolume()
    }

    /**
     * Smoothly fades media volume back up to the stored original volume.
     */
    suspend fun fadeUpToOriginal(
        stepDelayMs: Long = 55L,
        onStep: (Int) -> Unit = {}
    ): Int {
        val targetOriginal = originalVolumeBeforeDuck ?: getCurrentMediaVolume()
        smoothTransitionTo(targetOriginal, stepDelayMs, onStep)
        originalVolumeBeforeDuck = null
        isCurrentlyDucked = false
        return getCurrentMediaVolume()
    }

    /**
     * Immediately restores original volume when monitoring is stopped.
     */
    fun restoreOriginalImmediate(): Int {
        val orig = originalVolumeBeforeDuck
        if (orig != null) {
            setMediaVolumeImmediate(orig)
        }
        originalVolumeBeforeDuck = null
        isCurrentlyDucked = false
        return getCurrentMediaVolume()
    }

    private suspend fun smoothTransitionTo(
        targetVolume: Int,
        stepDelayMs: Long,
        onStep: (Int) -> Unit
    ) {
        val maxVol = getMaxMediaVolume()
        val clampedTarget = targetVolume.coerceIn(0, maxVol)
        var current = getCurrentMediaVolume()

        if (current == clampedTarget) {
            onStep(current)
            return
        }

        val direction = if (clampedTarget > current) 1 else -1
        while (current != clampedTarget) {
            current = (current + direction).coerceIn(0, maxVol)
            setMediaVolumeImmediate(current)
            onStep(current)
            if (current != clampedTarget) {
                delay(stepDelayMs)
            }
        }
    }

    /**
     * Optional built-in ambient chord stream on STREAM_MUSIC so users can immediately
     * hear and verify media ducking behavior even without an external music player open.
     */
    fun toggleTestTone(scope: CoroutineScope, enable: Boolean, onStateChanged: (Boolean) -> Unit) {
        if (!enable) {
            stopTestTone()
            onStateChanged(false)
            return
        }
        if (testToneJob?.isActive == true) {
            stopTestTone()
            onStateChanged(false)
            return
        }
        testToneJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSamples = sampleRate / 10
            val pcmBuffer = ShortArray(bufferSamples)
            var phase1 = 0.0
            var phase2 = 0.0
            var phase3 = 0.0
            val freq1 = 220.0 // A3
            val freq2 = 277.18 // C#4
            val freq3 = 329.63 // E4

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSamples * 2 * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                onStateChanged(false)
                return@launch
            }

            testAudioTrack = track
            runCatching { track.play() }
            onStateChanged(true)

            try {
                while (isActive) {
                    for (i in 0 until bufferSamples) {
                        val s1 = sin(phase1)
                        val s2 = sin(phase2)
                        val s3 = sin(phase3)
                        val combined = (s1 + s2 * 0.7 + s3 * 0.5) / 2.2
                        pcmBuffer[i] = (combined * 3200).roundToInt().toShort()
                        phase1 += 2.0 * PI * freq1 / sampleRate
                        phase2 += 2.0 * PI * freq2 / sampleRate
                        phase3 += 2.0 * PI * freq3 / sampleRate
                    }
                    track.write(pcmBuffer, 0, bufferSamples)
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
                testAudioTrack = null
                onStateChanged(false)
            }
        }
    }

    fun stopTestTone() {
        testToneJob?.cancel()
        testToneJob = null
        runCatching {
            testAudioTrack?.stop()
            testAudioTrack?.release()
        }
        testAudioTrack = null
    }
}
