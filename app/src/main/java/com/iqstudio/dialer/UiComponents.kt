//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// shared UI bits: avatar, press feedback, video background player, liquid glass.
package com.iqstudio.dialer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

private val AVATAR_COLORS = listOf(
    Color(0xFF3E495D) to Color(0xFFBCC7DE),
    Color(0xFF8990A8) to Color(0xFF283044),
    Color(0xFF4D8EFF) to Color(0xFFE0E3E5)
)

private fun initialsFor(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "" + parts[0][0].uppercaseChar() + parts[1][0].uppercaseChar()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

private fun colorPairFor(key: String): Pair<Color, Color> {
    val index = abs(key.hashCode()) % AVATAR_COLORS.size
    return AVATAR_COLORS[index]
}

@Composable
fun ContactAvatar(name: String?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!name.isNullOrBlank()) {
            val (bg, fg) = colorPairFor(name)
            Box(
                modifier = Modifier.fillMaxSize().background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(initialsFor(name), color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(SurfaceCardHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

@Composable
fun Modifier.pressScale(scaleDown: Float = 0.92f, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun VideoBackgroundPlayer(
    item: BackgroundItem,
    scale: Float = item.scale,
    offsetX: Float = item.offsetX,
    offsetY: Float = item.offsetY,
    playAudio: Boolean = item.hasSound && !item.muted,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(playAudio) {
        exoPlayer.volume = if (playAudio) 1f else 0f
    }
    DisposableEffect(item.uri) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offsetX,
            translationY = offsetY
        )
    )
}

val GlassTint = Color(0xFF4D8EFF)

// No backdrop blur -- native Compose has no first-party way to blur
// content behind a different composable on minSdk 29, and the libraries
// that would add it are either alpha-only or not yet verified enough to
// depend on. Gradient + border are tuned to fake depth instead: brighter
// top fading to darker bottom (light catching a curved surface), and a
// diagonal-gradient border brightest at the top-left corner (where a
// light source would actually hit), rather than one flat tint and a
// uniform-brightness edge.
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = GlassTint,
    tintAlpha: Float = 0.55f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = (tintAlpha + 0.20f).coerceAtMost(1f)),
                tint.copy(alpha = tintAlpha),
                tint.copy(alpha = (tintAlpha - 0.10f).coerceAtLeast(0.05f))
            )
        )
    )
    .border(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.30f)
            )
        ),
        shape = shape
    )

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = GlassTint,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .liquidGlass(shape = CircleShape, tint = tint)
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// Filled glass pill -- drop-in replacement for Button().
@Composable
fun GlassButton(
    onClick: () -> Unit,
    tint: Color = GlassTint,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.75f)
            .pressScale(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// Low-opacity glass pill -- drop-in replacement for OutlinedButton().
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    tint: Color = GlassTint,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.18f)
            .pressScale(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// Small glass pill for inline text actions inside a list row (e.g. "Edit fit").
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    tint: Color = GlassTint,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.35f)
            .pressScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// Glass container for grouping content (not a button) -- deliberately
// lower opacity than the interactive components above. Opacity is the
// signal for tappability across this whole system: high-opacity glass
// means "you can press this," low-opacity glass means "this is grouped
// content." Keeping that distinction consistent matters more here than
// making every surface look identical.
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = GlassTint,
    tintAlpha: Float = 0.14f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), tint = tint, tintAlpha = tintAlpha),
        content = content
    )
}
