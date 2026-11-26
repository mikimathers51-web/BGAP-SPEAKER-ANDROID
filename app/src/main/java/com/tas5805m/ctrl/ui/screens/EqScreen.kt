package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import com.tas5805m.ctrl.data.PresetTools
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.tas5805m.ctrl.data.BuiltinPresets
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.theme.WarnAmber
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.ui.components.NotReady
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.EqSync
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * EQ screen — Axton/DS18 style: frequency response curve + band sliders.
 */
@Composable
fun EqScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp
    var link by remember { mutableStateOf(true) }
    var editCh by remember { mutableStateOf("l") }   // kanal yang diedit slider saat L/R dipisah
    val userPresets by vm.presets.collectAsStateWithLifecycle()
    var presetName by remember { mutableStateOf("") }
    // Mode 3: EQ terpisah untuk satelit (eqband), center (eqc c) dan sub (eqc s)
    var target by remember { mutableStateOf("SAT") }
    val hasTargets = state.is31 && state.hasEqc
    val tgt = if (hasTargets) target else "SAT"
    val chip2 = tgt != "SAT"
    val linkEff = link || chip2
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    val src: List<Float> = when (tgt) {
        "CTR" -> state.eqCtr
        "SUB" -> state.eqSub
        else -> state.eqLeft
    }

    if (!state.ready) {
        NotReady(state, modifier)
        return
    }

    val lockedBands = if (state.xoverLocksEq) 3 else 0
    val shown: List<Float> = if (!linkEff && editCh == "r") state.eqRight else src
    val other: List<Float>? = if (linkEff) null else if (editCh == "r") state.eqLeft else state.eqRight
    // Salinan kerja yang ikut bergerak saat slider diseret (kurva membacanya); direset bila nilai dari perangkat berubah.
    val live = remember(shown, tgt, linkEff, editCh) {
        mutableStateListOf<Float>().also { l -> l.addAll(List(15) { i -> shown.getOrElse(i) { 0f } }) }
    }
    var activeSlot by remember(tgt) { mutableStateOf("A") }
    val slots = remember { mutableStateMapOf<String, List<Float>>() }       // "A|SAT" → gain 15 band
    var bypassSaved by remember(tgt) { mutableStateOf<List<Float>?>(null) }  // non-null = EQ sedang di-bypass
    fun gainsNow(): List<Float> = List(15) { live.getOrElse(it) { 0f } }
    fun applyGains(g: List<Float>) { scope.launch { spp.applyEqShape(tgt, g, lockedBands) } }
    fun flashMsg(m: String) { scope.launch { syncMsg = m; kotlinx.coroutines.delay(3000); syncMsg = null } }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Toolbar
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.eqOn,
                // firmware menolak `eq off` di mode 1/2/3/4 (bypass akan menghapus crossover)
                enabled = !(state.xoverLocksEq && state.eqOn),
                onClick = { spp.setEq(!state.eqOn) },
                label = { Text(if (state.eqOn) "EQ ON" else "EQ OFF", fontWeight = FontWeight.Bold) }
            )
            if (!chip2) {
                FilterChip(
                    selected = link,
                    onClick = { link = !link },
                    label = { Text(if (link) "L+R" else "L / R") }
                )
            }
            if (hasTargets) {
                listOf("SAT", "CTR", "SUB").forEach { t ->
                    FilterChip(
                        selected = tgt == t,
                        onClick = { target = t },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
            if (!linkEff) {
                FilterChip(selected = editCh == "l", onClick = { editCh = "l" }, label = { Text("Edit L") })
                FilterChip(selected = editCh == "r", onClick = { editCh = "r" }, label = { Text("Edit R") })
            }
            // Sync L→R: hanya relevan saat kanal L dan R dipisah (bukan L+R, bukan target CTR/SUB)
            if (!linkEff) {
                TextButton(
                    enabled = !syncing,
                    onClick = {
                        syncing = true
                        scope.launch {
                            val n = spp.syncEqLeftToRight(
                                state.eqLeft, state.eqRight,
                                if (state.xoverLocksEq) EqSync.LOCKED_BANDS else 0
                            )
                            syncMsg = if (n == 0) "R sudah sama dengan L" else "L→R: $n band disalin"
                            syncing = false
                            kotlinx.coroutines.delay(3000); syncMsg = null
                        }
                    }
                ) { Text("Sync L→R") }
            }
            TextButton(onClick = { spp.requestEqDump() }) { Text("Muat ulang") }
        }
        syncMsg?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.xoverLocksEq) {
            Text(
                "Mode ${state.modeName()}: band 0–2 dikunci (crossover)",
                style = MaterialTheme.typography.labelSmall,
                color = WarnAmber,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Preset cepat (dari BuiltinPresets, hanya jenis EQ)
        // A/B · Copy · Bypass · Reset (A/B & Bypass memakai jalur "preset": menulis L dan R sama → aktif saat L+R)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("A/B", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            listOf("A", "B").forEach { slot ->
                FilterChip(
                    selected = activeSlot == slot,
                    enabled = linkEff,
                    onClick = {
                        if (slot != activeSlot) {
                            slots["$activeSlot|$tgt"] = gainsNow()               // simpan slot yang ditinggalkan
                            val g = slots["$slot|$tgt"] ?: gainsNow()             // slot baru kosong → mulai dari EQ saat ini
                            activeSlot = slot
                            bypassSaved = null
                            applyGains(g)
                        }
                    },
                    label = { Text(slot, fontWeight = FontWeight.Bold) }
                )
            }
            AssistChip(
                enabled = linkEff,
                onClick = {
                    val to = if (activeSlot == "A") "B" else "A"
                    slots["$to|$tgt"] = gainsNow()
                    flashMsg("EQ $activeSlot disalin ke $to")
                },
                label = { Text("Copy → ${if (activeSlot == "A") "B" else "A"}") }
            )
            FilterChip(
                selected = bypassSaved != null,
                enabled = linkEff,
                onClick = {
                    val saved = bypassSaved
                    if (saved == null) { bypassSaved = gainsNow(); applyGains(List(15) { 0f }) }
                    else { bypassSaved = null; applyGains(saved) }
                },
                label = { Text("Bypass") }
            )
            TextButton(onClick = { bypassSaved = null; applyGains(List(15) { 0f }) }) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        }
        if (bypassSaved != null) {
            Text("EQ di-bypass (flat sementara). Tekan Bypass lagi untuk mengembalikan.",
                style = MaterialTheme.typography.labelSmall, color = WarnAmber,
                modifier = Modifier.padding(horizontal = 16.dp))
        } else if (!linkEff) {
            Text("A/B, Copy, dan Bypass aktif saat mode L+R.",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val eqPresets = BuiltinPresets.all.filter { it.kind == "eq" } + userPresets.filter { it.kind == "eq" }
            eqPresets.forEach { p ->
                val active = src.size >= 15 && p.eqLeft.size >= 15 &&
                    (lockedBands until 15).all { src[it].roundToInt() == p.eqLeft[it].roundToInt() }
                FilterChip(
                    selected = active,
                    onClick = { bypassSaved = null; applyGains(p.eqLeft) },   // kirim + geser slider + eqdump
                    label = { Text(p.name, fontSize = 12.sp) },
                    leadingIcon = { MiniCurve(p.eqLeft, if (active) MaterialTheme.colorScheme.primary else WarnAmber) },
                    trailingIcon = {
                        // preset buatan sendiri bisa dihapus lewat ×
                        if (!p.builtin) Text("×", fontSize = 16.sp, modifier = Modifier.clickable { vm.deletePreset(p.id) }.padding(horizontal = 4.dp))
                    }
                )
            }
        }

        // Nama preset + Save EQ (disimpan sebagai preset jenis "eq"; hanya untuk target SAT/utama)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it.take(PresetTools.MAX_NAME) },
                placeholder = { Text("Nama preset EQ…") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = tgt == "SAT",
                onClick = {
                    val l = if (linkEff || editCh == "l") gainsNow() else state.eqLeft
                    val r = if (linkEff || editCh == "r") gainsNow() else state.eqRight
                    vm.saveEqPreset(presetName, l, r)
                    presetName = ""
                    flashMsg("Preset EQ disimpan.")
                }
            ) { Text("Save EQ") }
        }
        if (tgt != "SAT") {
            Text("Save EQ hanya untuk target SAT (preset EQ tidak membawa EQ center/sub).",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Kurva respons + 15 slider vertikal (kolom sejajar dengan titik kurva)
        GlassCard(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            contentPadding = 8.dp
        ) {
            EqPanel(
                live = live,
                other = other,
                lockedBands = lockedBands,
                onSend = { i, v ->
                    bypassSaved = null   // mengubah slider mengakhiri bypass (nilai baru jadi EQ aktif)
                    when {
                        tgt == "CTR" -> spp.setEqChip2("c", i, v)
                        tgt == "SUB" -> spp.setEqChip2("s", i, v)
                        link -> spp.setEqBand("b", i, v)
                        else -> spp.setEqBand(if (editCh == "r") "r" else "l", i, v)
                    }
                }
            )
        }
        Text(
            "Seret slider untuk mengubah (1 dB/langkah) · ketuk dua kali = 0 dB · geser panel ke samping untuk band lain",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

private const val EQ_BANDS = 15
private val CutBlue = Color(0xFF64B5F6)

private fun fmtFreq(freq: Int): String = when {
    freq >= 1000 && freq % 1000 == 0 -> "${freq / 1000}k"
    freq >= 1000 -> "${freq / 1000}.${(freq % 1000) / 100}k"
    else -> "$freq"
}

private fun gainColor(g: Float): Color = when {
    g > 0.01f -> WarnAmber
    g < -0.01f -> CutBlue
    else -> Color.White.copy(alpha = 0.55f)
}

private fun yToDb(y: Float, h: Float, pad: Float): Float =
    (15f - ((y - pad) / (h - 2 * pad)) * 30f).coerceIn(-15f, 15f).roundToInt().toFloat()

@Composable
private fun EqPanel(
    live: SnapshotStateList<Float>,
    other: List<Float>?,
    lockedBands: Int,
    onSend: (Int, Float) -> Unit,
) {
    // Layar pendek (landscape): panel dipadatkan agar kurva + slider muat tanpa banyak scroll vertikal.
    val compact = LocalConfiguration.current.screenHeightDp < 500
    val curveH = if (compact) 80.dp else 110.dp
    val valH = 24.dp
    val sliderH = if (compact) 130.dp else 190.dp
    val freqH = 20.dp
    val axisW = 30.dp
    // Pembatas kirim per band: [waktu terakhir, nilai bulat terakhir]; maks. tiap 120 ms, nilai akhir saat dilepas.
    val sent = remember { Array(EQ_BANDS) { longArrayOf(0L, Long.MIN_VALUE) } }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth - axisW
        val colW = if (avail / EQ_BANDS > 38.dp) avail / EQ_BANDS else 38.dp
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(axisW)) {
                AxisLabels(curveH, 8.dp)
                Spacer(Modifier.height(valH))
                AxisLabels(sliderH, 10.dp)
                Spacer(Modifier.height(freqH))
            }
            Box(Modifier.width(avail).horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(colW * EQ_BANDS)) {
                    EqCurveAligned(live, other, WarnAmber, Modifier.fillMaxWidth().height(curveH))
                    Row {
                        AmpState.BAND_FREQS.forEachIndexed { i, freq ->
                            val locked = i < lockedBands
                            val g = live.getOrElse(i) { 0f }
                            Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.height(valH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "%+.1f".format(Locale.US, g.roundToInt().toFloat()),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        color = if (locked) Color.Gray else gainColor(g)
                                    )
                                }
                                VGainSlider(
                                    value = g,
                                    enabled = !locked,
                                    accent = gainColor(g),
                                    onChange = { v ->
                                        live[i] = v
                                        val iv = v.roundToInt().toLong()
                                        val now = System.currentTimeMillis()
                                        val s = sent[i]
                                        if (iv != s[1] && now - s[0] >= 120L) {
                                            s[0] = now; s[1] = iv
                                            onSend(i, v)
                                        }
                                    },
                                    onFinish = {
                                        val s = sent[i]
                                        if (live[i].roundToInt().toLong() != s[1]) onSend(i, live[i])
                                        s[1] = Long.MIN_VALUE
                                    },
                                    modifier = Modifier.width(colW).height(sliderH)
                                )
                                Box(Modifier.height(freqH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        fmtFreq(freq),
                                        fontSize = 10.sp,
                                        color = if (locked) Color.Gray else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisLabels(height: Dp, pad: Dp) {
    Box(Modifier.height(height).fillMaxWidth().padding(vertical = pad)) {
        val c = Color.White.copy(alpha = 0.4f)
        Text("+15", fontSize = 9.sp, color = c, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
        Text("0", fontSize = 9.sp, color = c, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
        Text("−15", fontSize = 9.sp, color = c, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp))
    }
}

/** Slider vertikal −15..+15 dB (langkah 1 dB). Seret = ubah, ketuk = set, ketuk dua kali = 0 dB. */
@Composable
private fun VGainSlider(
    value: Float,
    enabled: Boolean,
    accent: Color,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    Canvas(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 10.dp.toPx()
                detectDragGestures(
                    onDragStart = { change(yToDb(it.y, size.height.toFloat(), pad)) },
                    onDrag = { c, _ -> c.consume(); change(yToDb(c.position.y, size.height.toFloat(), pad)) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 10.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { change(0f); finish() },
                    onTap = { change(yToDb(it.y, size.height.toFloat(), pad)); finish() },
                )
            }
    ) {
        val pad = 10.dp.toPx()
        val cx = size.width / 2f
        val top = pad
        val bot = size.height - pad
        val mid = (top + bot) / 2f
        fun yOf(v: Float) = top + (15f - v.coerceIn(-15f, 15f)) / 30f * (bot - top)
        val a = if (enabled) 1f else 0.35f
        drawLine(Color.White.copy(alpha = 0.10f), Offset(cx, top), Offset(cx, bot), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(cx - 9.dp.toPx(), mid), Offset(cx + 9.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
        val ty = yOf(value)
        if (value != 0f) {
            drawLine(accent.copy(alpha = a), Offset(cx, mid), Offset(cx, ty), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Color(0xFFE2E8F0).copy(alpha = a), radius = 8.dp.toPx(), center = Offset(cx, ty))
        drawLine(Color(0xFF0B1220).copy(alpha = a), Offset(cx - 4.dp.toPx(), ty), Offset(cx + 4.dp.toPx(), ty), strokeWidth = 1.5.dp.toPx())
    }
}

/** Kurva respons: titik ke-i tepat di tengah kolom slider ke-i (sumbu X per band, bukan log-Hz). */
@Composable
private fun EqCurveAligned(main: List<Float>, other: List<Float>?, mainColor: Color, modifier: Modifier = Modifier) {
    val pink = Color(0xFFFF80AB)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val sy = (h / 2f - 8.dp.toPx()) / 15f
        val colW = w / EQ_BANDS
        fun x(i: Int) = (i + 0.5f) * colW
        fun y(g: Float) = mid - g.coerceIn(-15f, 15f) * sy
        for (db in -15..15 step 5) {
            drawLine(Color.White.copy(alpha = if (db == 0) 0.25f else 0.07f), Offset(0f, y(db.toFloat())), Offset(w, y(db.toFloat())), 1.dp.toPx())
        }
        for (i in 0 until EQ_BANDS) drawLine(Color.White.copy(alpha = 0.05f), Offset(x(i), 0f), Offset(x(i), h), 1.dp.toPx())

        fun smooth(g: List<Float>): Path {
            val p = Path()
            val m = min(g.size, EQ_BANDS)
            if (m == 0) return p
            p.moveTo(x(0), y(g[0]))
            for (i in 1 until m) {
                val cx = (x(i - 1) + x(i)) / 2f
                p.cubicTo(cx, y(g[i - 1]), cx, y(g[i]), x(i), y(g[i]))
            }
            return p
        }

        val n = min(main.size, EQ_BANDS)
        if (n > 1) {
            val fill = Path().apply {
                addPath(smooth(main))
                lineTo(x(n - 1), mid)
                lineTo(x(0), mid)
                close()
            }
            drawPath(fill, mainColor.copy(alpha = 0.15f))
        }
        drawPath(smooth(main), mainColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0 until n) drawCircle(mainColor, radius = 3.dp.toPx(), center = Offset(x(i), y(main[i])))
        other?.let { drawPath(smooth(it), pink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)) }
    }
}

/** Ikon kurva mini untuk chip preset. */
@Composable
private fun MiniCurve(gains: List<Float>, color: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val n = min(gains.size, EQ_BANDS)
        if (n < 2) return@Canvas
        val path = Path()
        for (i in 0 until n) {
            val x = i / (n - 1f) * size.width
            val y = size.height / 2f - gains[i].coerceIn(-8f, 8f) / 8f * (size.height / 2f - 1.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Log-frequency EQ response curve (Axton-style visual).
 * Approximates piecewise-linear response through the 15 band gains.
 */
@Composable
fun EqCurve(
    gainsL: List<Float>,
    gainsR: List<Float>,
    linked: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = Color(0xFFFF80AB)
    val freqs = AmpState.BAND_FREQS

    // Precompute log positions 0..1
    val logPos = remember {
        val minL = ln(freqs.first().toFloat())
        val maxL = ln(freqs.last().toFloat())
        freqs.map { ((ln(it.toFloat()) - minL) / (maxL - minL)).coerceIn(0f, 1f) }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val scaleY = h / 2f / 15f // ±15 dB

        // Grid: 0 dB + ± lines
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, midY - 7.5f * scaleY), Offset(w, midY - 7.5f * scaleY), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, midY + 7.5f * scaleY), Offset(w, midY + 7.5f * scaleY), 1.dp.toPx())

        // Vertical decade marks (approx at band centers)
        logPos.forEach { p ->
            val x = p * w
            drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
        }

        fun buildPath(gains: List<Float>): Path {
            val path = Path()
            val n = min(gains.size, logPos.size)
            for (i in 0 until n) {
                val x = logPos[i] * w
                val y = midY - gains[i].coerceIn(-15f, 15f) * scaleY
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        val pathL = buildPath(gainsL)
        val nFill = min(gainsL.size, logPos.size)
        if (nFill > 1) {
            val fill = Path().apply {
                addPath(pathL)
                lineTo(logPos[nFill - 1] * w, h)
                lineTo(logPos[0] * w, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(primary.copy(alpha = 0.22f), Color.Transparent)))
        }
        drawPath(pathL, primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Dots on bands
        val n = min(gainsL.size, logPos.size)
        for (i in 0 until n) {
            val x = logPos[i] * w
            val y = midY - gainsL[i].coerceIn(-15f, 15f) * scaleY
            drawCircle(primary, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        if (!linked) {
            val pathR = buildPath(gainsR)
            drawPath(pathR, secondary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
