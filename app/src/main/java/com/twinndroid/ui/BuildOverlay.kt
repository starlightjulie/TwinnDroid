package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun steps() = listOf(
    stringResource(R.string.step_reading), stringResource(R.string.step_patching),
    stringResource(R.string.step_signing), stringResource(R.string.step_installing),
)

/** Full-screen, animated progress for building and installing a clone. */
@Composable
fun BuildOverlay(vm: MainViewModel, state: BuildState) {
    val context = LocalContext.current
    val success = state.result?.success == true
    val failed = state.result?.success == false
    var showLog by remember { mutableStateOf(false) }

    // Shake the card once on failure.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(failed) {
        if (failed) shake.animateTo(0f, keyframes { durationMillis = 420; -14f at 60; 12f at 140; -8f at 220; 5f at 300; 0f at 420 })
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .graphicsLayer { translationX = shake.value.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TwinAnimation(running = state.running, success = success, failed = failed, modifier = Modifier.size(200.dp))
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                when {
                    success -> stringResource(R.string.build_ready, state.label)
                    failed -> stringResource(R.string.build_failed)
                    else -> stringResource(R.string.build_cloning, state.label)
                },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "buildTitle",
            ) { title ->
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))

            TwinnCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    steps().forEachIndexed { i, label ->
                        val status = when {
                            state.stage > i || success -> StepStatus.DONE
                            failed && state.stage == i -> StepStatus.FAILED
                            state.stage == i && state.running -> StepStatus.ACTIVE
                            else -> StepStatus.PENDING
                        }
                        StepRow(label, status)
                    }
                }
            }

            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.result?.message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showLog = !showLog }) { Text(stringResource(if (showLog) R.string.hide_details else R.string.show_details)) }
            AnimatedVisibility(showLog, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val scroll = rememberScrollState()
                LaunchedEffect(state.logs.size) { scroll.animateScrollTo(scroll.maxValue) }
                SelectionContainer {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .verticalScroll(scroll)
                            .padding(10.dp),
                    ) {
                        state.logs.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(!state.running, enter = fadeIn() + expandVertically()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::dismissBuild) { Text(stringResource(if (success) R.string.done else R.string.close)) }
                    val launch = if (success) vm.launchIntent(state.clonePackage) else null
                    if (launch != null) {
                        Button(onClick = { vm.dismissBuild(); context.startActivity(launch) }) { Text(stringResource(R.string.open_clone)) }
                    }
                }
            }
        }
    }
}

private enum class StepStatus { PENDING, ACTIVE, DONE, FAILED }

