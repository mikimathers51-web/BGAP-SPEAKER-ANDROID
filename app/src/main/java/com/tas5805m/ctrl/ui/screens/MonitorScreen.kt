package com.tas5805m.ctrl.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Monitor screen — level meter real-time (PEAK / RMS).
 * Visual diselaraskan dengan prototipe HTML (glass card, badge, meter gradient, readout besar).
 */
@Composable
fun MonitorScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val meterCtx = androidx.compose.ui.platform.LocalContext.current
    val meterPrefs = remember { meterCtx.getSharedPreferences("bgap_ui", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) { meterStyle = meterPrefs.getInt("meter_style", 0).coerceIn(0, 2) }

    var masterL by remember { mutableStateOf(-60f) }
    var masterR by remember { mutableStateOf(-60f) }
    var masterLPeak by remember { mutableStateOf(-60f) }
    var masterRPeak by remember { mutableStateOf(-60f) }
    var subLvl by remember { mutableStateOf(-60f) }
    var satLLvl by remember { mutableStateOf(-60f) }
    var satRLvl by remember { mutableStateOf(-60f) }
    var subPeak by remember { mutableStateOf(-60f) }
    var satLPeak by remember { mutableStateOf(-60f) }
    var satRPeak by remember { mutableStateOf(-60f) }

    var isRms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val t = (System.currentTimeMillis() - start) / 1000f
            val subT  = signal(t, -18f, 0f, 1.7f) + sin(t * 0.4f) * 3f
            val satLT = signal(t, -22f, 1.1f, 2.1f)
            val satRT = signal(t, -22f, 2.3f, 1.9f)
            val mLT = maxOf(subT, satLT) + 1.5f
            val mRT = maxOf(subT, satRT) + 1.5f

            val att = if (isRms) 0.15f else 0.40f
            val rel = if (isRms) 0.08f else 0.15f
            val off = if (isRms) -3f else 0f

            masterL = lerpMeter(masterL, mLT + off, att, rel)
            masterR = lerpMeter(masterR, mRT + off, att, rel)
            subLvl  = lerpMeter(subLvl,  subT + off, 0.30f, 0.12f)
            satLLvl = lerpMeter(satLLvl, satLT + off, att, rel)
            satRLvl = lerpMeter(satRLvl, satRT + off, att, rel)

            masterLPeak = updatePeak(masterLPeak, masterL)
            masterRPeak = updatePeak(masterRPeak, masterR)
            subPeak     = updatePeak(subPeak, subLvl)
            satLPeak    = updatePeak(satLPeak, satLLvl)
            satRPeak    = updatePeak(satRPeak, satRLvl)

            delay(33)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode toggle PEAK / RMS (seperti HTML)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(false to "PEAK", true to "RMS").forEach { (rms, label) ->
                val active = isRms == rms
                Text(
                    label,
                    color = if (active) AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) AccentCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (active) AccentCyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { isRms = rms }
                        .padding(vertical = 8.dp)
                )
            }
        }

        // ── Master Output ──────────────────────────────────
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MASTER OUTPUT",
                    color = TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp
                )
                val clip = masterL > -1f || masterR > -1f
                Text(
                    "CLIP",
                    color = if (clip) BadRed else TextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (clip) BadRed.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Classic", "Segmented", "Minimal").forEachIndexed { i, n ->
                    Text(
                        n, fontSize = 12.sp,
                        color = if (meterStyle == i) Color.Black else TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (meterStyle == i) OkGreen else Color.White.copy(alpha = 0.06f))
                            .clickable { meterStyle = i; meterPrefs.edit().putInt("meter_style", i).apply() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // L / R vertical meters
                MeterPair(
                    leftLevel = masterL, rightLevel = masterR,
                    leftPeak = masterLPeak, rightPeak = masterRPeak
                )
                // dB scale
                Column(
                    modifier = Modifier.height(150.dp).padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0", "−12", "−24", "−36", "−48", "−60").forEach {
                        Text(it, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                // Big readout (mirip screenshot: angka besar di kanan)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    val peak = maxOf(masterLPeak, masterRPeak)
                    Text(
                        "R",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        fmtDb(masterR),
                        color = dbColor(masterR),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        "dB",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PK  ",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            fmtDb(peak),
                            color = dbColor(peak),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            " dB",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── Channels ───────────────────────────────────────
        GlassCard {
            CardTitle(
                text = "Channels",
                trailing = { GlassBadge("from TAS5805M") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelColumn("Sub",   subLvl,  subPeak,  Modifier.weight(1f), isSub = true)
                ChannelColumn("Sat L", satLLvl, satLPeak, Modifier.weight(1f))
                ChannelColumn("Sat R", satRLvl, satRPeak, Modifier.weight(1f))
            }
        }
    }
}

/** Gaya level meter (0 Classic, 1 Segmented, 2 Minimal) — meniru pilihan di versi HTML. */
private var meterStyle by mutableIntStateOf(0)

@Composable
private fun MeterPair(leftLevel: Float, rightLevel: Float, leftPeak: Float, rightPeak: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VerticalMeter(leftLevel, leftPeak, Modifier.width(28.dp).height(150.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "L", color = TextMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VerticalMeter(rightLevel, rightPeak, Modifier.width(28.dp).height(150.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "R", color = TextMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun VerticalMeter(
    level: Float,
    peak: Float,
    modifier: Modifier,
    isSub: Boolean = false
) {
    val animated by animateFloatAsState(
        targetValue = ((level.coerceIn(-60f, 0f) + 60f) / 60f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "meter"
    )
    val peakFrac = ((peak.coerceIn(-60f, 0f) + 60f) / 60f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
    ) {
        val brush = Brush.verticalGradient(
            colorStops = if (isSub) arrayOf(
                0f    to Color(0xFFCA8A04),
                0.55f to Color(0xFFEAB308),
                0.72f to Color(0xFFF59E0B),
                0.86f to Color(0xFFF97316),
                1f    to BadRed
            ) else arrayOf(
                0f    to OkGreen,
                0.55f to OkGreen,
                0.66f to Color(0xFF84CC16),
                0.78f to WarnAmber,
                0.90f to Color(0xFFF97316),
                1f    to BadRed
            ),
            startY = size.height,
            endY = 0f
        )
        if (meterStyle == 1) {            // Segmented: 20 blok
            val n = 20
            val gap = 2.dp.toPx()
            val segH = (size.height - gap * (n - 1)) / n
            val lit = (animated * n).toInt()
            for (i in 0 until n) {
                val frac = (i + 1f) / n
                val c = if (isSub) { if (frac < 0.72f) Color(0xFFEAB308) else if (frac < 0.86f) Color(0xFFF97316) else BadRed }
                        else { if (frac < 0.66f) OkGreen else if (frac < 0.86f) WarnAmber else BadRed }
                drawRect(
                    color = c.copy(alpha = if (i < lit) 1f else 0.12f),
                    topLeft = Offset(0f, size.height - (i + 1) * segH - i * gap),
                    size = Size(size.width, segH)
                )
            }
        } else if (meterStyle == 2) {     // Minimal: satu warna datar
            val flat = if (animated > 0.9f) BadRed else if (isSub) Color(0xFFEAB308) else OkGreen
            val mh = size.height * animated
            if (mh > 0f) drawRect(flat, Offset(0f, size.height - mh), Size(size.width, mh))
        }
        val h = if (meterStyle == 0) size.height * animated else 0f
        if (h > 0f) {
            drawRect(
                brush = brush,
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h)
            )
        }
        // Peak hold line
        if (peakFrac > 0.001f) {
            val y = size.height * (1f - peakFrac)
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun ChannelColumn(
    name: String,
    level: Float,
    peak: Float,
    modifier: Modifier,
    isSub: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VerticalMeter(level, peak, Modifier.fillMaxWidth().weight(1f), isSub = isSub)
        Spacer(Modifier.height(8.dp))
        Text(name, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            fmtDb(level),
            color = if (isSub) AccentAmber else dbColor(level),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ========================================================
// Helpers
// ========================================================
private fun signal(t: Float, base: Float, offset: Float, speed: Float): Float =
    base +
    sin(t * speed + offset) * 6f +
    sin(t * speed * 2.31f + offset * 1.7f) * 3.5f +
    sin(t * speed * 5.13f + offset * 2.9f) * 1.8f +
    sin(t * speed * 9.7f + offset * 4.1f) * 0.9f

private fun lerpMeter(current: Float, target: Float, attack: Float, release: Float): Float {
    val t = target.coerceIn(-60f, 0f)
    return (current + (t - current) * (if (t > current) attack else release)).coerceIn(-60f, 0f)
}

private fun updatePeak(peak: Float, level: Float): Float =
    if (level > peak) level else (peak - 0.35f).coerceAtLeast(-60f).coerceAtLeast(level)

private fun fmtDb(db: Float): String = when {
    db <= -59.5f -> "−∞"
    db > -0.5f   -> "0.0"
    else         -> "%.1f".format(db)
}

private fun dbColor(db: Float): Color = when {
    db > -1f -> BadRed
    db > -6f -> WarnAmber
    else     -> AccentCyan
}
