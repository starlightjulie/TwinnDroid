package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

private val QUICK_SWATCHES = listOf(
    0xFF000000, 0xFF121212, 0xFF1E1450, 0xFF0F0D1A, 0xFFFFFFFF, 0xFFF5F5F5,
    0xFF7C4DFF, 0xFFB9A3FF, 0xFF2962FF, 0xFF4FC3F7, 0xFF00BFA5, 0xFF2EE6B6,
    0xFF43A047, 0xFFDCE775, 0xFFFFC107, 0xFFFF6F00, 0xFFE53935, 0xFFEC407A,
)

fun formatHex(argb: Long): String = "%06X".format(argb and 0xFFFFFF)

/** Accepts RRGGBB, #RRGGBB, RGB or AARRGGBB (alpha is dropped: theme colours are opaque). */
fun parseHex(text: String): Long? {
    var h = text.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    if (h.length == 8) h = h.substring(2)
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { 0xFF000000 or it }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Long, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    val start = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(formatHex(initial)) }
    var hexError by remember { mutableStateOf(false) }

    val argb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFF
    fun syncHex() { hex = formatHex(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)).toLong()); hexError = false }
    fun setColor(c: Long) {
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(c.toInt(), it) }
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hex = formatHex(c); hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val preview = Color(argb)
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(preview)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "#${formatHex(argb)}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (preview.luminance() > 0.45f) Color.Black else Color.White,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.uppercase().filter { it.isLetterOrDigit() || it == '#' }.take(9)
                        val parsed = parseHex(hex)
                        hexError = parsed == null
                        if (parsed != null) {
                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed.toInt(), it) }
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    label = { Text(stringResource(R.string.hex_code)) },
                    prefix = { Text("#") },
                    isError = hexError,
                    supportingText = { if (hexError) Text(stringResource(R.string.hex_error)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                GradientSlider(
                    stringResource(R.string.hue), "${hue.toInt()}°", hue, 0f..360f,
                    Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                ) { hue = it; syncHex() }
                GradientSlider(
                    stringResource(R.string.saturation), "${(sat * 100).toInt()}%", sat, 0f..1f,
                    Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
                ) { sat = it; syncHex() }
                GradientSlider(
                    stringResource(R.string.brightness), "${(value * 100).toInt()}%", value, 0f..1f,
                    Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
                ) { value = it; syncHex() }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.quick_picks), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_SWATCHES.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                                .clickable { setColor(c) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(argb) }, enabled = !hexError) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GradientSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("$label  ·  $valueText", style = MaterialTheme.typography.labelLarge)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(14.dp).clip(RoundedCornerShape(50)).background(track)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}
