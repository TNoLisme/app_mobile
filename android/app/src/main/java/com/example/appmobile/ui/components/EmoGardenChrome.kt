package com.example.appmobile.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
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
    val bubblePx = with(density) { 58.dp.toPx() }
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
            border = BorderStroke(1.dp, EgDesign.primary.copy(alpha = 0.40f)),
            shadowElevation = if (isDragging) 8.dp else 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFEAF7FF), Color(0xFFBEE7FF)),
                        ),
                        CircleShape
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                EgAssistantMascot(size = 44.dp, speaking = isDragging)
            }
        }
    }
}
