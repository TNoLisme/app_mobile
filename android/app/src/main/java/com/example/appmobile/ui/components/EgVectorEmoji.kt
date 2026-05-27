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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun EgEmotionVectorIcon(
    emotion: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    EgVectorEmojiIcon(value = egEmotionKey(emotion), modifier = modifier, size = size)
}

@Composable
fun EgVectorEmojiIcon(
    value: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null
) {
    val key = egVectorEmojiKey(value)
    Canvas(modifier = modifier.size(size)) {
        when (key) {
            "happy", "sad", "angry", "fear", "surprise", "disgust", "neutral" -> {
                drawEmotionFace(key)
            }
            "settings" -> drawGear(tint ?: EgDesign.blue)
            "user", "child" -> drawUser(tint ?: EgDesign.blue)
            "report", "document" -> drawDocument(tint ?: EgDesign.blue)
            "sparkle", "star" -> drawSparkle(tint ?: Color(0xFFFFC928))
            "warning" -> drawWarning(tint ?: Color(0xFFF59E0B))
            "bulb" -> drawBulb(tint ?: Color(0xFFFFC928))
            "lock" -> drawLock(tint ?: EgDesign.textSecondary)
            "camera" -> drawCamera(tint ?: EgDesign.blue)
            "gamepad" -> drawGamepad(tint ?: EgDesign.blue)
            "chat" -> drawChat(tint ?: EgDesign.blue)
            "phone" -> drawPhone(tint ?: EgDesign.blue)
            "mail" -> drawMail(tint ?: EgDesign.blue)
            "calendar" -> drawCalendar(tint ?: EgDesign.blue)
            "cake" -> drawCake(tint ?: Color(0xFFEF8D31))
            "trophy" -> drawTrophy(tint ?: Color(0xFFEAB308))
            "check" -> drawCheck(tint ?: Color.White)
            "close" -> drawClose(tint ?: EgDesign.textSecondary)
            "microphone" -> drawMicrophone(tint ?: EgDesign.blue)
            "eye" -> drawEye(tint ?: EgDesign.blue)
            "puzzle" -> drawPuzzle(tint ?: EgDesign.blue)
            "palette" -> drawPalette(tint ?: EgDesign.blue)
            "speaker" -> drawSpeaker(tint ?: EgDesign.blue)
            "bell" -> drawBell(tint ?: EgDesign.blue)
            "clock" -> drawClock(tint ?: EgDesign.blue)
            "save" -> drawSave(tint ?: EgDesign.blue)
            "exit" -> drawExit(tint ?: EgDesign.blue)
            "book" -> drawBook(tint ?: EgDesign.blue)
            "gift" -> drawGift(tint ?: Color(0xFFEF4444))
            "trash" -> drawTrash(tint ?: EgDesign.textSecondary)
            else -> drawGeneric(tint ?: EgDesign.blue)
        }
    }
}

