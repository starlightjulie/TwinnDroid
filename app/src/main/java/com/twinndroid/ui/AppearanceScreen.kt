package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Pentagon
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import com.twinndroid.ui.theme.BgAnimation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twinndroid.ui.theme.Aspect
import com.twinndroid.ui.theme.ThemeConfig
import com.twinndroid.ui.theme.ThemeMode
import com.twinndroid.ui.theme.ThemePresets
import com.twinndroid.ui.theme.ThemeRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository.get(context) }
    val cfg by repo.config.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val active = cfg.palette(systemDark)

    var editing by remember { mutableStateOf<Aspect?>(null) }
    var currentIcon by remember { mutableStateOf(AppIcons.current(context)) }
    var pendingIcon by remember { mutableStateOf<AppIcon?>(null) }
    var customUri by remember { mutableStateOf<Uri?>(null) }
    var customPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var shortcutLabel by remember { mutableStateOf("TwinnDroid") }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            customUri = uri
            customPreview = runCatching { AppIcons.loadAdaptive(context, uri)?.asImageBitmap() }.getOrNull()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = { IconButton(onClick = { vm.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel(stringResource(R.string.theme)) {
                val modes = listOf(ThemeMode.SYSTEM to stringResource(R.string.theme_system), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = cfg.mode == mode,
                            onClick = { repo.update { it.copy(mode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                if (cfg.custom != null) {
                    Hint(stringResource(R.string.theme_custom_hint))
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.material_you))
                            Hint(stringResource(R.string.material_you_desc))
                        }
                        Switch(checked = cfg.dynamicColor, onCheckedChange = { v -> repo.update { it.copy(dynamicColor = v) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.presets), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePresets.all.forEach { preset ->
                        val p = ThemeConfig(presetId = preset.id, mode = cfg.mode).palette(systemDark)
                        val selected = cfg.custom == null && !cfg.dynamicColor && cfg.presetId == preset.id
                        Column(
                            Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    repo.update { it.copy(presetId = preset.id, custom = null, dynamicColor = false) }
                                    if (cfg.matchIcon) AppIcons.byAlias(preset.icon)?.let { AppIcons.choose(context, it); currentIcon = it }
                                }
                                .background(Color(p.background)),
                        ) {
                            Box(Modifier.fillMaxWidth().height(26.dp).background(Brush.linearGradient(listOf(Color(p.headerStart), Color(p.headerEnd)))))
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(p.primary, p.secondary, p.tertiary, p.surface).forEach {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(it)))
                                }
                            }
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (Color(p.background).run { red + green + blue } > 1.5f) Color.Black else Color.White,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                ToggleLine(stringResource(R.string.match_icon), stringResource(R.string.match_icon_desc), cfg.matchIcon) { v ->
                    repo.update { it.copy(matchIcon = v) }
                    if (v) AppIcons.byAlias(ThemePresets.byId(cfg.presetId).icon)?.let { AppIcons.choose(context, it); currentIcon = it }
                }
            }

            Panel(stringResource(R.string.bg_effects)) {
                NavRow(
                    bgIcon(cfg.background),
                    stringResource(R.string.bg_animation),
                    stringResource(cfg.background.label) + if (cfg.parallax) " · " + stringResource(R.string.parallax) else "",
                ) { vm.screen = Screen.Background }
                NavRow(
                    Icons.Rounded.ViewDay,
                    stringResource(R.string.home_header),
                    stringResource(cfg.header.style.label) + if (cfg.header.pills) " · " + stringResource(R.string.pills) else "",
                ) { vm.screen = Screen.Header }
                ToggleLine(stringResource(R.string.pure_black), stringResource(R.string.pure_black_desc), cfg.pureBlack) { v -> repo.update { it.copy(pureBlack = v) } }
                ToggleLine(stringResource(R.string.glass_cards), stringResource(R.string.glass_cards_desc), cfg.glassCards) { v -> repo.update { it.copy(glassCards = v) } }
                ToggleLine(stringResource(R.string.outlines), stringResource(R.string.outlines_desc), cfg.outlines) { v -> repo.update { it.copy(outlines = v) } }
            }

            Panel(stringResource(R.string.custom_colours)) {
                Hint(
                    stringResource(if (cfg.dynamicColor) R.string.custom_colours_off else R.string.custom_colours_desc),
                )
                Spacer(Modifier.height(8.dp))
                Aspect.entries.forEach { aspect ->
                    val c = aspect.get(active)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = aspect }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(aspect.label))
                            Hint(stringResource(aspect.description))
                        }
                        Text("#${formatHex(c)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (cfg.custom != null) {
                    TextButton(onClick = { repo.update { it.copy(custom = null) } }) { Text(stringResource(R.string.reset_preset)) }
                }
            }

            Panel(stringResource(R.string.text_size)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = cfg.fontScale,
                        onValueChange = { v -> repo.update { it.copy(fontScale = (v * 20).toInt() / 20f) } },
                        valueRange = 0.85f..1.4f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge)
                }
                Hint(stringResource(R.string.text_size_hint, (cfg.fontScale * 100).toInt()))
            }

            Panel(stringResource(R.string.app_icon)) {
                Hint(stringResource(R.string.app_icon_desc))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcons.all.forEach { icon ->
                        val selected = icon === currentIcon
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            Box(
                                Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(icon.bgStart, icon.bgEnd)))
                                    .border(
                                        if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                                        RoundedCornerShape(18.dp),
                                    )
                                    .clickable { if (!selected) pendingIcon = icon },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(icon.foreground), null, Modifier.size(60.dp))
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                            .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                            Text(icon.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.own_image), style = MaterialTheme.typography.labelLarge)
                Hint(
                    stringResource(R.string.own_image_desc),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val img = customPreview
                        if (img != null) Image(img, null, Modifier.size(60.dp))
                        else Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.choose_image_short))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = shortcutLabel,
                        onValueChange = { shortcutLabel = it.take(24) },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(if (customUri == null) R.string.choose_image_short else R.string.change_image))
                    }
                    Button(
                        enabled = customUri != null,
                        onClick = {
                            val ok = runCatching { AppIcons.pinCustomShortcut(context, customUri!!, shortcutLabel) }.getOrDefault(false)
                            if (!ok) vm.message = context.getString(R.string.shortcut_unsupported)
                        },
                    ) { Text(stringResource(R.string.add_home)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { aspect ->
        ColorPickerDialog(
            title = stringResource(aspect.label),
            initial = aspect.get(active),
            onDismiss = { editing = null },
            onApply = { v ->
                repo.update { c -> c.copy(custom = aspect.set(c.custom ?: active, v), dynamicColor = false) }
                editing = null
            },
        )
    }

    pendingIcon?.let { icon ->
        AlertDialog(
            onDismissRequest = { pendingIcon = null },
            title = { Text(stringResource(R.string.switch_icon_title, icon.name)) },
            text = {
                Text(
                    stringResource(R.string.switch_icon_desc),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppIcons.choose(context, icon)
                    currentIcon = icon
                    pendingIcon = null
                }) { Text(stringResource(R.string.switch_)) }
            },
            dismissButton = { TextButton(onClick = { pendingIcon = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun Panel(title: String, content: @Composable () -> Unit) {
    TwinnCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ToggleLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Hint(value)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
    }
}

internal fun bgIcon(anim: BgAnimation) = when (anim) {
    BgAnimation.SPACE -> Icons.Rounded.AutoAwesome
    BgAnimation.PARTICLES -> Icons.Rounded.BubbleChart
    BgAnimation.CIRCLES -> Icons.Rounded.RadioButtonUnchecked
    BgAnimation.RINGS -> Icons.Rounded.TripOrigin
    BgAnimation.MESH -> Icons.Rounded.Gradient
    BgAnimation.SHAPES -> Icons.Rounded.Pentagon
    BgAnimation.SNOW -> Icons.Rounded.AcUnit
    BgAnimation.GRID -> Icons.Rounded.Apps
    BgAnimation.LAVA -> Icons.Rounded.WaterDrop
    BgAnimation.AURORA -> Icons.Rounded.NightsStay
    BgAnimation.WAVES -> Icons.Rounded.Waves
    BgAnimation.FIREFLIES -> Icons.Rounded.EmojiObjects
    BgAnimation.BOKEH -> Icons.Rounded.Lens
    BgAnimation.METEORS -> Icons.Rounded.Flare
    BgAnimation.NONE -> Icons.Rounded.VisibilityOff
    BgAnimation.RANDOM -> Icons.Rounded.Shuffle
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
