package com.tas5805m.ctrl.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.BgapColors
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber

// ============================================================================
// Balistik meter: fast attack / slow release
// ============================================================================

internal const val MIN_DB = -60f
private const val MAX_DB = 0f

/** Waktu naik (attack) — hampir instan supaya transien terlihat. */
const val METER_ATTACK_MS = 15

/**
 * Waktu turun (release). Data ESP32 datang tiap ±300 ms (MonitorScreen) / ±500 ms (Home),
 * jadi release ≈ jeda poll membuat batang mengalir terus tanpa berhenti di antara dua sampel.
 */
const val METER_RELEASE_MS = 300

internal fun dbToFraction(db: Float, minDb: Float): Float =
    ((db.coerceIn(minDb, MAX_DB) - minDb) / (MAX_DB - minDb))

/** Penyimpan arah perubahan target; BUKAN state (tidak memicu recompose). */
private class BallisticHolder(var target: Float, var spec: AnimationSpec<Float>)

/**
 * Mengubah fraksi target (0..1) jadi nilai ber-animasi dengan balistik meter:
 * target naik → tween pendek linear ([attackMs]); target turun → tween panjang yang melambat ([releaseMs]).
 *
 * Mengembalikan [State] (bukan nilai) — baca `.value` HANYA di dalam blok draw `Canvas`, sehingga tiap frame
 * animasi hanya memicu fase *draw*, tanpa recomposition. Itu yang menjaga 60–120 fps.
 */
@Composable
fun rememberMeterFraction(
    targetFraction: Float,
    attackMs: Int = METER_ATTACK_MS,
    releaseMs: Int = METER_RELEASE_MS,
): State<Float> {
    val attack = remember(attackMs) { tween<Float>(attackMs, easing = LinearEasing) }
    val release = remember(releaseMs) { tween<Float>(releaseMs, easing = LinearOutSlowInEasing) }
    val holder = remember { BallisticHolder(targetFraction, release) }
    // Spec hanya diganti saat target BERUBAH, supaya recompose induk dgn nilai sama tidak mengubah animasi berjalan.
    if (targetFraction != holder.target) {
        holder.spec = if (targetFraction > holder.target) attack else release
        holder.target = targetFraction
    }
    return animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = holder.spec,
        label = "meterBallistic"
    )
}

// ============================================================================
// Palet & cache brush
// ============================================================================

private val GradNormal = arrayOf(
    0f to OkGreen,
    0.55f to OkGreen,
    0.66f to Color(0xFF84CC16),
    0.78f to WarnAmber,
    0.90f to Color(0xFFF97316),
    1f to BadRed
)

private val GradSub = arrayOf(
    0f to Color(0xFFCA8A04),
    0.55f to Color(0xFFEAB308),
    0.72f to Color(0xFFF59E0B),
    0.86f to Color(0xFFF97316),
    1f to BadRed
)

/** Brush hanya dibuat ulang saat ukuran berubah, bukan tiap frame. */
private class BrushCache {
    private var length = -1f
    private var brush: Brush? = null
    fun vertical(h: Float, stops: Array<Pair<Float, Color>>): Brush {
        if (h != length || brush == null) {
            length = h
            brush = Brush.verticalGradient(colorStops = stops, startY = h, endY = 0f)
        }
        return brush!!
    }
    fun horizontal(w: Float, stops: Array<Pair<Float, Color>>): Brush {
        if (w != length || brush == null) {
            length = w
            brush = Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = w)
        }
        return brush!!
    }
}

private fun formatDb(db: Float): String = when {
    db <= MIN_DB + 0.5f -> "−∞"
    db > -0.5f -> "0.0"
    else -> "%.1f".format(db)
}

private fun dbColor(db: Float): Color = when {
    db > -1f -> BgapColors.Danger
    db > -6f -> BgapColors.Warn
    else -> BgapColors.TextMuted
}

// ============================================================================
// Meter vertikal (dipakai MonitorScreen)
// ============================================================================

/**
 * Meter vertikal berbasis [Canvas]. [style]: 0 = gradien, 1 = segmen (20 blok), 2 = minimal (satu warna).
 * Level & peak memakai balistik fast-attack/slow-release; seluruh gambar dilakukan di DrawScope.
 */
