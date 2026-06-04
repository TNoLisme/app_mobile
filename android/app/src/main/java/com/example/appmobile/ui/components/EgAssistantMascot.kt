package com.example.appmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.appmobile.R

@Composable
fun EgAssistantMascot(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    speaking: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (speaking) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "assistantMascotScale"
    )
    Image(
        painter = painterResource(
            id = if (speaking) R.drawable.assistant_bubble_active else R.drawable.assistant_bubble_idle
        ),
        contentDescription = "Mầm Mầm",
        modifier = modifier
            .size(size)
            .scale(scale),
        contentScale = ContentScale.Fit
    )
}