fun egVectorEmojiKey(value: String): String {
    val raw = value.trim()
    val lower = raw.lowercase()
    val emotionKey = egEmotionKey(raw)
    if (emotionKey in EgEmotionDisplayOrder) return emotionKey
    return when {
        raw == "\uD83D\uDE0A" || raw == "\uD83D\uDE42" -> "happy"
        raw == "\uD83D\uDE22" -> "sad"
        raw == "\uD83D\uDE21" || raw == "\uD83D\uDE20" -> "angry"
        raw == "\uD83D\uDE28" -> "fear"
        raw == "\uD83D\uDE2E" || raw == "\uD83D\uDE32" -> "surprise"
        raw == "\uD83E\uDD22" -> "disgust"
        raw == "\u2699\uFE0F" || raw == "\u2699" || "setting" in lower -> "settings"
        raw == "\uD83D\uDC64" || raw == "\uD83D\uDC76" || "user" in lower || "child" in lower -> "user"
        raw == "\uD83D\uDCCB" || raw == "\uD83D\uDCC4" || "report" in lower || "document" in lower -> "report"
        raw == "\u2728" || raw == "\uD83C\uDF1F" || raw == "\u2B50" || "sparkle" in lower || "star" in lower -> "sparkle"
        raw == "\u26A0\uFE0F" || raw == "\u26A0" || raw == "!" -> "warning"
        raw == "\uD83D\uDCA1" -> "bulb"
        raw == "\uD83D\uDD12" || raw == "\uD83D\uDD10" || raw == "\uD83D\uDD13" -> "lock"
        raw == "\uD83D\uDCF7" -> "camera"
        raw == "\uD83C\uDFAE" -> "gamepad"
        raw == "\uD83D\uDCAC" -> "chat"
        raw == "\u260E" || raw == "\u260E\uFE0F" -> "phone"
        raw == "\u2709" || raw == "\u2709\uFE0F" -> "mail"
        raw == "\uD83D\uDCC5" || raw == "\uD83D\uDCC6" -> "calendar"
        raw == "\uD83C\uDF82" -> "cake"
        raw == "\uD83C\uDFC6" -> "trophy"
        raw == "\u2713" || raw == "\u2705" -> "check"
        raw == "\u274C" || raw == "\u00D7" || raw == "\u2715" -> "close"
        raw == "\uD83C\uDFA4" || raw == "\uD83C\uDF99\uFE0F" -> "microphone"
        raw == "\uD83D\uDC41" || raw == "\uD83D\uDC40" -> "eye"
        raw == "\uD83E\uDDE9" -> "puzzle"
        raw == "\uD83C\uDFA8" -> "palette"
        raw == "\uD83D\uDD0A" -> "speaker"
        raw == "\uD83D\uDD14" -> "bell"
        raw == "\u23F0" -> "clock"
        raw == "\uD83D\uDCBE" -> "save"
        raw == "\uD83D\uDEAA" -> "exit"
        raw == "\uD83D\uDCD6" || raw == "\uD83D\uDCDA" -> "book"
        raw == "\uD83C\uDF81" -> "gift"
        raw == "\uD83D\uDDD1\uFE0F" || raw == "\uD83D\uDDD1" -> "trash"
        else -> lower
    }
}

