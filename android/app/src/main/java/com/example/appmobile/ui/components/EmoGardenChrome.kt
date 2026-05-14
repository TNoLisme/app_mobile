package com.example.appmobile.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class EmoGardenNavItem(val title: String) {
    Home("Trang chủ"),
    Learn("Học"),
    Games("Chơi game")
}

val EmoGardenBackground = SolidColor(Color(0xFFEAF7FF))

val EmoGardenButtonGradient = SolidColor(Color(0xFF62B5FF))

@Composable
fun EmoGardenTopNav(
    activeItem: EmoGardenNavItem,
    onHome: () -> Unit,
    onLearn: () -> Unit,
    onGames: () -> Unit,
    onProfile: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onProfile?.let {
                    RoundNavAction(text = "👤", onClick = it)
                }
                Spacer(modifier = Modifier.weight(1f))
                onSettings?.let {
                    RoundNavAction(text = "⚙", onClick = it)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavPill(
                    item = EmoGardenNavItem.Home,
                    activeItem = activeItem,
                    onClick = onHome,
                    modifier = Modifier.weight(1f)
                )
                NavPill(
                    item = EmoGardenNavItem.Learn,
                    activeItem = activeItem,
                    onClick = onLearn,
                    modifier = Modifier.weight(1f)
                )
                NavPill(
                    item = EmoGardenNavItem.Games,
                    activeItem = activeItem,
                    onClick = onGames,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RoundNavAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFEAF7FF),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 19.sp)
        }
    }
}

@Composable
private fun NavPill(
    item: EmoGardenNavItem,
    activeItem: EmoGardenNavItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = item == activeItem
    val background = if (active) Color(0xFF62B5FF) else Color(0xFFF8FCFF)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = if (active) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(background, CircleShape)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.title,
                color = if (active) Color.White else Color(0xFF0B3C7D),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GradientActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(EmoGardenButtonGradient, CircleShape)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun AssistantChatBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(58.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EmoGardenButtonGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("💬", fontSize = 28.sp)
        }
    }
}

@Composable
fun DraggableAssistantBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("assistant_bubble_position", Context.MODE_PRIVATE)
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val bubblePx = with(density) { 58.dp.toPx() }
    val edgePx = with(density) { 12.dp.toPx() }
    val topPx = with(density) { 24.dp.toPx() }
    val bottomPx = with(density) { 92.dp.toPx() }
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxX = (screenWidth - bubblePx - edgePx).coerceAtLeast(edgePx)
    val maxY = (screenHeight - bubblePx - bottomPx).coerceAtLeast(topPx)
    val defaultX = maxX
    val defaultY = maxY
    fun clamp(value: Offset): Offset {
        return Offset(
            x = value.x.coerceIn(edgePx, maxX),
            y = value.y.coerceIn(topPx, maxY)
        )
    }

    val offset = remember(screenWidth, screenHeight) {
        Animatable(
            clamp(
                Offset(
                    preferences.getFloat("x", defaultX),
                    preferences.getFloat("y", defaultY)
                )
            ),
            Offset.VectorConverter
        )
    }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(screenWidth, screenHeight) {
        offset.snapTo(clamp(offset.value))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(58.dp)
                .pointerInput(screenWidth, screenHeight) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo(clamp(offset.value + dragAmount))
                            }
                        },
                        onDragEnd = {
                            val targetX = if (offset.value.x < screenWidth / 2f) edgePx else maxX
                            scope.launch {
                                val target = clamp(Offset(targetX, offset.value.y))
                                offset.animateTo(target, animationSpec = spring())
                                preferences.edit()
                                    .putFloat("x", target.x)
                                    .putFloat("y", target.y)
                                    .apply()
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
                .clickable(enabled = !isDragging, onClick = onClick),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
            shadowElevation = if (isDragging) 8.dp else 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EmoGardenButtonGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("💬", fontSize = 28.sp)
            }
        }
    }
}
