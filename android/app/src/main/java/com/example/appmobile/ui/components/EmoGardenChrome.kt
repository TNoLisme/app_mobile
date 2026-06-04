package com.example.appmobile.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.appmobile.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DraggableAssistantBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("assistant_bubble_position", Context.MODE_PRIVATE)
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val bubbleSize = 64.dp
    val bubblePx = with(density) { bubbleSize.toPx() }
    val edgePx = with(density) { 12.dp.toPx() }
    val topPx = with(density) { 24.dp.toPx() }
    val bottomPx = edgePx
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bubbleImage = if (isDragging || isPressed) {
        R.drawable.assistant_bubble_active
    } else {
        R.drawable.assistant_bubble_idle
    }
    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging || isPressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "assistantBubbleScale"
    )

    LaunchedEffect(screenWidth, screenHeight) {
        offset.snapTo(clamp(offset.value))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bubbleImage),
            contentDescription = "Mầm Mầm",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(bubbleSize)
                .scale(bubbleScale)
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
                .clickable(
                    enabled = !isDragging,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        )
    }
}