private fun DrawScope.drawEmotionFace(key: String) {
    val w = size.width
    val h = size.height
    val s = min(w, h)
    val c = Offset(w / 2f, h / 2f)
    val faceColor = when (key) {
        "angry" -> Color(0xFFFF8A3D)
        "fear" -> Color(0xFF8CB5FF)
        "disgust" -> Color(0xFFA6D96A)
        "sad" -> Color(0xFFFFD86B)
        "surprise" -> Color(0xFFFFD86B)
        else -> Color(0xFFFFD64D)
    }
    drawCircle(faceColor, radius = s * 0.46f, center = c)
    drawCircle(Color.White.copy(alpha = 0.45f), radius = s * 0.15f, center = Offset(c.x - s * 0.16f, c.y - s * 0.18f))

    val stroke = Stroke(width = s * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dark = Color(0xFF253247)
    val eyeY = c.y - s * 0.11f
    val leftEye = Offset(c.x - s * 0.16f, eyeY)
    val rightEye = Offset(c.x + s * 0.16f, eyeY)

    when (key) {
        "happy" -> {
            drawArc(dark, 200f, 140f, false, topLeft = Offset(leftEye.x - s * 0.08f, leftEye.y - s * 0.03f), size = Size(s * 0.16f, s * 0.12f), style = stroke)
            drawArc(dark, 200f, 140f, false, topLeft = Offset(rightEye.x - s * 0.08f, rightEye.y - s * 0.03f), size = Size(s * 0.16f, s * 0.12f), style = stroke)
            drawArc(dark, 20f, 140f, false, topLeft = Offset(c.x - s * 0.22f, c.y - s * 0.02f), size = Size(s * 0.44f, s * 0.30f), style = stroke)
        }
        "sad" -> {
            drawCircle(dark, s * 0.04f, leftEye)
            drawCircle(dark, s * 0.04f, rightEye)
            drawArc(dark, 200f, 140f, false, topLeft = Offset(c.x - s * 0.19f, c.y + s * 0.12f), size = Size(s * 0.38f, s * 0.26f), style = stroke)
            drawPath(Path().apply {
                moveTo(rightEye.x + s * 0.07f, rightEye.y + s * 0.04f)
                quadraticBezierTo(rightEye.x + s * 0.13f, rightEye.y + s * 0.15f, rightEye.x + s * 0.07f, rightEye.y + s * 0.22f)
            }, color = Color(0xFF4FC3F7), style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
        }
        "angry" -> {
            drawLine(dark, Offset(leftEye.x - s * 0.09f, leftEye.y - s * 0.08f), Offset(leftEye.x + s * 0.08f, leftEye.y - s * 0.01f), stroke.width, StrokeCap.Round)
            drawLine(dark, Offset(rightEye.x + s * 0.09f, rightEye.y - s * 0.08f), Offset(rightEye.x - s * 0.08f, rightEye.y - s * 0.01f), stroke.width, StrokeCap.Round)
            drawCircle(dark, s * 0.035f, leftEye)
            drawCircle(dark, s * 0.035f, rightEye)
            drawLine(dark, Offset(c.x - s * 0.15f, c.y + s * 0.20f), Offset(c.x + s * 0.15f, c.y + s * 0.16f), stroke.width, StrokeCap.Round)
        }
        "fear" -> {
            drawCircle(dark, s * 0.055f, leftEye)
            drawCircle(dark, s * 0.055f, rightEye)
            drawOval(dark, topLeft = Offset(c.x - s * 0.09f, c.y + s * 0.10f), size = Size(s * 0.18f, s * 0.20f))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x - s * 0.28f, c.y - s * 0.33f), Offset(c.x + s * 0.28f, c.y - s * 0.33f), s * 0.035f, StrokeCap.Round)
        }
        "surprise" -> {
            drawCircle(dark, s * 0.05f, leftEye)
            drawCircle(dark, s * 0.05f, rightEye)
            drawOval(dark, topLeft = Offset(c.x - s * 0.10f, c.y + s * 0.08f), size = Size(s * 0.20f, s * 0.22f))
        }
        "disgust" -> {
            drawLine(dark, Offset(leftEye.x - s * 0.07f, leftEye.y - s * 0.02f), Offset(leftEye.x + s * 0.07f, leftEye.y + s * 0.02f), stroke.width, StrokeCap.Round)
            drawLine(dark, Offset(rightEye.x - s * 0.07f, rightEye.y + s * 0.02f), Offset(rightEye.x + s * 0.07f, rightEye.y - s * 0.02f), stroke.width, StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(c.x - s * 0.06f, c.y + s * 0.03f)
                quadraticBezierTo(c.x, c.y + s * 0.10f, c.x + s * 0.06f, c.y + s * 0.03f)
            }, color = dark, style = stroke)
            drawArc(dark, 195f, 150f, false, topLeft = Offset(c.x - s * 0.18f, c.y + s * 0.13f), size = Size(s * 0.36f, s * 0.22f), style = stroke)
        }
        else -> {
            drawCircle(dark, s * 0.04f, leftEye)
            drawCircle(dark, s * 0.04f, rightEye)
            drawLine(dark, Offset(c.x - s * 0.13f, c.y + s * 0.18f), Offset(c.x + s * 0.13f, c.y + s * 0.18f), stroke.width, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawGear(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
    repeat(8) { i ->
        val a = Math.toRadians((i * 45).toDouble())
        drawLine(
            color,
            Offset(c.x + cos(a).toFloat() * s * 0.28f, c.y + sin(a).toFloat() * s * 0.28f),
            Offset(c.x + cos(a).toFloat() * s * 0.40f, c.y + sin(a).toFloat() * s * 0.40f),
            stroke.width,
            StrokeCap.Round
        )
    }
    drawCircle(color, s * 0.22f, c, style = stroke)
    drawCircle(color, s * 0.055f, c)
}

private fun DrawScope.drawUser(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color, s * 0.14f, Offset(c.x, c.y - s * 0.17f))
    drawArc(color, 205f, 130f, false, Offset(c.x - s * 0.30f, c.y - s * 0.02f), Size(s * 0.60f, s * 0.50f), style = Stroke(s * 0.10f, cap = StrokeCap.Round))
}

private fun DrawScope.drawDocument(color: Color) {
    val s = min(size.width, size.height)
    val left = size.width * 0.25f
    val top = size.height * 0.16f
    drawRoundRect(color, Offset(left, top), Size(s * 0.50f, s * 0.68f), androidx.compose.ui.geometry.CornerRadius(s * 0.05f), style = Stroke(s * 0.065f))
    repeat(3) { i ->
        val y = top + s * (0.25f + i * 0.14f)
        drawLine(color, Offset(left + s * 0.12f, y), Offset(left + s * 0.38f, y), s * 0.045f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSparkle(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    val path = Path().apply {
        moveTo(c.x, c.y - s * 0.42f)
        lineTo(c.x + s * 0.10f, c.y - s * 0.10f)
        lineTo(c.x + s * 0.42f, c.y)
        lineTo(c.x + s * 0.10f, c.y + s * 0.10f)
        lineTo(c.x, c.y + s * 0.42f)
        lineTo(c.x - s * 0.10f, c.y + s * 0.10f)
        lineTo(c.x - s * 0.42f, c.y)
        lineTo(c.x - s * 0.10f, c.y - s * 0.10f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawWarning(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    val path = Path().apply {
        moveTo(c.x, c.y - s * 0.38f)
        lineTo(c.x + s * 0.40f, c.y + s * 0.34f)
        lineTo(c.x - s * 0.40f, c.y + s * 0.34f)
        close()
    }
    drawPath(path, color, style = Stroke(s * 0.07f, join = StrokeJoin.Round))
    drawLine(color, Offset(c.x, c.y - s * 0.10f), Offset(c.x, c.y + s * 0.12f), s * 0.06f, StrokeCap.Round)
    drawCircle(color, s * 0.035f, Offset(c.x, c.y + s * 0.25f))
}

private fun DrawScope.drawBulb(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color, s * 0.23f, Offset(c.x, c.y - s * 0.10f), style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x - s * 0.12f, c.y + s * 0.15f), Offset(c.x + s * 0.12f, c.y + s * 0.15f), s * 0.07f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.08f, c.y + s * 0.27f), Offset(c.x + s * 0.08f, c.y + s * 0.27f), s * 0.06f, StrokeCap.Round)
}

private fun DrawScope.drawLock(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.25f, c.y - s * 0.02f), Size(s * 0.50f, s * 0.38f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.07f))
    drawArc(color, 200f, 140f, false, Offset(c.x - s * 0.20f, c.y - s * 0.31f), Size(s * 0.40f, s * 0.38f), style = Stroke(s * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCamera(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.34f, c.y - s * 0.18f), Size(s * 0.68f, s * 0.45f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.07f))
    drawCircle(color, s * 0.12f, c, style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x - s * 0.16f, c.y - s * 0.25f), Offset(c.x + s * 0.02f, c.y - s * 0.25f), s * 0.07f, StrokeCap.Round)
}

private fun DrawScope.drawGamepad(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.40f, c.y - s * 0.18f), Size(s * 0.80f, s * 0.40f), androidx.compose.ui.geometry.CornerRadius(s * 0.18f), style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x - s * 0.26f, c.y), Offset(c.x - s * 0.12f, c.y), s * 0.06f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.19f, c.y - s * 0.07f), Offset(c.x - s * 0.19f, c.y + s * 0.07f), s * 0.06f, StrokeCap.Round)
    drawCircle(color, s * 0.04f, Offset(c.x + s * 0.15f, c.y - s * 0.03f))
    drawCircle(color, s * 0.04f, Offset(c.x + s * 0.27f, c.y + s * 0.04f))
}

