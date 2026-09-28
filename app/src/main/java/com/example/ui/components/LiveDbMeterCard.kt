package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuckingPhase
import com.example.model.DuckingSettings
import com.example.model.DuckingTelemetryState
import com.example.ui.theme.MinimalAccentLightGray
import com.example.ui.theme.MinimalBackgroundOffWhite
import com.example.ui.theme.MinimalBackgroundWhite
import com.example.ui.theme.MinimalCircleGray
import com.example.ui.theme.MinimalFrameDarkCharcoal
import com.example.ui.theme.MinimalTextDarkGray
import com.example.ui.theme.MinimalThresholdRed
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Split Dark-Framed Meter Box matching the reference image (Picsart_26-09-28_18-11-52-951.png):
 * - Outer dark charcoal (#3B3B3B) rounded rectangle
 * - Left half: 8 bold vertical white sound-level bars separated by dark charcoal slots
 * - Right half: White/off-white rectangular panel with stacked "DB / spl" and giant 2-digit dB readout ("00")
 * - Full-width vivid red horizontal threshold line cutting edge-to-edge across both halves
 */
@Composable
fun LiveDbMeterCard(
    telemetry: DuckingTelemetryState,
    settings: DuckingSettings,
    onResetPeak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedDb by animateFloatAsState(
        targetValue = telemetry.currentDb,
        animationSpec = spring(stiffness = 500f),
        label = "animatedDb"
    )
    val displayDbInt = animatedDb.roundToInt().coerceIn(0, 99)
    val formattedTwoDigitDb = String.format(Locale.US, "%02d", displayDbInt)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .testTag("live_db_meter_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MinimalFrameDarkCharcoal
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_db_bar_chart")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // LEFT HALF: 8 bold vertical white sound-level bars
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxChartDb = 90f
                        val totalW = size.width
                        val totalH = size.height

                        val rawBars = telemetry.barHistory
                        val barCount = 8
                        val displayBars = if (rawBars.size >= barCount) {
                            rawBars.takeLast(barCount)
                        } else {
                            List(barCount) { 0f }
                        }

                        val gap = 6.dp.toPx()
                        val barWidth = ((totalW - gap * (barCount - 1)) / barCount).coerceAtLeast(6f)

                        displayBars.forEachIndexed { index, dbVal ->
                            // Ensure visible baseline height like the reference image even in quiet rooms
                            val normalized = if (dbVal <= 1f) {
                                // Reference-style staggered idle bar silhouette when silent
                                val idleHeights = floatArrayOf(0.86f, 0.76f, 0.78f, 0.94f, 0.84f, 0.77f, 0.83f, 0.79f)
                                idleHeights[index % idleHeights.size] * 0.25f
                            } else {
                                (dbVal / maxChartDb).coerceIn(0.18f, 1f)
                            }
                            val barHeight = totalH * normalized
                            val x = index * (barWidth + gap)
                            val y = totalH - barHeight

                            val exceedsThreshold = dbVal >= settings.sensitivityDb
                            drawRoundRect(
                                color = if (exceedsThreshold) MinimalThresholdRed else MinimalBackgroundOffWhite,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // RIGHT HALF: Crisp white/off-white readout box with "DB / spl" and giant "00"
                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight(0.88f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MinimalBackgroundOffWhite)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "DB",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MinimalCircleGray
                                )
                                Text(
                                    text = "spl",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MinimalCircleGray
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = formattedTwoDigitDb,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 64.sp,
                                    lineHeight = 64.sp
                                ),
                                color = MinimalCircleGray,
                                modifier = Modifier.testTag("live_db_value_text")
                            )
                        }

                        // Compact threshold, reduction & status row at the bottom of the right cutout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thr: ${settings.sensitivityDb.roundToInt()}dB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MinimalAccentLightGray,
                                modifier = Modifier.testTag("threshold_summary_text")
                            )
                            Text(
                                text = "-${settings.volumeReductionPercent}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MinimalAccentLightGray,
                                modifier = Modifier.testTag("reduction_summary_text")
                            )
                            Text(
                                text = if (telemetry.phase == DuckingPhase.DUCKING_ACTIVE) "DUCK" else "LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = if (telemetry.phase == DuckingPhase.DUCKING_ACTIVE) {
                                    MinimalThresholdRed
                                } else {
                                    MinimalAccentLightGray
                                },
                                modifier = Modifier.testTag("status_badge")
                            )
                        }
                    }
                }
            }

            // Full-width vivid RED horizontal threshold line cutting edge-to-edge across the entire box
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxChartDb = 90f
                val thresholdNormalized = (settings.sensitivityDb / maxChartDb).coerceIn(0.15f, 0.85f)
                val y = size.height * (1f - thresholdNormalized)
                drawLine(
                    color = MinimalThresholdRed,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4.5.dp.toPx()
                )
            }
        }
    }
}

/**
 * Bottom row matching Picsart_26-09-28_18-11-52-951.png:
 * 1. Left rounded-square gray button (#9C9C9D) with 3 vertical fader/slider tracks & knobs -> opens Settings
 * 2. Second rounded-square red button (#E60000) with bold white "on" (or gray "off") -> toggles Master Monitoring
 */
@Composable
fun MinimalBottomActionRow(
    isMonitoringEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("master_header_card"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Compact gray rounded-square 3-vertical-sliders Settings button
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MinimalCircleGray)
                .clickable { onOpenSettings() }
                .semantics { contentDescription = "Open Settings Sliders" }
                .testTag("open_settings_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val trackStroke = 2.dp.toPx()
                val knobWidth = 4.2.dp.toPx()
                val knobHeight = 8.5.dp.toPx()
                val colX = listOf(size.width * 0.2f, size.width * 0.5f, size.width * 0.8f)
                val knobCenterY = listOf(size.height * 0.58f, size.height * 0.38f, size.height * 0.72f)

                for (i in 0..2) {
                    val x = colX[i]
                    drawLine(
                        color = MinimalFrameDarkCharcoal,
                        start = Offset(x, size.height * 0.10f),
                        end = Offset(x, size.height * 0.90f),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        color = MinimalFrameDarkCharcoal,
                        topLeft = Offset(x - knobWidth / 2f, knobCenterY[i] - knobHeight / 2f),
                        size = Size(knobWidth, knobHeight),
                        cornerRadius = CornerRadius(knobWidth / 2f, knobWidth / 2f)
                    )
                }
            }
        }

        // 2. Compact red rounded-square "on" Master Monitoring toggle button
        val buttonBgColor = if (isMonitoringEnabled) Color(0xFFE60000) else Color(0xFFD60000)
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(buttonBgColor)
                .clickable { onToggleMonitoring(!isMonitoringEnabled) }
                .semantics { contentDescription = "Master On Off Toggle" }
                .testTag("master_monitoring_switch"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "on",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                ),
                color = MinimalBackgroundWhite
            )
            if (isMonitoringEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MinimalBackgroundWhite)
                )
            }
        }
    }
}
