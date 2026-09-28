package com.example.audio

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure acoustic calculation utilities for converting 16-bit PCM microphone samples
 * into calibrated decibel (dB SPL approximation) readings and computing ducked media volumes.
 */
object DecibelCalculator {

    // Reference minimum RMS to prevent log10(0)
    private const val MIN_RMS = 1.0
    private const val MAX_PCM_16BIT = 32767.0
    // Calibration offset so normal quiet room (~30dB) to loud speech (~75dB) maps onto 0..95 dB
    private const val CALIBRATION_MAX_DB = 92.0

    /**
     * Calculates root-mean-square (RMS) amplitude from a 16-bit PCM buffer.
     */
    fun calculateRms(buffer: ShortArray, readCount: Int): Double {
        if (readCount <= 0 || buffer.isEmpty()) return 0.0
        val count = readCount.coerceAtMost(buffer.size)
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count)
    }

    /**
     * Converts an RMS amplitude (0..32767) into a positive decibel reading (0..95 dB).
     * Uses 20 * log10(rms / 32767) + CALIBRATION_MAX_DB, clamped to [0f, 95f].
     */
    fun rmsToDecibels(rms: Double): Float {
        if (rms <= MIN_RMS) return 0f
        val normalized = (rms / MAX_PCM_16BIT).coerceIn(1e-5, 1.0)
        val dbfs = 20.0 * log10(normalized) // -100..0 dBFS
        val splApprox = (dbfs + CALIBRATION_MAX_DB).coerceIn(0.0, 95.0)
        return splApprox.toFloat()
    }

    /**
     * Calculates decibels directly from a 16-bit PCM buffer.
     */
    fun calculateDecibels(buffer: ShortArray, readCount: Int): Float {
        val rms = calculateRms(buffer, readCount)
        return rmsToDecibels(rms)
    }

    /**
     * Applies exponential moving average (EMA) smoothing to prevent jittery UI readings.
     * Uses faster attack (0.45) and gentler release (0.22) for responsive peak detection.
     */
    fun smoothDecibels(previousDb: Float, rawDb: Float): Float {
        val alpha = if (rawDb > previousDb) 0.45f else 0.22f
        val smoothed = previousDb + alpha * (rawDb - previousDb)
        return (smoothed * 10f).roundToInt() / 10f
    }

    /**
     * Calculates the target media stream volume index given the original volume
     * and the user's desired volume reduction percentage (0%..100%).
     */
    fun calculateTargetDuckedVolume(
        originalVolume: Int,
        reductionPercent: Int,
        maxVolume: Int
    ): Int {
        val clampedOriginal = originalVolume.coerceIn(0, maxVolume)
        val clampedReduction = reductionPercent.coerceIn(0, 100)
        val remainingFraction = (100 - clampedReduction) / 100f
        return (clampedOriginal * remainingFraction).roundToInt().coerceIn(0, clampedOriginal)
    }
}
