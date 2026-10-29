// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.heatmap.TypingHeatmap
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog

/** Number of samples a key needs before its average offset is shown */
private const val MIN_SAMPLES_FOR_AVERAGE = 15
/** Number of samples before and after enabling adaptation needed to compare them */
private const val MIN_SAMPLES_FOR_COMPARISON = 200
/** weeks with fewer touches are not shown in the history */
private const val MIN_TOUCHES_PER_WEEK = 50

@Composable
fun TypingHeatmapScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val layouts = remember(refresh) { TypingHeatmap.getLayouts(context) }
    var selectedLayoutId by remember { mutableStateOf<String?>(null) }
    var onlyMisses by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val layout = layouts.firstOrNull { it.id == selectedLayoutId } ?: layouts.firstOrNull()
    val otherDevice = remember(refresh) { TypingHeatmap.getOtherLearnedOnDevice(context) }
    val prefs = context.prefs()
    val adaptEnabled = prefs.getBoolean(Settings.PREF_ADAPT_TO_TYPING, Defaults.PREF_ADAPT_TO_TYPING)
    val strength = TypingHeatmap.DEFAULT_STRENGTH

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.typing_heatmap_title),
        settings = emptyList(),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (otherDevice != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.typing_heatmap_other_device, otherDevice, TypingHeatmap.currentDevice),
                            style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { TypingHeatmap.clear(context); refresh++ }) {
                                Text(stringResource(R.string.typing_heatmap_start_over))
                            }
                            OutlinedButton(onClick = { TypingHeatmap.acceptDataFromOtherDevice(context); refresh++ }) {
                                Text(stringResource(R.string.typing_heatmap_keep))
                            }
                        }
                    }
                }
            }
            if (layout == null) {
                InfoCard(stringResource(R.string.typing_heatmap_no_data))
                InfoCard(stringResource(R.string.typing_heatmap_privacy))
                return@Column
            }
            if (layouts.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    layouts.forEach {
                        FilterChip(
                            selected = it.id == layout.id,
                            onClick = { selectedLayoutId = it.id },
                            label = { Text(layoutLabel(it.id)) }
                        )
                    }
                }
                Text(stringResource(R.string.typing_heatmap_views), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyMisses, onClick = { onlyMisses = false },
                    label = { Text(stringResource(R.string.typing_heatmap_all_touches)) })
                FilterChip(selected = onlyMisses, onClick = { onlyMisses = true },
                    label = { Text(stringResource(R.string.typing_heatmap_only_misses)) })
            }

            val offsets = remember(layout, strength, adaptEnabled) {
                if (adaptEnabled) TypingHeatmap.getOffsets(context, layout.id, strength) else emptyMap()
            }
            HeatmapCanvas(layout, onlyMisses, offsets)

            val total = layout.sampleCount
            val misses = layout.samples.entries.sumOf { (code, list) -> list.count { it.touchedCode != code } }
            val percent = if (total == 0) 0f else misses * 100f / total
            Text(stringResource(R.string.typing_heatmap_stats, total, misses, percent), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.typing_heatmap_legend), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    !adaptEnabled -> stringResource(R.string.typing_heatmap_adapt_off)
                    offsets.isEmpty() -> stringResource(R.string.typing_heatmap_adapt_waiting, TypingHeatmap.MIN_SAMPLES_FOR_ADAPTATION)
                    else -> stringResource(R.string.typing_heatmap_adapt_on, offsets.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // miss rate before and after enabling adaptation
            val allSamples = layout.samples.entries.flatMap { (code, list) -> list.map { code to it } }
            val before = allSamples.filter { !it.second.adapted }
            val after = allSamples.filter { it.second.adapted }
            if (after.isNotEmpty()) {
                Text(stringResource(R.string.typing_heatmap_before_after), style = MaterialTheme.typography.titleSmall)
                if (before.size < MIN_SAMPLES_FOR_COMPARISON || after.size < MIN_SAMPLES_FOR_COMPARISON) {
                    Text(stringResource(R.string.typing_heatmap_before_after_not_enough, MIN_SAMPLES_FOR_COMPARISON),
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    fun rate(list: List<Pair<Int, TypingHeatmap.Sample>>) = list.count { it.first != it.second.touchedCode } * 100f / list.size
                    Text(stringResource(R.string.typing_heatmap_before_after_values, rate(before), before.size, rate(after), after.size),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            // miss rate per week, to see whether typing gets better
            val weekly = remember(refresh) {
                TypingHeatmap.getWeeklyStats(context).filter { it.touches >= MIN_TOUCHES_PER_WEEK }.takeLast(8)
            }
            if (weekly.isNotEmpty()) {
                Text(stringResource(R.string.typing_heatmap_history), style = MaterialTheme.typography.titleSmall)
                val maxRate = weekly.maxOf { it.misses * 100f / it.touches }.coerceAtLeast(1f)
                val dateFormat = remember { java.text.SimpleDateFormat("dd.MM.", java.util.Locale.getDefault()) }
                weekly.forEach { week ->
                    val rate = week.misses * 100f / week.touches
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateFormat.format(java.util.Date(week.week * 7L * 86_400_000L)),
                            Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(1f).height(10.dp)) {
                            Box(Modifier.fillMaxWidth(rate / maxRate).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
                        }
                        Text(stringResource(R.string.typing_heatmap_history_value, rate, week.touches),
                            Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            val confusions = layout.samples.entries
                .flatMap { (code, list) -> list.filter { it.touchedCode != code }.map { code to it.touchedCode } }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(8)
            if (confusions.isNotEmpty()) {
                Text(stringResource(R.string.typing_heatmap_top_misses), style = MaterialTheme.typography.titleSmall)
                confusions.forEach { (pair, count) ->
                    Text(stringResource(R.string.typing_heatmap_miss_line, cp(pair.first), cp(pair.second), count),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            InfoCard(stringResource(R.string.typing_heatmap_privacy))
            OutlinedButton(onClick = { showClearDialog = true }) {
                Text(stringResource(R.string.typing_heatmap_clear))
            }
        }
    }
    if (showClearDialog) {
        ConfirmationDialog(
            onDismissRequest = { showClearDialog = false },
            onConfirmed = {
                TypingHeatmap.clear(context)
                refresh++
            },
            content = { Text(stringResource(R.string.typing_heatmap_clear_confirm)) }
        )
    }
}

/** Readable name for a layout id like "custom.Latn.xyz. (landscape) (one-handed)", e.g. "Deutsch (Qwertz) · Querformat" */
@Composable
private fun layoutLabel(id: String): String {
    var main = id
    val modes = mutableListOf<String>()
    for ((suffix, res) in listOf(" (floating)" to R.string.typing_heatmap_view_floating,
            " (one-handed)" to R.string.typing_heatmap_view_one_handed, " (landscape)" to R.string.typing_heatmap_view_landscape)) {
        if (main.endsWith(suffix)) {
            main = main.removeSuffix(suffix)
            modes.add(0, stringResource(res))
        }
    }
    val name = (SubtypeLocaleUtils.getMainLayoutDisplayName(main) ?: main).replaceFirstChar { it.uppercase() }
    val language = remember(main) {
        SubtypeSettings.getEnabledSubtypes().firstOrNull { it.mainLayoutName() == main }
            ?.let { SubtypeLocaleUtils.getSubtypeLanguageDisplayName(it.locale()) }
    }
    val base = if (language == null || name.contains(language, ignoreCase = true)) name else "$language ($name)"
    return (listOf(base) + modes).joinToString(" · ")
}

private fun cp(code: Int) = if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else "?"

@Composable
private fun InfoCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HeatmapCanvas(layout: TypingHeatmap.LayoutData, onlyMisses: Boolean, offsets: Map<Int, FloatArray>) {
    val keyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val keyBorder = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val hitColor = Color(0xFF1E88E5)
    val missColor = Color(0xFFE53935)
    val averageColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = labelColor)
    val shapesByCode = remember(layout) { layout.shapes.associateBy { it.code } }
    val aspect = layout.aspectRatio.takeIf { it > 0.05f } ?: 0.35f
    val changedAreaColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
    // areas where adaptation picks another key than plain detection, in normalized coordinates
    val changedAreas = remember(layout, offsets) { computeChangedAreas(layout, offsets, aspect) }

    Canvas(Modifier.fillMaxWidth().aspectRatio(1f / aspect)) {
        val w = size.width
        val h = size.height
        val gap = 2.dp.toPx()
        // keys
        layout.shapes.forEach { key ->
            val topLeft = Offset(key.x * w + gap, key.y * h + gap)
            val keySize = Size(key.width * w - 2 * gap, key.height * h - 2 * gap)
            if (keySize.width <= 0 || keySize.height <= 0) return@forEach
            drawRoundRect(keyColor, topLeft, keySize, CornerRadius(6.dp.toPx()))
            drawRoundRect(keyBorder, topLeft, keySize, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
            if (key.label.isNotEmpty() && key.label.length <= 3) {
                val measured = textMeasurer.measure(key.label, labelStyle)
                drawText(measured, topLeft = Offset(
                    key.centerX * w - measured.size.width / 2f,
                    key.centerY * h - measured.size.height / 2f
                ))
            }
        }
        // areas assigned to another key by adaptation
        changedAreas.forEach { cell ->
            drawRect(changedAreaColor, Offset(cell[0] * w, cell[1] * h), Size(cell[2] * w, cell[3] * h))
        }
        // touches
        val dotRadius = 2.5.dp.toPx()
        layout.samples.forEach { (code, list) ->
            val key = shapesByCode[code] ?: return@forEach
            list.forEach { sample ->
                val miss = sample.touchedCode != code
                if (onlyMisses && !miss) return@forEach
                val x = (key.centerX + sample.dx * key.width) * w
                val y = (key.centerY + sample.dy * key.height) * h
                drawCircle(if (miss) missColor.copy(alpha = 0.8f) else hitColor.copy(alpha = 0.45f), dotRadius, Offset(x, y))
            }
            // average offset of all touches meant for this key
            if (!onlyMisses && list.size >= MIN_SAMPLES_FOR_AVERAGE) {
                val avgX = list.sumOf { it.dx.toDouble() }.toFloat() / list.size
                val avgY = list.sumOf { it.dy.toDouble() }.toFloat() / list.size
                val center = Offset(key.centerX * w, key.centerY * h)
                val avg = Offset((key.centerX + avgX * key.width) * w, (key.centerY + avgY * key.height) * h)
                drawLine(averageColor, center, avg, 2.dp.toPx())
                drawCircle(averageColor, 3.5.dp.toPx(), avg)
            }
        }
    }
}

/** Grid cells (x, y, width, height normalized) where adaptation assigns touches to another key */
private fun computeChangedAreas(layout: TypingHeatmap.LayoutData, offsets: Map<Int, FloatArray>, aspect: Float): List<FloatArray> {
    if (offsets.isEmpty()) return emptyList()
    val letters = layout.shapes.filter { Character.isLetter(it.code) && it.width > 0 && it.height > 0 }
    if (letters.size < 2) return emptyList()
    // use keyboard-proportional units, so distances are like on the real keyboard
    val candidates = letters.map { TypingHeatmap.Candidate(it.code, it.x, it.y * aspect, it.width, it.height * aspect) }
    val columns = 120
    val cellWidth = 1f / columns
    val rows = (columns * aspect).toInt().coerceAtLeast(1)
    val cellHeight = 1f / rows
    val result = ArrayList<FloatArray>()
    for (row in 0 until rows) {
        val y = (row + 0.5f) * cellHeight
        for (column in 0 until columns) {
            val x = (column + 0.5f) * cellWidth
            // only inside the letter area
            if (letters.none { x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height }) continue
            if (TypingHeatmap.adaptedKeyCode(x, y * aspect, candidates, offsets) != null)
                result.add(floatArrayOf(column * cellWidth, row * cellHeight, cellWidth, cellHeight))
        }
    }
    return result
}
