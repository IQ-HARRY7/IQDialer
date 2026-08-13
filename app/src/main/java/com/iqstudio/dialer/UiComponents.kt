//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later). 
// * 
//**************************************************

// for UI/UX must be improved. ui is terrible at this moment. 

package com.iqstudio.dialer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

// Pairs 
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

// Colored initials for anyone with a name (matches the reference design);
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

// Tactile press feedback (scale to 0.92 while held) matching the reference
@Composable
fun Modifier.pressScale(scaleDown: Float = 0.92f, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) scaleDown else 1f, label = "pressScale")
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

// Shared between InCallActivity (plays the item's saved scale/offset) and
// AdvancedSettingsScreen's fit editor (overrides scale/offset with live,
// not-yet-saved gesture state) -- same player, same resize behavior.
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


// It was Hard - yet working now! 