@Composable
fun VerticalLevelMeter(
    levelDb: Float,
    peakDb: Float,
    modifier: Modifier = Modifier,
    isSub: Boolean = false,
    isClipping: Boolean = false,
    style: Int = 0,
    minDb: Float = MIN_DB,
    attackMs: Int = METER_ATTACK_MS,
    releaseMs: Int = METER_RELEASE_MS,
) {
    val level = rememberMeterFraction(dbToFraction(levelDb, minDb), attackMs, releaseMs)
    val peak = rememberMeterFraction(dbToFraction(peakDb, minDb), attackMs, releaseMs)
    val clipAlpha = animateFloatAsState(
        targetValue = if (isClipping) 1f else 0f,
        animationSpec = tween(220),
        label = "clipAlpha"
    )
    val brushCache = remember { BrushCache() }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
    ) {
        // State dibaca DI SINI (fase draw) → tidak ada recomposition per frame.
        val lv = level.value.coerceIn(0f, 1f)
        val pk = peak.value.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height

        when (style) {
            1 -> { // Segmented: 20 blok, blok teratas menyala parsial supaya gerakan tetap mulus
                val n = 20
                val gap = 2.dp.toPx()
                val segH = (h - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val frac = (i + 1f) / n
                    val c = if (isSub) {
                        if (frac < 0.72f) Color(0xFFEAB308) else if (frac < 0.86f) Color(0xFFF97316) else BadRed
                    } else {
                        if (frac < 0.66f) OkGreen else if (frac < 0.86f) WarnAmber else BadRed
                    }
                    val cover = (lv * n - i).coerceIn(0f, 1f)
                    drawRect(
                        color = c.copy(alpha = 0.12f + 0.88f * cover),
                        topLeft = Offset(0f, h - (i + 1) * segH - i * gap),
                        size = Size(w, segH)
                    )
                }
            }
            2 -> { // Minimal: satu warna datar
                val flat = if (lv > 0.9f) BadRed else if (isSub) Color(0xFFEAB308) else OkGreen
                val mh = h * lv
                if (mh > 0f) drawRect(flat, Offset(0f, h - mh), Size(w, mh))
            }
            else -> { // Gradien
                val fh = h * lv
                if (fh > 0f) {
                    drawRect(
                        brush = brushCache.vertical(h, if (isSub) GradSub else GradNormal),
                        topLeft = Offset(0f, h - fh),
                        size = Size(w, fh)
                    )
                }
            }
        }

        // Garis peak-hold
        if (pk > 0.001f) {
            val y = h * (1f - pk)
            drawLine(Color.White, Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
        }

        // Flash clip
        if (clipAlpha.value > 0f) {
            drawRect(BgapColors.Danger.copy(alpha = clipAlpha.value * 0.6f))
        }
    }
}

// ============================================================================
// Meter horizontal (dipakai LevelBar & peer)
// ============================================================================

/**
 * Meter horizontal berbasis [Canvas] dengan balistik yang sama. [peakDb] boleh null (tanpa penanda peak).
 * [minDb] = lantai skala dB (default −60; Monitor dan Home sama-sama memakai −60).
 */
@Composable
fun HorizontalLevelMeter(
    levelDb: Float,
    peakDb: Float?,
    modifier: Modifier = Modifier,
    minDb: Float = MIN_DB,
    barHeight: Dp = 10.dp,
    attackMs: Int = METER_ATTACK_MS,
    releaseMs: Int = METER_RELEASE_MS,
) {
    val level = rememberMeterFraction(dbToFraction(levelDb, minDb), attackMs, releaseMs)
    val peak = rememberMeterFraction(if (peakDb == null) 0f else dbToFraction(peakDb, minDb), attackMs, releaseMs)
    val brushCache = remember { BrushCache() }

    Canvas(
        modifier = modifier
            .height(barHeight)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        val lv = level.value.coerceIn(0f, 1f)
        val pk = peak.value.coerceIn(0f, 1f)

        val fw = size.width * lv
        if (fw > 0f) {
            drawRect(
                brush = brushCache.horizontal(size.width, GradNormal),
                size = Size(fw, size.height)
            )
        }
        if (peakDb != null && pk > 0.001f) {
            val x = size.width * pk
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Baris meter horizontal lengkap dengan label + angka dB. */
@Composable
fun PeerMeterRow(
    name: String,
    sublabel: String,
    levelDb: Float,
    peakDb: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.width(60.dp)) {
            Text(
                name,
                color = BgapColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                sublabel,
                color = BgapColors.TextDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        HorizontalLevelMeter(
            levelDb = levelDb,
            peakDb = peakDb,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDb(levelDb),
            color = dbColor(levelDb),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End
        )
    }
}
