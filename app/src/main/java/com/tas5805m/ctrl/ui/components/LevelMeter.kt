package com.tas5805m.ctrl.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.ctrl.ui.theme.BgapColors

private const val MIN_DB = -60f
private const val MAX_DB = 0f

private fun dbToFraction(db: Float): Float =
    ((db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB))

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

/**
 * Meter vertikal dengan gradient hijau→kuning→merah, peak-hold, dan clip flash.
 */
@Composable
fun VerticalLevelMeter(
    levelDb: Float,
    peakDb: Float,
    modifier: Modifier = Modifier,
    isSub: Boolean = false,
    isClipping: Boolean = false
) {
    val clipAlpha by animateFloatAsState(
        targetValue = if (isClipping) 1f else 0f,
        animationSpec = tween(220),
        label = "clipAlpha"
    )

    Canvas(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.04f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
    ) {
        val corner = CornerRadius(6.dp.toPx())
        val levelFraction = dbToFraction(levelDb)
        val peakFraction = dbToFraction(peakDb)

        // Gradient anchor: hijau di bawah, merah di atas
        val gradientStops = if (isSub) arrayOf(
            0f to BgapColors.MeterSubStart,
            0.55f to Color(0xFFEAB308),
            0.72f to Color(0xFFF59E0B),
            0.86f to BgapColors.MeterOrange,
            1f to BgapColors.MeterRed
        ) else arrayOf(
            0f to BgapColors.MeterGreen,
            0.55f to BgapColors.MeterGreen,
            0.66f to BgapColors.MeterLime,
            0.78f to BgapColors.MeterYellow,
            0.90f to BgapColors.MeterOrange,
            1f to BgapColors.MeterRed
        )

        val brush = Brush.verticalGradient(
            colorStops = gradientStops,
            startY = size.height,
            endY = 0f
        )

        // Fill level
        val fillHeight = size.height * levelFraction
        if (fillHeight > 0f) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, size.height - fillHeight),
                size = Size(size.width, fillHeight),
                cornerRadius = corner
            )
        }

        // Peak marker (garis putih)
        if (peakFraction > 0.001f) {
            val peakY = size.height * (1f - peakFraction)
            drawLine(
                color = Color.White,
                start = Offset(0f, peakY),
                end = Offset(size.width, peakY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Clip glow overlay
        if (clipAlpha > 0f) {
            drawRoundRect(
                color = BgapColors.Danger.copy(alpha = clipAlpha * 0.6f),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Meter horizontal untuk peer wireless.
 */
@Composable
fun HorizontalLevelMeter(
    levelDb: Float,
    peakDb: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(10.dp)
            .background(
                Color.White.copy(alpha = 0.05f),
                androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
            )
    ) {
        val corner = CornerRadius(5.dp.toPx())
        val levelFraction = dbToFraction(levelDb)
        val peakFraction = dbToFraction(peakDb)

        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to BgapColors.MeterGreen,
                0.55f to BgapColors.MeterGreen,
                0.66f to BgapColors.MeterLime,
                0.78f to BgapColors.MeterYellow,
                0.90f to BgapColors.MeterOrange,
                1f to BgapColors.MeterRed
            ),
            startX = 0f,
            endX = size.width
        )

        val fillWidth = size.width * levelFraction
        if (fillWidth > 0f) {
            drawRoundRect(
                brush = brush,
                size = Size(fillWidth, size.height),
                cornerRadius = corner
            )
        }

        if (peakFraction > 0.001f) {
            val peakX = size.width * peakFraction
            drawLine(
                color = Color.White,
                start = Offset(peakX, 0f),
                end = Offset(peakX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * Baris meter horizontal lengkap dengan label + angka dB.
 */
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