private fun DrawScope.drawChat(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.34f, c.y - s * 0.25f), Size(s * 0.68f, s * 0.45f), androidx.compose.ui.geometry.CornerRadius(s * 0.13f), style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x - s * 0.08f, c.y + s * 0.20f), Offset(c.x - s * 0.22f, c.y + s * 0.34f), s * 0.06f, StrokeCap.Round)
}

private fun DrawScope.drawPhone(color: Color) = drawGenericPhoneLike(color)
private fun DrawScope.drawMail(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.36f, c.y - s * 0.24f), Size(s * 0.72f, s * 0.48f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x - s * 0.34f, c.y - s * 0.20f), Offset(c.x, c.y + s * 0.06f), s * 0.05f, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.34f, c.y - s * 0.20f), Offset(c.x, c.y + s * 0.06f), s * 0.05f, StrokeCap.Round)
}

private fun DrawScope.drawCalendar(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.33f, c.y - s * 0.30f), Size(s * 0.66f, s * 0.62f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.065f))
    drawLine(color, Offset(c.x - s * 0.30f, c.y - s * 0.10f), Offset(c.x + s * 0.30f, c.y - s * 0.10f), s * 0.06f, StrokeCap.Round)
}

private fun DrawScope.drawCake(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.30f, c.y), Size(s * 0.60f, s * 0.28f), androidx.compose.ui.geometry.CornerRadius(s * 0.05f), style = Stroke(s * 0.07f))
    drawLine(color, Offset(c.x, c.y - s * 0.26f), Offset(c.x, c.y - s * 0.02f), s * 0.06f, StrokeCap.Round)
    drawCircle(Color(0xFFFFD54F), s * 0.06f, Offset(c.x, c.y - s * 0.31f))
}

