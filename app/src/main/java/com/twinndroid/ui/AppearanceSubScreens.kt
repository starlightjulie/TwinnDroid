package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twinndroid.ui.theme.BgAnimation
import com.twinndroid.ui.theme.HeaderSettings
import com.twinndroid.ui.theme.HeaderStyle
import com.twinndroid.ui.theme.ThemeRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding -> content(Modifier.padding(padding).consumeWindowInsets(padding)) }
}

/** Appearance → Background & effects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository.get(context) }
    val cfg by repo.config.collectAsState()
    SubScreen(stringResource(R.string.bg_effects), onBack = { vm.screen = Screen.Appearance }) { mod ->
        Column(
            mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.bg_desc) + " " + stringResource(R.string.inspired_morphe),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.FilledTonalButton(onClick = { vm.peek = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Visibility, null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.preview_bg))
            }
            TwinnCard {
                Column(Modifier.padding(16.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        BgAnimation.entries.forEach { anim ->
                            val selected = cfg.background == anim
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                    )
                                    .border(
                                        if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        RoundedCornerShape(18.dp),
                                    )
                                    .clickable { repo.update { it.copy(background = anim) } }
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    bgIcon(anim),
                                    null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(anim.label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
            TwinnCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleLine(stringResource(R.string.parallax), stringResource(R.string.parallax_desc), cfg.parallax) { v ->
                        repo.update { it.copy(parallax = v) }
                    }
                }
            }
        }
    }
}

/** Appearance → Home header: the Clones tab title card and its pills, with a live preview. */
@Composable
fun HeaderSettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val repo = remember { ThemeRepository.get(context) }
    val cfg by repo.config.collectAsState()
    val h = cfg.header
    fun set(f: (HeaderSettings) -> HeaderSettings) = repo.update { it.copy(header = f(it.header)) }

    SubScreen(stringResource(R.string.home_header), onBack = { vm.screen = Screen.Appearance }) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            HomeHeader(count = vm.clones.size, updates = 0, onAppearance = {}, onSettings = {}, settings = h)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(stringResource(R.string.card)) {
                    val styles = HeaderStyle.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        styles.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = h.style == s,
                                onClick = { set { it.copy(style = s) } },
                                shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                            ) { Text(stringResource(s.label), maxLines = 1) }
                        }
                    }
                    AnimatedVisibility(h.style != HeaderStyle.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            LabeledSlider(stringResource(R.string.opacity), "${(h.opacity * 100).toInt()}%", h.opacity, 0f..1f) { v -> set { it.copy(opacity = v) } }
                            LabeledSlider(stringResource(R.string.blur), "${h.blur.toInt()} dp", h.blur, 0f..40f) { v -> set { it.copy(blur = v) } }
                            if (Build.VERSION.SDK_INT < 31) Hint(stringResource(R.string.blur_needs_12))
                            else if (h.opacity >= 0.999f && h.style != HeaderStyle.GLASS) Hint(stringResource(R.string.blur_hint))
                        }
                    }
                    LabeledSlider(stringResource(R.string.roundness), "${h.radius.toInt()} dp", h.radius, 0f..48f) { v -> set { it.copy(radius = v) } }
                    ToggleLine(stringResource(R.string.floating_card), stringResource(R.string.floating_card_desc), h.floating) { v -> set { it.copy(floating = v) } }
                }
                Panel(stringResource(R.string.pills)) {
                    ToggleLine(
                        stringResource(R.string.pills_toggle),
                        stringResource(R.string.pills_toggle_desc),
                        h.pills,
                    ) { v -> set { it.copy(pills = v) } }
                    AnimatedVisibility(h.pills, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            LabeledSlider(stringResource(R.string.pill_opacity), "${(h.pillOpacity * 100).toInt()}%", h.pillOpacity, 0f..0.9f) { v -> set { it.copy(pillOpacity = v) } }
                            LabeledSlider(stringResource(R.string.pill_blur), "${h.pillBlur.toInt()} dp", h.pillBlur, 0f..30f) { v -> set { it.copy(pillBlur = v) } }
                            LabeledSlider(stringResource(R.string.pill_roundness), "${(h.pillRadius * 2).toInt()}%", h.pillRadius, 0f..50f) { v -> set { it.copy(pillRadius = v) } }
                        }
                    }
                }
                TextButton(onClick = { set { HeaderSettings() } }) { Text(stringResource(R.string.reset_header)) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = v, onValueChange = onChange, valueRange = range)
    }
}
