package com.twinndroid.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.random.Random

/** Seeded per-element values in flat arrays, so drawing allocates nothing per frame. */
internal class Field(n: Int, seed: Int) {
    val count = n
    private val rnd = Random(seed)
    val x = FloatArray(n) { rnd.nextFloat() }
    val y = FloatArray(n) { rnd.nextFloat() }
    val size = FloatArray(n) { rnd.nextFloat() }
    val speed = FloatArray(n) { 0.4f + rnd.nextFloat() * 0.6f }
    val phase = FloatArray(n) { rnd.nextFloat() * 6.2831855f }
    val depth = FloatArray(n) { 0.3f + rnd.nextFloat() * 0.7f }
    val vx = FloatArray(n) { rnd.nextFloat() - 0.5f }
    val vy = FloatArray(n) { rnd.nextFloat() - 0.5f }
    val kind = IntArray(n) { rnd.nextInt(3) }
}

internal data class BackdropColors(
    val bg: Color,
    val ink: Color,
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val dark: Boolean,
) {
    /** Overall strength: decorations are a little softer on light backgrounds. */
    // Light mode draws on a dimmed page, so the decorations can be a touch stronger than before
    // to actually read, while still staying subtle.
    val k = if (dark) 1f else 1.15f
}

/**
 * Everything needed to draw one frame of the animated background. It is shared through
 * [LocalBackdrop] so frosted surfaces ([Frosted]) can redraw the exact same frame, blurred,
 * behind themselves.
 */
@Stable
class Backdrop internal constructor() {
    internal val time = mutableFloatStateOf(17f)
    internal val tilt = mutableStateOf(Offset.Zero)
    internal var style by mutableStateOf(BgAnimation.SPACE)
    internal var rootSize by mutableStateOf(Size.Zero)
    // State, so the backdrop repaints on a theme change even with no animation ticking.
    internal var colors by mutableStateOf(BackdropColors(Color.Black, Color.White, Color.White, Color.White, Color.White, true))

    // Per-style scratch data, rebuilt when the style changes.
    internal var field = Field(1, 1)
    internal var field2 = Field(1, 1)
    internal val path = Path()
    internal val buf = FloatArray(512)
    internal var sprites: Array<ImageBitmap>? = null
    internal var spriteInk: Color = Color.Unspecified
    internal val lava: LavaShader? = if (Build.VERSION.SDK_INT >= 33) LavaShader() else null

    internal fun prepare(s: BgAnimation) {
        style = s
        when (s) {
            BgAnimation.SPACE -> { field = Field(260, 7); field2 = Field(120, 23) }
            BgAnimation.SNOW -> field = Field(96, 11)
            BgAnimation.PARTICLES -> field = Field(34, 3)
            BgAnimation.FIREFLIES -> field = Field(26, 29)
            BgAnimation.BOKEH -> field = Field(16, 31)
            BgAnimation.METEORS -> field = Field(140, 37)
            BgAnimation.MESH -> field = Field(MESH_COLS * MESH_ROWS, 41)
            else -> field = Field(8, 19)
        }
    }
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Creates the shared backdrop and drives its clock (only while resumed) and tilt sensor. */
@Composable
fun rememberBackdrop(): Backdrop {
    val extras = LocalTwinnExtras.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val b = remember { Backdrop() }
    val style = remember(extras.background) {
        if (extras.background == BgAnimation.RANDOM) {
            BgAnimation.entries.filter { it != BgAnimation.NONE && it != BgAnimation.RANDOM }.random()
        } else {
            extras.background
        }
    }
    remember(style) { b.prepare(style); style }
    val dark = scheme.background.luminance() < 0.5f
    SideEffect {
        b.colors = BackdropColors(
            bg = scheme.background,
            ink = if (dark) Color.White else Color(0xFF1A1A2A),
            c1 = scheme.primary,
            c2 = scheme.secondary,
            c3 = scheme.tertiary,
            dark = dark,
        )
    }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val lifecycle = LocalLifecycleOwner.current
    if (style != BgAnimation.NONE && !reduceMotion) {
        LaunchedEffect(style, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) b.time.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                        last = now
                    }
                }
            }
        }
    }
    TiltSensor(b, extras.parallax && style != BgAnimation.NONE && !reduceMotion)
    return b
}

/** Full-screen backdrop behind every screen. Each frame is a redraw only: no recomposition, no layout. */
@Composable
fun AnimatedBackground(backdrop: Backdrop, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .onSizeChanged { backdrop.rootSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .drawBehind { drawBackdrop(backdrop, size.width, size.height, null) },
    )
}

/**
 * A surface that shows the animated background behind it, blurred, under a tint: real frosted
 * glass. Only the area inside the surface is redrawn, and elements outside it are skipped.
 */
@Composable
fun Frosted(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = Color.Transparent,
    blur: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val b = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.clip(shape)) {
        if (b != null && blur > 0.dp && Build.VERSION.SDK_INT >= 31) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val r = blur.toPx()
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                        clip = true
                    }
                    .drawBehind {
                        val o = origin
                        val root = b.rootSize
                        if (root.width <= 0f) return@drawBehind
                        val margin = blur.toPx() * 2
                        val cull = Rect(o.x - margin, o.y - margin, o.x + size.width + margin, o.y + size.height + margin)
                        translate(-o.x, -o.y) { drawBackdrop(b, root.width, root.height, cull) }
                    },
            )
        }
        if (tint.alpha > 0f) Box(Modifier.matchParentSize().background(tint))
        content()
    }
}

@Composable
private fun TiltSensor(b: Backdrop, enabled: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(enabled, lifecycle) {
        if (!enabled) {
            b.tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val tx = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ty = ((e.values[1] - 6f) / 9.81f).coerceIn(-1f, 1f)
                val cur = b.tilt.value
                b.tilt.value = Offset(cur.x + (tx - cur.x) * 0.08f, cur.y + (ty - cur.y) * 0.08f)
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                Lifecycle.Event.ON_PAUSE -> sm.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
}

/** Draws one frame into a [w]×[h] area. [cull], when given, lets styles skip elements outside it. */
internal fun DrawScope.drawBackdrop(b: Backdrop, w: Float, h: Float, cull: Rect?) {
    val c = b.colors
    drawRect(c.bg, size = Size(w, h))
    val t = b.time.floatValue
    val p = b.tilt.value * 28.dp.toPx()
    val s = Scene(this, b, w, h, t, p, c, cull)
    when (b.style) {
        BgAnimation.SPACE -> s.space()
        BgAnimation.PARTICLES -> s.particles()
        BgAnimation.CIRCLES -> s.circles()
        BgAnimation.RINGS -> s.rings()
        BgAnimation.MESH -> s.mesh()
        BgAnimation.SHAPES -> s.shapes()
        BgAnimation.SNOW -> s.snow()
        BgAnimation.GRID -> s.grid()
        BgAnimation.LAVA -> s.lava()
        BgAnimation.AURORA -> s.aurora()
        BgAnimation.WAVES -> s.waves()
        BgAnimation.FIREFLIES -> s.fireflies()
        BgAnimation.BOKEH -> s.bokeh()
        BgAnimation.METEORS -> s.meteors()
        BgAnimation.NONE, BgAnimation.RANDOM -> Unit
    }
}
