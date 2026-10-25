package com.twinndroid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twinndroid.ui.theme.LocalTwinnExtras

/** Card that follows the Appearance settings: translucent "glass" and optional outline. */
@Composable
fun TwinnCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = LocalTwinnExtras.current
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (extras.glassCards) container.copy(alpha = 0.7f) else container),
        border = if (extras.outlines) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)) else null,
        content = content,
    )
}

class TabItem(val label: String, val icon: ImageVector)

/**
 * Bottom bar of pill tabs: the selected pill widens and reveals its label with a spring.
 * [progress] is the pager's fractional position, so the highlight tracks a swipe in real time.
 */
@Composable
fun PillTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val extras = LocalTwinnExtras.current
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val weight by animateFloatAsState(
                if (isSel) 2.2f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "tabWeight",
            )
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (extras.glassCards) 0.75f else 1f),
                label = "tabBg",
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabFg",
            )
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(weight)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(bg)
                    .then(
                        if (extras.outlines || isSel) {
                            Modifier.border(
                                1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(28.dp),
                            )
                        } else Modifier,
                    )
                    .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val scale by animateFloatAsState(if (isSel) 1.1f else 1f, label = "iconScale")
                    Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                    AnimatedVisibility(
                        visible = isSel,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(tab.label, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
