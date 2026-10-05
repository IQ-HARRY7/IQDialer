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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

private val AVATAR_GRADIENTS = listOf(
    Color(0xFF4F5BD5) to Color(0xFF9B5DE5),
    Color(0xFFFF7E5F) to Color(0xFFFF4D8D),
    Color(0xFF00C6A7) to Color(0xFF3A7BFF),
    Color(0xFFFFB347) to Color(0xFFFF6B6B),
    Color(0xFF8E54E9) to Color(0xFFEC5FA6),
    Color(0xFF34D399) to Color(0xFF0EA5E9),
    Color(0xFF38BDF8) to Color(0xFF6366F1),
    Color(0xFFF59E0B) to Color(0xFFEF4444)
)

private fun initialsFor(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "" + parts[0][0].uppercaseChar() + parts[1][0].uppercaseChar()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

private fun gradientFor(key: String): Pair<Color, Color> =
    AVATAR_GRADIENTS[abs(key.hashCode()) % AVATAR_GRADIENTS.size]

@Composable
fun ContactAvatar(name: String?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!name.isNullOrBlank()) {
            val (from, to) = gradientFor(name)
            Box(
                modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(from, to))),
                contentAlignment = Alignment.Center
            ) {
                Text(initialsFor(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF4F5BD5), Color(0xFF9B5DE5)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressScale(scaleDown: Float = 0.92f, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .combinedClickable(interactionSource = interactionSource, indication = null, onLongClick = onLongClick, onClick = onClick)
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

private const val UniversalScrimAlpha = 0.62f

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

val GlassTint = Color.White

val DarkGlassContent = Color(0xFF101415)

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

@Composable
fun GlassRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    tint: Color = GlassTint,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .liquidGlass(shape = RoundedCornerShape(16.dp), tint = tint, tintAlpha = 0.09f)
            .pressScale(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val menuShape = RoundedCornerShape(20.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(with(density) { (-12).dp.roundToPx() }, with(density) { 8.dp.roundToPx() }),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        var animateIn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { animateIn = true }
        val scale by animateFloatAsState(
            targetValue = if (animateIn) 1f else 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "dropdownScale"
        )
        val alpha by animateFloatAsState(targetValue = if (animateIn) 1f else 0f, label = "dropdownAlpha")

        Column(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .widthIn(min = 210.dp)
                .width(IntrinsicSize.Max)
                .clip(menuShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2E2B52).copy(alpha = 0.58f), Color(0xFF1A2236).copy(alpha = 0.58f))
                    )
                )
                .liquidGlass(shape = menuShape, tint = AccentViolet, tintAlpha = 0.16f)
                .padding(vertical = 8.dp),
            content = content
        )
    }
}

@Composable
fun GlassDropdownMenuItem(
    text: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    accent: Color = AccentIndigo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .liquidGlass(shape = CircleShape, tint = accent, tintAlpha = 0.34f),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text,
            color = if (selected) accent else Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

// Rounded gradient tile for a settings row's leading icon.
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .liquidGlass(shape = shape, tintAlpha = 0.14f)
            .then(if (focused) Modifier.border(1.5.dp, AccentIndigo.copy(alpha = 0.8f), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                cursorBrush = SolidColor(AccentIndigo),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).pressScale { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconChip(icon: ImageVector, accent: Color, size: Dp = 38.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .liquidGlass(shape = RoundedCornerShape(size * 0.32f), tint = accent, tintAlpha = 0.32f),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun glassSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = AccentIndigo,
    checkedBorderColor = AccentIndigo
)

// ;
// Go to Sleep 
