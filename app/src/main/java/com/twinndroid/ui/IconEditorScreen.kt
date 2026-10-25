package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinndroid.data.IconAdjust
import com.twinndroid.data.IconMode

internal val BADGE_COLORS = listOf(
    0xFF7C4DFF, 0xFF2EE6B6, 0xFFFF4D8D, 0xFFFF9F1C, 0xFF3D8BFF, 0xFFE53935, 0xFF43A047, 0xFF212121,
)

private val TINTS = listOf(0xFF7C4DFF, 0xFF2962FF, 0xFF00BFA5, 0xFF43A047, 0xFFFFC107, 0xFFFF6F00, 0xFFE53935, 0xFFEC407A, 0xFF000000, 0xFFFFFFFF)

/** Icon customization, with the live preview pinned at the top so it stays visible while editing. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconEditorScreen(vm: MainViewModel) {
    val state = vm.editor ?: return
    val c = state.config
    var customTint by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCustomIcon(uri)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.icon)) },
                navigationIcon = { IconButton(onClick = { vm.screen = Screen.Editor }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned preview — always visible above the controls.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { state.preview?.let { Image(it, null, Modifier.size(80.dp)) } }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(c.label.ifBlank { stringResource(R.string.unnamed) }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (c.iconMode) {
                                IconMode.ORIGINAL -> stringResource(R.string.icon_original)
                                IconMode.BADGE -> stringResource(R.string.badge) + " · " + c.badgeText.ifBlank { "—" }
                                IconMode.CUSTOM -> stringResource(R.string.icon_custom)
                            } + if (!c.iconAdjust.isIdentity) " · " + stringResource(R.string.adjusted) else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconPanel(stringResource(R.string.style)) {
                    val modes = listOf(IconMode.ORIGINAL to stringResource(R.string.original), IconMode.BADGE to stringResource(R.string.badge), IconMode.CUSTOM to stringResource(R.string.custom))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = c.iconMode == mode,
                                onClick = {
                                    if (mode == IconMode.CUSTOM && c.customIconPath == null) {
                                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        vm.updateConfig { it.copy(iconMode = mode) }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                    when (c.iconMode) {
                        IconMode.BADGE -> {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.badge_colour), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                BADGE_COLORS.forEach { color ->
                                    val selected = c.badgeColor == color
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(Color(color))
                                            .border(
                                                if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Color.Transparent),
                                                CircleShape,
                                            )
                                            .clickable { vm.updateConfig { it.copy(badgeColor = color) } },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = c.badgeText,
                                onValueChange = { v -> vm.updateConfig { it.copy(badgeText = v.take(2)) } },
                                label = { Text(stringResource(R.string.badge_text)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconMode.CUSTOM -> {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.choose_image)) }
                        }
                        IconMode.ORIGINAL -> Unit
                    }
                }

                IconPanel(stringResource(R.string.colour_adjust)) {
                    val a = c.iconAdjust
                    fun set(x: IconAdjust) = vm.updateConfig { it.copy(iconAdjust = x) }
                    AdjustSlider(stringResource(R.string.hue), "${a.hue.toInt()}°", a.hue, -180f..180f) { set(a.copy(hue = it)) }
                    AdjustSlider(stringResource(R.string.saturation), "${(a.saturation * 100).toInt()}%", a.saturation, 0f..2f) { set(a.copy(saturation = it)) }
                    AdjustSlider(stringResource(R.string.brightness), "${(a.brightness * 100).toInt()}", a.brightness, -0.6f..0.6f) { set(a.copy(brightness = it)) }
                    AdjustSlider(stringResource(R.string.contrast), "${(a.contrast * 100).toInt()}", a.contrast, -0.5f..0.5f) { set(a.copy(contrast = it)) }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.tint), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .border(if (a.tint == null) 3.dp else 1.dp, if (a.tint == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { set(a.copy(tint = null)) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Block, stringResource(R.string.no_tint), Modifier.size(18.dp)) }
                        TINTS.forEach { tnt ->
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(tnt))
                                    .border(if (a.tint == tnt) 3.dp else 1.dp, if (a.tint == tnt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable { set(a.copy(tint = tnt)) },
                            )
                        }
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { customTint = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Colorize, stringResource(R.string.custom_colour), Modifier.size(18.dp)) }
                    }
                    AnimatedVisibility(a.tint != null) {
                        AdjustSlider(stringResource(R.string.tint_strength), "${(a.tintStrength * 100).toInt()}%", a.tintStrength, 0.1f..1f) { set(a.copy(tintStrength = it)) }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = a.invert, onClick = { set(a.copy(invert = !a.invert)) }, label = { Text(stringResource(R.string.invert)) })
                        FilterChip(selected = a.flip, onClick = { set(a.copy(flip = !a.flip)) }, label = { Text(stringResource(R.string.flip)) })
                        FilterChip(
                            selected = a.rotation != 0,
                            onClick = { set(a.copy(rotation = (a.rotation + 90) % 360)) },
                            label = { Text(stringResource(R.string.rotate) + if (a.rotation == 0) "" else " ${a.rotation}°") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (!a.isIdentity) TextButton(onClick = { set(IconAdjust()) }) { Text(stringResource(R.string.reset_adjust)) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 40.dp), color = Color.Transparent)
            }
        }
    }

    if (customTint) {
        ColorPickerDialog(
            title = stringResource(R.string.tint_colour),
            initial = c.iconAdjust.tint ?: 0xFF7C4DFF,
            onDismiss = { customTint = false },
            onApply = { v -> vm.updateConfig { it.copy(iconAdjust = it.iconAdjust.copy(tint = v)) }; customTint = false },
        )
    }
}

@Composable
private fun IconPanel(title: String, content: @Composable () -> Unit) {
    TwinnCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun AdjustSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = v, onValueChange = onChange, valueRange = range)
    }
}
