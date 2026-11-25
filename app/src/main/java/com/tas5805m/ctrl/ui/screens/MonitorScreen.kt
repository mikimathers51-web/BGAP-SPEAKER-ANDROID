package com.tas5805m.ctrl.ui.screens

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
import com.tas5805m.ctrl.data.SubPeakSnapshot
import com.tas5805m.ctrl.data.SubPeakTracker
import com.tas5805m.ctrl.ui.components.AnalogVuMeter
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.HorizontalLevelMeter
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.components.VerticalLevelMeter
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
    // Pengukur puncak khusus subwoofer (kanal sub terkeras dari semua kanal sub di mode ini).
    val subPeak = remember { SubPeakTracker() }
    var subSnap by remember { mutableStateOf<SubPeakSnapshot?>(null) }

    LaunchedEffect(state.mode) { meters.clear(); snaps = emptyMap(); subPeak.clear(); subSnap = null }

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
            val subLevels = (listOf(a, b) + MonitorLayout.extra(cur)).filter { it.isSub }
                .mapNotNull { MonitorLayout.read(cur, it.id) }
            if (subLevels.isNotEmpty()) subPeak.push(subLevels.max(), now)
            subSnap = subPeak.snapshot()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Classic", "Segmented", "Minimal", "Analog").forEachIndexed { i, n ->
                    Text(
                        n, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1,
                        color = if (meterStyle == i) Color.Black else TextMuted,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (meterStyle == i) OkGreen else Color.White.copy(alpha = 0.06f))
                            .clickable { vm.setMeterStyle(i) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (meterStyle == 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(Triple(chA, lA, pA), Triple(chB, lB, pB)).forEach { (ch, lv, pk) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            AnalogVuMeter(
                                levelDb = lv, peakDb = pk, label = ch.label, isSub = ch.isSub,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)
                            )
                            Spacer(Modifier.height(4.dp))
                            ReadoutRow(ch.label, lv)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReadoutRow("PK ", maxOf(pA, pB))
                    ReadoutRow("SES", sesMax)
                    Text(
                        "RESET",
                        color = AccentCyan, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                meters.values.forEach { it.resetPeaks() }
                                subPeak.resetPeaks()
                                snaps = meters.mapValues { it.value.snapshot() }
                                subSnap = subPeak.snapshot()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
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
                                    subPeak.resetPeaks()
                                    snaps = meters.mapValues { it.value.snapshot() }
                                    subSnap = subPeak.snapshot()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
                    if (meterStyle == 3) {
                        // Dial analog: maks. 2 per baris agar tetap terbaca di layar HP
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            extra.chunked(2).forEach { rowChs ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowChs.forEach { ch ->
                                        val sn = snaps[ch.id]
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            AnalogVuMeter(
                                                levelDb = sn.levelDb(isAvg), peakDb = sn.peakDb(),
                                                label = ch.label, isSub = ch.isSub,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                if (sn == null) "n/a" else fmtDb(sn.levelDb(isAvg)),
                                                color = if (sn == null) TextDim else if (ch.isSub) AccentAmber else dbColor(sn.levelDb(isAvg)),
                                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (rowChs.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
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

        // ── Puncak subwoofer ───────────────────────────────
        val hasSubCh = (listOf(chA, chB) + extra).any { it.isSub }
        if (hasSubCh) {
            SubPeakCard(subSnap)
        }
    }
}

@Composable
private fun SubPeakCard(snap: SubPeakSnapshot?) {
    GlassCard {
        CardTitle(text = "Puncak Subwoofer", trailing = { GlassBadge("hold 3 dtk") })
        if (snap == null) {
            Text("Menunggu data level sub…", color = TextMuted, fontSize = 12.sp)
            return@GlassCard
        }
        val now = snap.now / 10f
        val hold = snap.hold / 10f
        val ses = snap.session / 10f
        HorizontalLevelMeter(
            levelDb = now, peakDb = hold,
            modifier = Modifier.fillMaxWidth(), barHeight = 14.dp
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SubStat("PUNCAK", fmtDb(hold), dbColor(hold))
            SubStat("SESI", fmtDb(ses), dbColor(ses))
            SubStat("HEADROOM", "%.1f".format(snap.headroomDb), if (snap.headroomDb < 1f) BadRed else OkGreen)
            SubStat("CREST≈", "%.1f".format(snap.crestDb), TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SubStat("≥ −3 dB", "${snap.nearHits}×", if (snap.nearHits > 0) WarnAmber else TextDim)
            SubStat("CLIP ≥ −1", "${snap.clipHits}×", if (snap.clipHits > 0) BadRed else TextDim)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Level dicuplik ±3×/dtk dari chip; puncak sangat singkat di antara dua cuplikan bisa terlewat. " +
                "CREST = puncak − rata-rata cuplikan (perkiraan).",
            color = TextDim, fontSize = 10.sp
        )
    }
}

@Composable
private fun SubStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(
            value, color = color, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace
        )
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
    // Gambar + animasi fast-attack/slow-release ada di ui/components/LevelMeter.kt
    VerticalLevelMeter(
        levelDb = level,
        peakDb = peak,
        modifier = modifier,
        isSub = isSub,
        isClipping = peak >= -1f,   // peak-hold per kanal ≥ −1 dB → batang ikut berkedip merah
        style = meterStyle,
    )
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
