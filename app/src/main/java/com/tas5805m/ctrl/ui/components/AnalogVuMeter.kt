package com.tas5805m.ctrl.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ----------------------------------------------------------------------------
// VU meter jarum analog gaya retro (panel krem / amber untuk sub, lampu clip, penanda peak).
// Digambar penuh di Canvas; nilai animasi hanya dibaca di blok draw (tanpa recomposition per frame).
// ----------------------------------------------------------------------------

private val Ink = Color(0xFF2B2418)
private val RedZone = Color(0xFFC62828)
private val PeakMark = Color(0xFF1565C0)
private val Bezel = Color(0xFF1A1510)

private val FaceNormal = listOf(Color(0xFFF6ECCB), Color(0xFFE3D09A))
private val FaceSub = listOf(Color(0xFFF8D88F), Color(0xFFDFA83A))

private const val SWEEP_HALF = 50f          // jarum menyapu ±50° dari tegak lurus
private const val RED_FROM_DB = -6f         // zona merah mulai −6 dB
private val MAJOR_DB = intArrayOf(-60, -40, -20, -10, -5, 0)

private class DialCache {
    var w = -1f
    var h = -1f
    var face: Brush? = null
    var vignette: Brush? = null
}

/**
 * @param levelDb posisi jarum (balistik: naik [attackMs], turun [releaseMs]).
 * @param peakDb  penanda peak-hold (segitiga biru di luar busur) + lampu CLIP bila ≥ −1 dB.
 * @param isSub   panel amber untuk subwoofer.
 * Beri ukuran lewat [modifier], mis. `Modifier.fillMaxWidth().aspectRatio(1.5f)`.
 */