@Composable
private fun StepRow(label: String, status: StepStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(status, transitionSpec = { (fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy))) togetherWith fadeOut() }, label = "step") { s ->
                when (s) {
                    StepStatus.ACTIVE -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    StepStatus.DONE -> Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(16.dp))
                    }
                    StepStatus.FAILED -> Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                    }
                    StepStatus.PENDING -> Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val alpha by animateFloatAsState(if (status == StepStatus.PENDING) 0.5f else 1f, label = "stepAlpha")
        Text(
            label,
            fontWeight = if (status == StepStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}

/**
 * The TwinnDroid "split droid" mark, animated. While cloning, the head splits and rejoins as
 * data hops from the original half to the clone half. On success the clone lifts away and both
 * halves grow back into two whole droids. On failure the halves snap back together.
 */
@Composable
private fun TwinAnimation(running: Boolean, success: Boolean, failed: Boolean, modifier: Modifier) {
    val front = MaterialTheme.colorScheme.onBackground
    val back = MaterialTheme.colorScheme.secondary
    val accent = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val eye = MaterialTheme.colorScheme.background
    val error = MaterialTheme.colorScheme.error

    val loop = rememberInfiniteTransition(label = "twin")
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin")
    val flow by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "flow")
    val split by animateFloatAsState(
        when {
            success -> 1f
            failed -> 0f
            running -> 0.35f + 0.65f * bob
            else -> 1f
        },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "split",
    )
    val complete by animateFloatAsState(if (success) 1f else 0f, tween(700, delayMillis = 250, easing = FastOutSlowInEasing), label = "complete")
    val burst = remember { Animatable(0f) }
    LaunchedEffect(success) { if (success) burst.animateTo(1f, tween(900, delayMillis = 300, easing = FastOutSlowInEasing)) }

    Canvas(modifier) {
        val r = size.minDimension * 0.2f
        val cx = size.width / 2
        val cy = size.height * 0.6f
        // Split: halves part sideways and the clone half lifts; once complete they level out.
        val sideways = r * 0.1f + r * 1.0f * split * (0.25f + 0.75f * complete)
        val lift = r * 0.28f * split * (1f - complete)
        val left = Offset(cx - sideways, cy)
        val right = Offset(cx + sideways, cy - lift)
        val leftColor = if (failed) error else front
        val rightColor = if (failed) error else back

        if (running) {
            val box = Size(r * 4.4f, r * 4.4f)
            val tl = Offset(cx - box.width / 2, cy - r * 0.45f - box.height / 2)
            drawArc(accent, spin, 110f, false, tl, box, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(back, spin + 180f, 70f, false, tl, box, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = 0.7f)
            // Data hopping from the original to the clone along an arc.
            val from = left + Offset(-r * 0.2f, -r * 0.45f)
            val to = right + Offset(r * 0.2f, -r * 0.45f)
            val ctrl = Offset((from.x + to.x) / 2, minOf(from.y, to.y) - r * 1.1f)
            for (k in 0 until 5) {
                val f = (flow + k / 5f) % 1f
                val a = 1 - f
                val pos = Offset(a * a * from.x + 2 * a * f * ctrl.x + f * f * to.x, a * a * from.y + 2 * a * f * ctrl.y + f * f * to.y)
                drawCircle(accent, r * 0.075f, pos, alpha = sin(f * PI.toFloat()))
            }
        }
        if (burst.value > 0f && burst.value < 1f) {
            val colors = arrayOf(accent, back, tertiary)
            for (i in 0 until 18) {
                val a = (i / 18f) * 2f * PI.toFloat()
                val d = r * (1.4f + burst.value * 1.8f)
                drawCircle(colors[i % 3], 4.dp.toPx() * (1f - burst.value), Offset(cx + cos(a) * d, cy - r * 0.5f + sin(a) * d), alpha = 1f - burst.value)
            }
        }

        // Left droid: the original's left half, growing its right half back on success.
        drawSplitDroid(left, r, leftColor, eye, startAngle = 180f, sweep = 90f + 90f * complete, leftSide = true, complete = complete)
        // Right droid: the clone's right half, growing its left half back on success.
        drawSplitDroid(right, r, rightColor, eye, startAngle = 270f - 90f * complete, sweep = 90f + 90f * complete, leftSide = false, complete = complete)

        // Base line: one shared line while joined, splitting into two under the finished droids.
        val baseY = cy + r * 0.3f
        val stroke = r * 0.14f
        drawLine(front, Offset(left.x - r, baseY), Offset(right.x + r, baseY), stroke, StrokeCap.Round, alpha = 0.45f * (1f - complete))
        if (complete > 0f) {
            drawLine(leftColor, Offset(left.x - r, baseY), Offset(left.x + r, baseY), stroke, StrokeCap.Round, alpha = 0.45f * complete)
            drawLine(rightColor, Offset(right.x - r, baseY), Offset(right.x + r, baseY), stroke, StrokeCap.Round, alpha = 0.45f * complete)
        }
    }
}

/** One droid head drawn as a wedge of a half-disc, with the antenna and eye of each visible side. */
private fun DrawScope.drawSplitDroid(
    c: Offset,
    r: Float,
    color: Color,
    eye: Color,
    startAngle: Float,
    sweep: Float,
    leftSide: Boolean,
    complete: Float,
) {
    drawArc(color, startAngle, sweep, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
    val stroke = r * 0.13f
    val ownAlpha = 1f
    val otherAlpha = complete
    fun antenna(side: Float, alpha: Float) {
        if (alpha <= 0f) return
        val base = Offset(c.x + side * r * 0.5f, c.y - r * 0.86f)
        drawLine(color, base, Offset(base.x + side * r * 0.22f, base.y - r * 0.38f), stroke, StrokeCap.Round, alpha = alpha)
    }
    fun eyeAt(side: Float, alpha: Float) {
        if (alpha <= 0f) return
        drawCircle(eye, r * 0.11f, Offset(c.x + side * r * 0.36f, c.y - r * 0.4f), alpha = alpha)
    }
    val own = if (leftSide) -1f else 1f
    antenna(own, ownAlpha)
    antenna(-own, otherAlpha)
    eyeAt(own, ownAlpha)
    eyeAt(-own, otherAlpha)
}
