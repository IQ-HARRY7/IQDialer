//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// My Little secret 👀😜
package com.iqstudio.dialer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class InfiniteScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            IQDialerTheme {
                InfiniteScanScreen(onBack = { finish() })
            }
        }
    }
}

private const val COLOR_CYCLE_SECONDS = 12f
private const val METEOR_LAP_SECONDS = 3.6f
private const val ROLL_SECONDS = 16f

// Red to blue and back, going through magenta and violet.
private fun scanColors(t: Float): Pair<Color, Color> {
    val raw = 0.5f - 0.5f * cos(t * 2f * PI.toFloat() / COLOR_CYCLE_SECONDS)
    val phase = raw * raw * (3f - 2f * raw)
    val hue = (((3f - 152f * phase) % 360f) + 360f) % 360f
    val sat = 0.81f + 0.19f * phase
    return Color.hsv(hue, sat, 1f) to Color.hsv(hue, sat * 0.42f, 1f)
}

@Composable
private fun InfiniteScanScreen(onBack: () -> Unit) {
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                clock.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(900)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
        }
        Text(
            "DEVELOPER MODE",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val orbSize = min(min(maxWidth.value, maxHeight.value), 380f).dp
            Box(
                modifier = Modifier
                    .size(orbSize)
                    .graphicsLayer {
                        alpha = appear.value
                        scaleX = 0.85f + 0.15f * appear.value
                        scaleY = 0.85f + 0.15f * appear.value
                    },
                contentAlignment = Alignment.Center
            ) {
                ScanOrb(clock)
                MeteorLayer(clock, front = false)
                OrbCenter(clock)
                MeteorLayer(clock, front = true)
            }
        }

        Text(
            "Powered by IQ_STUDIO🎊",
            color = Color(0xFF6B6B70),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
        )
    }
}

@Composable
private fun ScanOrb(clock: MutableFloatState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.floatValue
        val (c, g) = scanColors(t)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.84f
        val pulse = 0.5f + 0.5f * sin(t * 1.4f)

        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.copy(alpha = 0.30f + 0.10f * pulse), c.copy(alpha = 0.10f), Color.Transparent),
                center = center,
                radius = radius * 1.4f
            ),
            radius = radius * 1.4f,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.copy(alpha = 0.24f), c.copy(alpha = 0.10f), c.copy(alpha = 0.03f)),
                center = Offset(center.x, center.y - radius * 0.25f),
                radius = radius * 0.95f
            ),
            radius = radius * 0.78f,
            center = center
        )

        drawCircle(
            color = g.copy(alpha = 0.30f + 0.15f * pulse),
            radius = radius * 0.78f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

@Composable
private fun MeteorLayer(clock: MutableFloatState, front: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.floatValue
        val (core, glow) = scanColors(t)
        drawMeteor(t, core, glow, front)
    }
}

// The orbit is a tilted circle in 3D. Segments behind the name go in the back layer, the rest in front.
private fun DrawScope.drawMeteor(t: Float, core: Color, glow: Color, front: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val a = size.minDimension / 2f * 0.84f * 0.98f
    val spin = t * (2f * PI.toFloat() / METEOR_LAP_SECONDS)
    val tilt = 0.44f + 0.12f * sin(t * 0.40f)
    val roll = t * (2f * PI.toFloat() / ROLL_SECONDS)
    val sinT = sin(tilt)
    val cosT = cos(tilt)
    val sinR = sin(roll)
    val cosR = cos(roll)

    fun point(theta: Float, out: FloatArray) {
        val x0 = cos(theta)
        val z0 = sin(theta)
        val y1 = -z0 * sinT
        val z1 = z0 * cosT
        val x2 = x0 * cosR - y1 * sinR
        val y2 = x0 * sinR + y1 * cosR
        val s = 1f + 0.22f * z1
        out[0] = cx + x2 * a * s
        out[1] = cy + y2 * a * s
        out[2] = z1
    }

    val tailSegments = 80
    val step = 0.028f
    val head = FloatArray(3)
    val cur = FloatArray(3)
    point(spin, head)
    var px = head[0]
    var py = head[1]
    var pz = head[2]

    for (k in 1..tailSegments) {
        point(spin - k * step, cur)
        val zMid = (pz + cur[2]) / 2f
        if ((zMid >= 0f) == front) {
            val f = k / tailSegments.toFloat()
            val fade = (1f - f).pow(1.7f)
            val dim = 0.55f + 0.45f * (zMid + 1f) / 2f
            val w = (4.2f * (1f - f) + 0.5f) * density * (1f + 0.3f * zMid)
            val tone = lerp(Color.White, glow, (f * 1.6f).coerceAtMost(1f))
            drawLine(
                color = core.copy(alpha = (fade * dim * 0.22f).coerceIn(0f, 1f)),
                start = Offset(px, py),
                end = Offset(cur[0], cur[1]),
                strokeWidth = w * 3.4f
            )
            drawLine(
                color = tone.copy(alpha = (fade * dim).coerceIn(0f, 1f)),
                start = Offset(px, py),
                end = Offset(cur[0], cur[1]),
                strokeWidth = w
            )
        }
        px = cur[0]
        py = cur[1]
        pz = cur[2]
    }

    if ((head[2] >= 0f) == front) {
        val headCenter = Offset(head[0], head[1])
        val reach = 11.dp.toPx() * (1f + 0.3f * head[2])
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, glow.copy(alpha = 0.85f), core.copy(alpha = 0.30f), Color.Transparent),
                center = headCenter,
                radius = reach
            ),
            radius = reach,
            center = headCenter
        )
        drawCircle(color = Color.White, radius = 2.4.dp.toPx() * (1f + 0.3f * head[2]), center = headCenter)
    }
}

@Composable
private fun OrbCenter(clock: MutableFloatState) {
    val (core, glow) = scanColors(clock.floatValue)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val s = 1f + 0.025f * sin(clock.floatValue * 2f)
            scaleX = s
            scaleY = s
        }
    ) {
        Text(
            "DEVELOPER MODE",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "IQ_HARRY_07",
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(Color.White, glow, Color.White)),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                shadow = Shadow(color = core.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = 28f)
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Scanning...",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * (0.5f + 0.5f * sin(clock.floatValue * 2.6f)) }
        )
    }
}

// Secret is over ✌️
// Quite sure that it was "Asthetic" 🗿