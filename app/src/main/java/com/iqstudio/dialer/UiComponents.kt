//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

// shared UI bits: avatar, press feedback, video background player, liquid glass, universal background.
package com.iqstudio.dialer

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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

// The one background image behind every screen (Recents/Contacts/Settings/
// Advanced/Blocklist/Splash -- everywhere except InCallActivity, which keeps
// its own per-call photo/video background). Scrim alpha is a single tunable:
// raise it if text legibility suffers against a busy photo, lower it if the
// photo reads as too washed out. R.drawable.background_1 must exist on disk;
// this file doesn't create it.
private const val UniversalScrimAlpha = 0.62f

// Decoded once per process, not once per Activity. painterResource() on its
// own re-decodes the JPEG fresh every time a screen composes it -- Splash
// and Main both do, every cold start -- which is real, measurable work for
// a raster image this size. IQDialerApplication kicks off the first decode
// on a background thread at process start, so this is usually already
// populated by the time any screen actually needs it; the synchronized
// block just makes the cold-path (nothing warmed it yet) safe too.
object BackgroundImageCache {
    @Volatile private var bitmap: ImageBitmap? = null
    private val lock = Any()

    fun get(context: Context): ImageBitmap {
        bitmap?.let { return it }
        synchronized(lock) {
            bitmap?.let { return it }
            val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.background_1).asImageBitmap()
            bitmap = decoded
            return decoded
        }
    }
}

@Composable
fun UniversalBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember { BackgroundImageCache.get(context) }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101415).copy(alpha = UniversalScrimAlpha))
        )
        content()
    }
}

// White, not blue -- kept the name GlassTint (used as the default tint
// param everywhere) rather than renaming it across every file, but the
// value is now white. Everything built on liquidGlass() inherits this
// automatically since none of them hardcode a color. Red/green stay as
// explicit overrides at their own call sites (delete, call, FAB), never
// defaulted, so they're untouched by this change.
val GlassTint = Color.White

// Dark content color for anything sitting on a light/white glass surface
// (the selected nav pill) -- matches the app's own background color for a
// clean cutout look, and is the actual fix for white-on-white being
// invisible.
val DarkGlassContent = Color(0xFF101415)

// No backdrop blur -- native Compose has no first-party way to blur
// content behind a different composable on minSdk 29, and the available
// libraries are alpha-stage across the board right now. Gradient + border
// fake depth instead: brighter top fading to darker bottom, and a
// diagonal-gradient border brightest at the top-left corner. White reads
// as glass at meaningfully lower opacity than the old blue did -- these
// defaults are tuned for white specifically, not just carried over.
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = GlassTint,
    tintAlpha: Float = 0.18f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = (tintAlpha + 0.14f).coerceAtMost(1f)),
                tint.copy(alpha = tintAlpha),
                tint.copy(alpha = (tintAlpha - 0.06f).coerceAtLeast(0.03f))
            )
        )
    )
    .border(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.10f),
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
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.28f)
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
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.10f)
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
            .liquidGlass(shape = RoundedCornerShape(50), tint = tint, tintAlpha = 0.16f)
            .pressScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// Glass container for grouping content (not a button) -- deliberately
// lower opacity than the interactive components above. Opacity is the
// signal for tappability across this whole system: higher-opacity glass
// means "you can press this," lower-opacity glass means "this is grouped
// content."
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = GlassTint,
    tintAlpha: Float = 0.07f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), tint = tint, tintAlpha = tintAlpha),
        content = content
    )
}

// Floating glass row for list items (Recents/Contacts) -- same idea as
// GlassCard but sized and padded for a single clickable row rather than a
// content section.
@Composable
fun GlassRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GlassTint,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .liquidGlass(shape = RoundedCornerShape(16.dp), tint = tint, tintAlpha = 0.09f)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

