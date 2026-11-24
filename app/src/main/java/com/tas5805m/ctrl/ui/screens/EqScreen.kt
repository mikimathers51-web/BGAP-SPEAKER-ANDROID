package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hubungkan perangkat dulu", color = Color.Gray)
        }
        return
    }

    Column(modifier.fillMaxSize()) {
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

        // —— Frequency response curve (hero) ——
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .height(160.dp),
            contentPadding = 0.dp
        ) {
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                EqCurve(
                    gainsL = src,
                    gainsR = if (linkEff) src else state.eqRight,
                    linked = linkEff,
                    modifier = Modifier.fillMaxSize()
                )
                // dB labels
                Column(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) {
                    listOf("+15", "0", "−15").forEach {
                        Text(it, fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
                        if (it != "−15") Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Quick shapes
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val shapes = listOf(
                "Flat" to List(15) { 0f },
                "Bass+" to listOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                "V-Shape" to listOf(4f, 3f, 1f, 0f, -1f, -2f, -1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f),
                "Vocal" to listOf(-2f, -1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f, -1f, 0f, 0f, 0f),
                "Treble+" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f, 2f),
            )
            shapes.forEach { (name, g) ->
                AssistChip(
                    onClick = {
                        g.forEachIndexed { i, v ->
                            if (!(state.xoverLocksEq && i <= 2)) {
                                when (tgt) {
                                    "CTR" -> spp.setEqChip2("c", i, v)
                                    "SUB" -> spp.setEqChip2("s", i, v)
                                    else -> spp.setEqBand("b", i, v)
                                }
                            }
                        }
                    },
                    label = { Text(name, fontSize = 12.sp) }
                )
            }
        }

        // Band sliders
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(AmpState.BAND_FREQS) { i, freq ->
                val locked = state.xoverLocksEq && i <= 2
                val gL = src.getOrElse(i) { 0f }
                val gR = state.eqRight.getOrElse(i) { 0f }
                var localL by remember(gL, tgt) { mutableFloatStateOf(gL) }
                var localR by remember(gR) { mutableFloatStateOf(gR) }

                Card(
                    colors = if (locked)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    else CardDefaults.cardColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        val freqLabel = when {
                            freq >= 1000 && freq % 1000 == 0 -> "${freq / 1000}k"
                            freq >= 1000 -> "${freq / 1000}.${(freq % 1000) / 100}k"
                            else -> "$freq"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                freqLabel,
                                modifier = Modifier.width(40.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (locked) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (linkEff) "${localL.roundToInt()} dB"
                                else "L ${localL.roundToInt()}  R ${localR.roundToInt()}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            if (locked) Text("  🔒", fontSize = 11.sp)
                        }
                        if (!locked) {
                            Slider(
                                value = localL,
                                onValueChange = {
                                    localL = it
                                    if (linkEff) localR = it
                                },
                                onValueChangeFinished = {
                                    when {
                                        tgt == "CTR" -> spp.setEqChip2("c", i, localL)
                                        tgt == "SUB" -> spp.setEqChip2("s", i, localL)
                                        link -> spp.setEqBand("b", i, localL)
                                        else -> spp.setEqBand("l", i, localL)
                                    }
                                },
                                // firmware: gain EQ integer (sscanf %d) -> langkah 1 dB, bukan 0,5 dB
                                valueRange = -15f..15f,
                                steps = 29,
                                modifier = Modifier.height(28.dp)
                            )
                            if (!linkEff) {
                                Slider(
                                    value = localR,
                                    onValueChange = { localR = it },
                                    onValueChangeFinished = { spp.setEqBand("r", i, localR) },
                                    valueRange = -15f..15f,
                                    steps = 29,
                                    modifier = Modifier.height(28.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFF80AB),
                                        activeTrackColor = Color(0xFFFF80AB)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
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
