package com.twinndroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.twinndroid.R
import com.twinndroid.ui.theme.Frosted
import com.twinndroid.ui.theme.HeaderSettings
import com.twinndroid.ui.theme.HeaderStyle
import com.twinndroid.ui.theme.LocalTwinnExtras

/** The big title card on the Clones tab, drawn according to Appearance → Home header. */
@Composable
fun HomeHeader(
    count: Int,
    updates: Int,
    onAppearance: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settings: HeaderSettings = LocalTwinnExtras.current.header,
) {
    val extras = LocalTwinnExtras.current
    val scheme = MaterialTheme.colorScheme
    val hs = settings
    val onGradient = hs.style == HeaderStyle.GRADIENT
    val contentColor = if (onGradient) Color.White else scheme.onBackground
    val shape: Shape = if (hs.floating) RoundedCornerShape(hs.radius.dp) else RoundedCornerShape(bottomStart = hs.radius.dp, bottomEnd = hs.radius.dp)
    // Glass is always see-through; the other styles only once their opacity is lowered.
    val blur = when {
        hs.style == HeaderStyle.NONE -> 0.dp
        hs.style == HeaderStyle.GLASS || hs.opacity < 0.999f -> hs.blur.dp
        else -> 0.dp
    }

    Frosted(
        modifier = modifier
            .then(if (hs.floating) Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp) else Modifier)
            .fillMaxWidth()
            .then(
                if (extras.outlines && hs.style != HeaderStyle.NONE && hs.style != HeaderStyle.GRADIENT) {
                    Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), shape)
                } else Modifier,
            ),
        shape = shape,
        blur = blur,
    ) {
        when (hs.style) {
            HeaderStyle.GRADIENT -> Box(Modifier.matchParentSize().background(extras.headerBrush, alpha = hs.opacity))
            HeaderStyle.GLASS -> Box(Modifier.matchParentSize().background(scheme.surfaceContainer.copy(alpha = hs.opacity * 0.6f)))
            HeaderStyle.SOLID -> Box(Modifier.matchParentSize().background(scheme.surfaceContainer.copy(alpha = hs.opacity)))
            HeaderStyle.NONE -> Unit
        }
        Row(
            Modifier
                .then(if (hs.floating) Modifier else Modifier.statusBarsPadding())
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (hs.floating) 12.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPill(hs, onGradient, Modifier) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.padding(2.dp).size(56.dp))
            }
            Spacer(Modifier.width(8.dp))
            HeaderPill(hs, onGradient, Modifier) {
                Column(Modifier.padding(horizontal = if (hs.pills) 14.dp else 4.dp, vertical = if (hs.pills) 6.dp else 0.dp)) {
                    val res = androidx.compose.ui.platform.LocalContext.current.resources
                    Text("TwinnDroid", style = MaterialTheme.typography.headlineMedium, color = contentColor)
                    Text(
                        buildString {
                            append(res.getQuantityString(R.plurals.clone_count, count, count))
                            if (updates > 0) append(" · " + res.getQuantityString(R.plurals.update_count, updates, updates))
                        },
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HeaderPill(hs, onGradient, Modifier) {
                IconButton(onClick = onAppearance) { Icon(Icons.Rounded.Palette, stringResource(R.string.appearance), tint = contentColor) }
            }
            Spacer(Modifier.width(6.dp))
            HeaderPill(hs, onGradient, Modifier) {
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = contentColor) }
            }
        }
    }
}

/** Frosted pill behind a header element, so it stays readable over any background. */
@Composable
private fun HeaderPill(hs: HeaderSettings, onGradient: Boolean, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    if (!hs.pills) {
        Box(modifier, content = content)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = hs.pillRadius.toInt().coerceIn(0, 50))
    val tint = if (onGradient) Color.Black.copy(alpha = hs.pillOpacity) else scheme.surfaceContainerHighest.copy(alpha = hs.pillOpacity)
    Frosted(
        modifier = modifier.then(
            if (LocalTwinnExtras.current.outlines) {
                Modifier.border(BorderStroke(1.dp, (if (onGradient) Color.White else scheme.outline).copy(alpha = 0.25f)), shape)
            } else Modifier,
        ),
        shape = shape,
        tint = tint,
        blur = hs.pillBlur.dp,
        content = content,
    )
}
