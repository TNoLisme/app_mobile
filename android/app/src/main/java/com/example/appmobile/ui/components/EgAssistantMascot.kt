package com.example.appmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun EgAssistantMascot(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    speaking: Boolean = false
) {
    Canvas(modifier = modifier.size(size)) {
        drawAssistantMascot(speaking)
    }
}

// Simple mascot drawn from basic shapes so the implementation is easy to explain.
private fun DrawScope.drawAssistantMascot(speaking: Boolean) {
    val s = min(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(
        color = Color(0xFF1F4E6A).copy(alpha = 0.12f),
        radius = s * 0.38f,
        center = Offset(center.x, center.y + s * 0.08f)
    )

    drawLeaf(center = Offset(center.x - s * 0.03f, center.y - s * 0.30f), size = s, left = true)
    drawLeaf(center = Offset(center.x + s * 0.03f, center.y - s * 0.30f), size = s, left = false)

    drawOval(
        color = Color(0xFF62B8F6),
        topLeft = Offset(center.x - s * 0.24f, center.y + s * 0.16f),
        size = Size(s * 0.48f, s * 0.18f)
    )
    drawRect(
        color = Color(0xFF74C7FF),
        topLeft = Offset(center.x - s * 0.20f, center.y + s * 0.20f),
        size = Size(s * 0.40f, s * 0.16f)
    )
    drawOval(
        color = Color(0xFF1E73BE).copy(alpha = 0.18f),
        topLeft = Offset(center.x - s * 0.20f, center.y + s * 0.26f),
        size = Size(s * 0.40f, s * 0.12f)
    )

    drawCircle(Color(0xFFFDFEFF), radius = s * 0.33f, center = Offset(center.x, center.y - s * 0.02f))
    drawCircle(Color(0xFF0F4C8A).copy(alpha = 0.88f), radius = s * 0.33f, center = Offset(center.x, center.y - s * 0.02f), style = Stroke(s * 0.022f))
    drawCircle(Color.White, radius = s * 0.12f, center = Offset(center.x - s * 0.13f, center.y - s * 0.15f))

    val faceColor = Color(0xFF273044)
    drawCircle(Color(0xFF143B68), radius = s * 0.052f, center = Offset(center.x - s * 0.12f, center.y - s * 0.05f))
    drawCircle(Color(0xFF143B68), radius = s * 0.052f, center = Offset(center.x + s * 0.12f, center.y - s * 0.05f))
    drawCircle(Color.White, radius = s * 0.018f, center = Offset(center.x - s * 0.137f, center.y - s * 0.067f))
    drawCircle(Color.White, radius = s * 0.018f, center = Offset(center.x + s * 0.103f, center.y - s * 0.067f))

    drawCircle(Color(0xFFFF9DB6).copy(alpha = 0.32f), radius = s * 0.052f, center = Offset(center.x - s * 0.21f, center.y + s * 0.06f))
    drawCircle(Color(0xFFFF9DB6).copy(alpha = 0.32f), radius = s * 0.052f, center = Offset(center.x + s * 0.21f, center.y + s * 0.06f))

    if (speaking) {
        drawCircle(faceColor, radius = s * 0.050f, center = Offset(center.x, center.y + s * 0.10f))
    } else {
        drawArc(
            color = faceColor,
            startAngle = 24f,
            sweepAngle = 132f,
            useCenter = false,
            topLeft = Offset(center.x - s * 0.14f, center.y + s * 0.02f),
            size = Size(s * 0.28f, s * 0.18f),
            style = Stroke(width = s * 0.04f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawLeaf(center: Offset, size: Float, left: Boolean) {
    val direction = if (left) -1f else 1f
    val path = Path().apply {
        moveTo(center.x, center.y)
        cubicTo(
            center.x + direction * size * 0.15f,
            center.y - size * 0.20f,
            center.x + direction * size * 0.36f,
            center.y - size * 0.08f,
            center.x + direction * size * 0.16f,
            center.y + size * 0.10f
        )
        cubicTo(
            center.x + direction * size * 0.06f,
            center.y + size * 0.03f,
            center.x + direction * size * 0.02f,
            center.y - size * 0.08f,
            center.x,
            center.y
        )
        close()
    }
    drawPath(path, color = if (left) Color(0xFF5FCB70) else Color(0xFF43B75A))
}
