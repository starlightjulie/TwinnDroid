package com.twinndroid.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.twinndroid.R
import com.twinndroid.ui.theme.Aspect
import com.twinndroid.ui.theme.Palette
import com.twinndroid.ui.theme.SavedTheme
import com.twinndroid.ui.theme.ThemeRepository

/**
 * Build your own theme: colours, background, text size and home header, each in a fold-out
 * section, then save it by name and switch between saved themes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyThemesScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository.get(context) }
    val cfg by repo.config.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val shown = shownColours(cfg.palette(systemDark), cfg.dynamicColor)

    var editing by remember { mutableStateOf<Aspect?>(null) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedTheme?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.my_themes)) },
                navigationIcon = { IconButton(onClick = { vm.screen = Screen.Appearance }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TwinnCard {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Fold(stringResource(R.string.custom_colours), initiallyOpen = true) {
                        ToggleLine(stringResource(R.string.use_my_colours), stringResource(R.string.use_my_colours_hint), cfg.custom != null) { on ->
                            repo.update { it.copy(custom = if (on) shown else null, dynamicColor = false) }
                        }
                        cfg.custom?.let { own ->
                            Hint(stringResource(R.string.custom_colours_desc))
                            Spacer(Modifier.height(8.dp))
                            Aspect.entries.forEach { aspect ->
                                ColourRow(aspect, aspect.get(own)) { editing = aspect }
                            }
                        }
                    }
                    Fold(stringResource(R.string.bg_effects)) { BackgroundSettings(vm) }
                    Fold(stringResource(R.string.text_size)) {
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
                    Fold(stringResource(R.string.home_header), divider = false) { HomeHeaderSettings(vm) }
                    ButtonRow(Modifier.padding(vertical = 12.dp)) {
                        Button(onClick = { saving = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            OneLine(stringResource(R.string.save_set))
                        }
                    }
                }
            }

            Panel(stringResource(R.string.saved_sets)) {
                if (cfg.savedThemes.isEmpty()) {
                    Hint(stringResource(R.string.no_sets))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cfg.savedThemes.forEach { theme ->
                            PaletteTile(
                                theme.name,
                                theme.look.palette(systemDark),
                                selected = cfg.lookOnly() == theme.look,
                                width = 140.dp,
                                onDelete = { deleting = theme },
                            ) { repo.update { it.usingTheme(theme) } }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint(stringResource(R.string.sets_hint))
                }
            }
        }
    }

    editing?.let { aspect ->
        ColorPickerDialog(
            title = stringResource(aspect.label),
            initial = aspect.get(cfg.custom ?: shown),
            onDismiss = { editing = null },
            onApply = { v ->
                repo.update { c -> c.copy(custom = aspect.set(c.custom ?: shown, v), dynamicColor = false) }
                editing = null
            },
        )
    }

    if (saving) {
        val suggested = stringResource(R.string.set_default_name, cfg.savedThemes.size + 1)
        var name by remember { mutableStateOf(suggested) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.save_set)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = name.trim().ifEmpty { suggested }
                    repo.update { it.savingTheme(chosen, shown) }
                    vm.message = context.getString(R.string.set_saved, chosen)
                    saving = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_q)) },
            text = { Text(stringResource(R.string.delete_set_confirm, theme.name)) },
            confirmButton = {
                TextButton(onClick = {
                    repo.update { c -> c.copy(savedThemes = c.savedThemes - theme) }
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The colours really on screen: with Material You on, those come from the wallpaper, not the preset. */
@Composable
private fun shownColours(palette: Palette, dynamicColor: Boolean): Palette {
    if (!dynamicColor || Build.VERSION.SDK_INT < 31) return palette
    val scheme = MaterialTheme.colorScheme
    fun Color.argb() = toArgb().toLong() and 0xFFFFFFFFL
    return palette.copy(
        primary = scheme.primary.argb(),
        secondary = scheme.secondary.argb(),
        tertiary = scheme.tertiary.argb(),
        background = scheme.background.argb(),
        surface = scheme.surface.argb(),
    )
}

@Composable
private fun ColourRow(aspect: Aspect, colour: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(colour))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(aspect.label))
            Hint(stringResource(aspect.description))
        }
        Text("#${formatHex(colour)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

/** A section that folds open and shut under its title. */
@Composable
private fun Fold(title: String, initiallyOpen: Boolean = false, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "foldChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 12.dp), content = content)
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
