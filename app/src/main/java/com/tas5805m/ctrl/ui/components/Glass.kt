package com.tas5805m.ctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kartu "kaca" mengikuti desain HTML:
 * - Gradient vertikal 0.07 → 0.03
 * - Border 1px rgba(255,255,255,0.10)
 * - Highlight line di atas (seperti ::before di HTML)
 * - Radius 20dp
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(ink.copy(alpha = 0.07f), ink.copy(alpha = 0.03f))
                ),
                shape
            )
            .border(1.dp, ink.copy(alpha = 0.10f), shape)
    ) {
        // Top highlight line (HTML ::before)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            ink.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            Modifier.padding(contentPadding),
            content = content
        )
    }
}

/** Judul kartu: huruf kapital kecil + (opsional) badge di kanan. */
@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) trailing()
    }
}

/** Lencana mono kecil (mis. "from TAS5805M", "LR4", "Mode 2.1"). */
@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        modifier
            .background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = color,
        maxLines = 1,
    )
}

/** Baris judul + keterangan + Switch (seperti baris toggle pada prototipe). */
@Composable
fun SwitchRow(
    title: String,
    sub: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Jarak antar bagian di dalam kartu. */
@Composable
fun GlassGap(h: Int = 12) = Spacer(Modifier.height(h.dp))

/** Label section di luar kartu (HTML .section-label). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = Color(0xFF64748B),
    )
}

/**
 * Metric cell (HTML .metric) — untuk baris Uptime / PVDD / Dropped / Latency.
 */
@Composable
fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Mirip HTML .metric — kartu gelap, label kecil uppercase, nilai mono tebal
    val shape = RoundedCornerShape(14.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(shape)
            .background(ink.copy(alpha = 0.04f), shape)
            .border(1.dp, ink.copy(alpha = 0.08f), shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = Color(0xFF64748B),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            letterSpacing = (-0.3).sp,
        )
    }
}

/** Warna ListItem transparan agar tampil di atas [GlassCard] (default ListItem opak). */
@Composable
fun glassListItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
