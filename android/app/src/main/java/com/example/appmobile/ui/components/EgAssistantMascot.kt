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

    drawCircle(Color(0xFFFFD45C), radius = s * 0.36f, center = center)
    drawCircle(Color(0xFFFFE897), radius = s * 0.15f, center = Offset(center.x - s * 0.12f, center.y - s * 0.12f))
    drawCircle(Color(0xFFB87912).copy(alpha = 0.20f), radius = s * 0.36f, center = center, style = Stroke(s * 0.025f))

    val faceColor = Color(0xFF273044)
    drawCircle(faceColor, radius = s * 0.045f, center = Offset(center.x - s * 0.13f, center.y - s * 0.03f))
    drawCircle(faceColor, radius = s * 0.045f, center = Offset(center.x + s * 0.13f, center.y - s * 0.03f))
    drawCircle(Color.White, radius = s * 0.014f, center = Offset(center.x - s * 0.145f, center.y - s * 0.045f))
    drawCircle(Color.White, radius = s * 0.014f, center = Offset(center.x + s * 0.115f, center.y - s * 0.045f))

    drawCircle(Color(0xFFFF9DB6).copy(alpha = 0.28f), radius = s * 0.055f, center = Offset(center.x - s * 0.22f, center.y + s * 0.08f))
    drawCircle(Color(0xFFFF9DB6).copy(alpha = 0.28f), radius = s * 0.055f, center = Offset(center.x + s * 0.22f, center.y + s * 0.08f))

    if (speaking) {
        drawCircle(faceColor, radius = s * 0.055f, center = Offset(center.x, center.y + s * 0.13f))
    } else {
        drawArc(
            color = faceColor,
            startAngle = 24f,
            sweepAngle = 132f,
            useCenter = false,
            topLeft = Offset(center.x - s * 0.14f, center.y + s * 0.04f),
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