private fun DrawScope.drawTrophy(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.20f, c.y - s * 0.28f), Size(s * 0.40f, s * 0.36f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.07f))
    drawArc(color, 90f, 120f, false, Offset(c.x - s * 0.43f, c.y - s * 0.25f), Size(s * 0.28f, s * 0.25f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawArc(color, -30f, 120f, false, Offset(c.x + s * 0.15f, c.y - s * 0.25f), Size(s * 0.28f, s * 0.25f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawLine(color, Offset(c.x, c.y + s * 0.08f), Offset(c.x, c.y + s * 0.28f), s * 0.07f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.20f, c.y + s * 0.32f), Offset(c.x + s * 0.20f, c.y + s * 0.32f), s * 0.07f, StrokeCap.Round)
}

private fun DrawScope.drawCheck(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawLine(color, Offset(c.x - s * 0.25f, c.y), Offset(c.x - s * 0.06f, c.y + s * 0.18f), s * 0.10f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.06f, c.y + s * 0.18f), Offset(c.x + s * 0.28f, c.y - s * 0.22f), s * 0.10f, StrokeCap.Round)
}

private fun DrawScope.drawClose(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawLine(color, Offset(c.x - s * 0.23f, c.y - s * 0.23f), Offset(c.x + s * 0.23f, c.y + s * 0.23f), s * 0.08f, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.23f, c.y - s * 0.23f), Offset(c.x - s * 0.23f, c.y + s * 0.23f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.drawMicrophone(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.11f, c.y - s * 0.32f), Size(s * 0.22f, s * 0.38f), androidx.compose.ui.geometry.CornerRadius(s * 0.10f), style = Stroke(s * 0.07f))
    drawArc(color, 20f, 140f, false, Offset(c.x - s * 0.25f, c.y - s * 0.08f), Size(s * 0.50f, s * 0.35f), style = Stroke(s * 0.07f, cap = StrokeCap.Round))
    drawLine(color, Offset(c.x, c.y + s * 0.22f), Offset(c.x, c.y + s * 0.36f), s * 0.07f, StrokeCap.Round)
}

private fun DrawScope.drawEye(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawOval(color, Offset(c.x - s * 0.36f, c.y - s * 0.18f), Size(s * 0.72f, s * 0.36f), style = Stroke(s * 0.07f))
    drawCircle(color, s * 0.10f, c)
}

private fun DrawScope.drawPuzzle(color: Color) = drawGeneric(color)
private fun DrawScope.drawPalette(color: Color) = drawGeneric(color)
private fun DrawScope.drawSpeaker(color: Color) = drawGeneric(color)
private fun DrawScope.drawBell(color: Color) = drawGeneric(color)
private fun DrawScope.drawClock(color: Color) = drawGeneric(color)
private fun DrawScope.drawSave(color: Color) = drawDocument(color)
private fun DrawScope.drawExit(color: Color) = drawGenericPhoneLike(color)
private fun DrawScope.drawBook(color: Color) = drawDocument(color)
private fun DrawScope.drawGift(color: Color) = drawGeneric(color)
private fun DrawScope.drawTrash(color: Color) = drawDocument(color)

private fun DrawScope.drawGenericPhoneLike(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(color, Offset(c.x - s * 0.20f, c.y - s * 0.36f), Size(s * 0.40f, s * 0.72f), androidx.compose.ui.geometry.CornerRadius(s * 0.09f), style = Stroke(s * 0.07f))
    drawCircle(color, s * 0.025f, Offset(c.x, c.y + s * 0.26f))
}

private fun DrawScope.drawGeneric(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color.copy(alpha = 0.16f), s * 0.42f, c)
    drawCircle(color, s * 0.24f, c, style = Stroke(s * 0.075f))
    drawCircle(color, s * 0.055f, c)
}