@Composable
fun AnalogVuMeter(
    levelDb: Float,
    peakDb: Float,
    label: String,
    modifier: Modifier = Modifier,
    isSub: Boolean = false,
    minDb: Float = MIN_DB,
    attackMs: Int = 40,
    releaseMs: Int = 350,
) {
    val needle = rememberMeterFraction(dbToFraction(levelDb, minDb), attackMs, releaseMs)
    val peak = rememberMeterFraction(dbToFraction(peakDb, minDb), METER_ATTACK_MS, releaseMs)
    val lamp = animateFloatAsState(
        targetValue = if (peakDb >= -1f) 1f else 0f,
        animationSpec = tween(220),
        label = "vuLamp"
    )

    val measurer = rememberTextMeasurer()
    val scaleStyle = remember {
        TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = Ink)
    }
    val titleStyle = remember {
        TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ink)
    }
    val scaleLabels = remember(minDb, measurer) {
        MAJOR_DB.filter { it >= minDb }.map { db ->
            db to measurer.measure(if (db == 0) "0" else "−${-db}", scaleStyle)
        }
    }
    val title = remember(label, measurer) { measurer.measure(label, titleStyle) }
    val cache = remember(isSub) { DialCache() }

    Canvas(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w / 2f, h - 8.dp.toPx())
        val sinHalf = sin(Math.toRadians(SWEEP_HALF.toDouble())).toFloat()
        val r = min(pivot.y - 12.dp.toPx(), (w / 2f - 12.dp.toPx()) / sinHalf)

        /** Arah satuan jarum untuk fraksi 0..1 (0 = kiri −50°, 1 = kanan +50°). */
        fun dir(frac: Float): Offset {
            val a = Math.toRadians((-SWEEP_HALF + 2f * SWEEP_HALF * frac).toDouble())
            return Offset(sin(a).toFloat(), -cos(a).toFloat())
        }

        if (cache.w != w || cache.h != h || cache.face == null) {
            cache.w = w; cache.h = h
            cache.face = Brush.verticalGradient(if (isSub) FaceSub else FaceNormal, startY = 0f, endY = h)
            cache.vignette = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                center = Offset(w / 2f, h * 0.55f),
                radius = w * 0.75f
            )
        }

        // Panel + vinyet "lampu" retro
        drawRect(brush = cache.face!!)
        drawRect(brush = cache.vignette!!)

        // Busur skala + zona merah
        val arcTopLeft = Offset(pivot.x - r, pivot.y - r)
        val arcSize = Size(2f * r, 2f * r)
        drawArc(
            color = Ink, startAngle = -90f - SWEEP_HALF, sweepAngle = 2f * SWEEP_HALF, useCenter = false,
            topLeft = arcTopLeft, size = arcSize, style = Stroke(1.2.dp.toPx())
        )
        val redFrac = dbToFraction(RED_FROM_DB, minDb)
        drawArc(
            color = RedZone,
            startAngle = -90f - SWEEP_HALF + 2f * SWEEP_HALF * redFrac,
            sweepAngle = 2f * SWEEP_HALF * (1f - redFrac),
            useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(3.dp.toPx())
        )

        // Tick tiap 5 dB; mayor berlabel
        var db = (ceil(minDb / 5f) * 5f).toInt()
        while (db <= 0) {
            val major = db in MAJOR_DB
            val len = if (major) 9.dp.toPx() else 5.dp.toPx()
            val d = dir(dbToFraction(db.toFloat(), minDb))
            drawLine(
                color = if (db > RED_FROM_DB) RedZone else Ink,
                start = pivot + d * (r - len), end = pivot + d * r,
                strokeWidth = if (major) 1.4.dp.toPx() else 0.9.dp.toPx()
            )
            db += 5
        }
        for ((labelDb, tl) in scaleLabels) {
            val c = pivot + dir(dbToFraction(labelDb.toFloat(), minDb)) * (r - 18.dp.toPx())
            drawText(
                tl,
                color = if (labelDb > RED_FROM_DB) RedZone else Color.Unspecified,
                topLeft = Offset(c.x - tl.size.width / 2f, c.y - tl.size.height / 2f)
            )
        }

        // Judul kanal (kiri bawah)
        drawText(title, topLeft = Offset(8.dp.toPx(), h - title.size.height - 6.dp.toPx()))

        // Penanda peak-hold: segitiga di luar busur, menunjuk ke pusat
        val pf = peak.value.coerceIn(0f, 1f)
        if (pf > 0.001f) {
            val d = dir(pf)
            val tip = pivot + d * (r + 2.dp.toPx())
            val base = pivot + d * (r + 9.dp.toPx())
            val side = Offset(-d.y, d.x) * 3.dp.toPx()
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + side.x, base.y + side.y)
                lineTo(base.x - side.x, base.y - side.y)
                close()
            }
            drawPath(path, PeakMark)
        }

        // Jarum + bayangan
        val nf = needle.value.coerceIn(0f, 1f)
        val tip = pivot + dir(nf) * (r - 2.dp.toPx())
        val shadow = Offset(2.dp.toPx(), 2.dp.toPx())
        drawLine(Color.Black.copy(alpha = 0.25f), pivot + shadow, tip + shadow, 2.dp.toPx(), StrokeCap.Round)
        drawLine(Ink, pivot, tip, 1.6.dp.toPx(), StrokeCap.Round)

        // Tutup poros
        val capR = h * 0.15f
        drawCircle(Ink, radius = capR, center = pivot)
        drawCircle(Color(0xFF5A4B35), radius = capR * 0.4f, center = pivot)

        // Lampu CLIP (kanan atas)
        val lampC = Offset(w - 11.dp.toPx(), 11.dp.toPx())
        if (lamp.value > 0.01f) {
            drawCircle(RedZone.copy(alpha = 0.35f * lamp.value), radius = 9.dp.toPx(), center = lampC)
        }
        drawCircle(lerp(Color(0xFF5B1A1A), Color(0xFFFF3B30), lamp.value), radius = 4.dp.toPx(), center = lampC)

        // Bezel gelap
        drawRoundRect(
            color = Bezel, cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(6.dp.toPx())
        )
    }
}
