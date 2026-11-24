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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.MeterChannel
import com.tas5805m.ctrl.data.MeterSnapshot
import com.tas5805m.ctrl.data.MonitorChannel
import com.tas5805m.ctrl.data.MonitorLayout
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.theme.*
import kotlinx.coroutines.delay

/** Jeda antar poll: ±3×/dtk, sesuai asumsi [MeterChannel] (firmware `wlvl` aman ±6×/dtk). */
private const val TICK_MS = 300L

/**
 * Monitor — level meter dari data ASLI firmware: `lvl` (chip 1), `lvl2` (chip 2), `wlvl` (sub nirkabel).
 * Pemetaan kanal per mode ada di [MonitorLayout]. Poll hanya jalan saat layar terlihat ([PollWhileStarted]).
 * Mode "AVG" = rata-rata daya atas ±24 sampel terakhir (firmware hanya mengirim level cuplikan, bukan RMS gelombang).
 */
@Composable
fun MonitorScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val state by vm.state.collectAsStateWithLifecycle()

    // Pilihan meter disimpan di DataStore (bertahan setelah app ditutup). Kunci `meterRms` = mode AVG.
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isAvg = settings.meterRms
    val meterStyle = settings.meterStyle

    val meters = remember { mutableMapOf<String, MeterChannel>() }
    var snaps by remember { mutableStateOf<Map<String, MeterSnapshot>>(emptyMap()) }

    // Ganti mode = pemetaan kanal berubah → buang riwayat lama.
    LaunchedEffect(state.mode) { meters.clear(); snaps = emptyMap() }

    PollWhileStarted {
        var n = 0
        while (true) {
            val st = vm.state.value
            if (!st.connected) { delay(TICK_MS); continue }
            if (st.hasLvl) vm.spp.requestLvl()
            if (st.hasLvl2 && MonitorLayout.needsLvl2(st.mode)) vm.spp.requestLvl2()
            if (st.isWireless && st.hasWlvl) {
                // WLVL hanya mengisi peer yang sudah dikenal dari `status` (baris WSUBS) → status tiap ±1,5 dtk.
                if (n % 5 == 0) vm.spp.requestStatus()
                if (st.peerDetails.isNotEmpty()) vm.spp.requestWlvl()
            }
            delay(TICK_MS)   // beri waktu balasan tiba sebelum sampel diambil
            val cur = vm.state.value
            val now = System.currentTimeMillis()
            val (a, b) = MonitorLayout.main(cur.mode)
            for (ch in listOf(a, b) + MonitorLayout.extra(cur)) {
                val v = MonitorLayout.read(cur, ch.id)
                if (v == null) meters.remove(ch.id)          // n/a (slave offline / WLVL na)
                else meters.getOrPut(ch.id) { MeterChannel() }.push(v, now)
            }
            snaps = meters.mapValues { it.value.snapshot() }
            n++
        }
    }

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hubungkan perangkat dulu", color = Color.Gray)
        }
        return
    }

    val (chA, chB) = MonitorLayout.main(state.mode)
    val extra = MonitorLayout.extra(state)
    val sA = snaps[chA.id]; val sB = snaps[chB.id]
    val lA = sA.levelDb(isAvg); val lB = sB.levelDb(isAvg)
    val pA = sA.peakDb(); val pB = sB.peakDb()
    val sesMax = maxOf(sA?.sessionMax ?: MeterChannel.FLOOR, sB?.sessionMax ?: MeterChannel.FLOOR) / 10f
    val clip = maxOf(pA, pB) >= -1f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode toggle PEAK / AVG
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(false to "PEAK", true to "AVG").forEach { (avg, label) ->
                val active = isAvg == avg
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
                        .clickable { vm.setMeterRms(avg) }
                        .padding(vertical = 8.dp)
                )
            }
        }

        if (!state.hasLvl) {
            Text(
                "Firmware ini tidak melaporkan level meter (CAPS tanpa `lvl`).",
                color = WarnAmber, fontSize = 12.sp
            )
        }

        // ── Output (chip 1) ────────────────────────────────
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "OUTPUT · ${state.modeName().uppercase()}",
                    color = TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp
                )
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
                            .clickable { vm.setMeterStyle(i) }
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
                MeterPair(
                    a = chA, b = chB,
                    levelA = lA, levelB = lB, peakA = pA, peakB = pB,
                    style = meterStyle
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
                // Readout besar: kanal yang lebih keras
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    val loud = if (lA >= lB) chA else chB
                    val loudDb = maxOf(lA, lB)
                    Text(
                        loud.label,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        fmtDb(loudDb),
                        color = dbColor(loudDb),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Text("dB", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp))
                    ReadoutRow("PK ", maxOf(pA, pB))
                    ReadoutRow("SES", sesMax)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "RESET PEAKS",
                        color = AccentCyan, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                meters.values.forEach { it.resetPeaks() }
                                snaps = meters.mapValues { it.value.snapshot() }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Channels (chip 2 / sub nirkabel) ───────────────
        if (extra.isNotEmpty() || state.isWireless) {
            GlassCard {
                CardTitle(
                    text = "Channels",
                    trailing = { GlassBadge(if (state.isWireless) "wireless sub" else "chip 2") }
                )
                if (extra.isEmpty()) {
                    Text("Menunggu data sub nirkabel…", color = TextMuted, fontSize = 12.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        extra.forEach { ch ->
                            val sn = snaps[ch.id]
                            ChannelColumn(
                                ch.label, sn.levelDb(isAvg), sn.peakDb(),
                                Modifier.weight(1f), meterStyle,
                                isSub = ch.isSub, na = sn == null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterPair(
    a: MonitorChannel, b: MonitorChannel,
    levelA: Float, levelB: Float, peakA: Float, peakB: Float, style: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(a, levelA, peakA), Triple(b, levelB, peakB)).forEach { (ch, lv, pk) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalMeter(lv, pk, Modifier.width(28.dp).height(150.dp), style, isSub = ch.isSub)
                Spacer(Modifier.height(6.dp))
                Text(
                    ch.label, color = TextMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReadoutRow(label: String, db: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            fmtDb(db), color = dbColor(db), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace
        )
        Text(" dB", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun VerticalMeter(
    level: Float,
    peak: Float,
    modifier: Modifier,
    meterStyle: Int,
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
    meterStyle: Int,
    isSub: Boolean = false,
    na: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VerticalMeter(level, peak, Modifier.fillMaxWidth().weight(1f), meterStyle, isSub = isSub)
        Spacer(Modifier.height(8.dp))
        Text(name, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (na) "n/a" else fmtDb(level),
            color = if (na) TextDim else if (isSub) AccentAmber else dbColor(level),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ========================================================
// Helpers
// ========================================================
/** deci-dB → dB untuk tampilan (null = belum ada data → lantai). */
private fun MeterSnapshot?.levelDb(avg: Boolean): Float =
    (if (this == null) MeterChannel.FLOOR else if (avg) this.avg else this.now) / 10f

private fun MeterSnapshot?.peakDb(): Float = (this?.peakHold ?: MeterChannel.FLOOR) / 10f

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
