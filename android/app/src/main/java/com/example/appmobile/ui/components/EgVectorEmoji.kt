package com.example.appmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
    val iconSize = size
    Canvas(modifier = modifier.size(iconSize)) {
        val shouldUseTint = tint != null && (
            tint == Color.White ||
                key in MonoPreferredKeys
            )
        when {
            key in EgEmotionDisplayOrder || key == "neutral" -> drawEmotionFace(key)
            shouldUseTint -> drawMonoGlyph(key, tint ?: EgDesign.blue)
            else -> drawColorGlyph(key)
        }
    }
}

private val MonoPreferredKeys = setOf(
    "home",
    "close",
    "check",
    "edit",
    "refresh",
    "play",
    "next",
    "download",
    "fullscreen",
    "trash",
    "lock",
    "back"
)

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
        raw == "\u2B07\uFE0F" || raw == "\u2B07" || "download" in lower -> "download"
        raw == "\uD83D\uDEAA" -> "exit"
        raw == "\uD83D\uDCD6" || raw == "\uD83D\uDCDA" -> "book"
        raw == "\uD83C\uDF81" -> "gift"
        raw == "\uD83D\uDDD1\uFE0F" || raw == "\uD83D\uDDD1" -> "trash"
        "back" in lower || "undo" in lower -> "back"
        else -> lower
    }
}

private fun DrawScope.drawColorGlyph(key: String) {
    when (key) {
        "user", "child" -> drawUserBadge()
        "settings" -> drawSettingsBadge()
        "report", "document" -> drawReportBadge()
        "sparkle", "star" -> drawSparkleBadge()
        "bulb" -> drawBulbBadge()
        "warning" -> drawWarningBadge()
        "lock" -> drawLockBadge()
        "camera" -> drawCameraBadge()
        "gamepad" -> drawGamepadBadge()
        "chat" -> drawChatBadge()
        "phone" -> drawPhoneBadge()
        "mail" -> drawMailBadge()
        "calendar" -> drawCalendarBadge()
        "cake" -> drawCakeBadge()
        "trophy" -> drawTrophyBadge()
        "eye" -> drawEyeBadge()
        "target" -> drawTargetBadge()
        "puzzle" -> drawPuzzleBadge()
        "palette" -> drawPaletteBadge()
        "speaker" -> drawSpeakerBadge()
        "bell" -> drawBellBadge()
        "clock" -> drawClockBadge()
        "save" -> drawSaveBadge()
        "exit" -> drawExitBadge()
        "book" -> drawBookBadge()
        "gift" -> drawGiftBadge()
        "trash" -> drawTrashBadge()
        "microphone" -> drawMicBadge()
        "info" -> drawInfoBadge()
        "refresh" -> drawRefreshBadge()
        "edit" -> drawEditBadge()
        "back" -> drawBackArrowBadge()
        "home" -> drawMonoGlyph("home", EgDesign.blue)
        "play", "next" -> drawMonoGlyph("play", EgDesign.blue)
        "download" -> drawMonoGlyph("download", EgDesign.blue)
        "fullscreen" -> drawMonoGlyph("fullscreen", EgDesign.blue)
        else -> drawSparkleBadge()
    }
}

private fun DrawScope.drawBadgeBase(
    fill: Color,
    shadow: Color = Color(0xFF44546A).copy(alpha = 0.14f),
    border: Color = Color.White.copy(alpha = 0.72f)
) {
    val s = min(size.width, size.height)
    val c = center()
    drawCircle(shadow, radius = s * 0.46f, center = Offset(c.x, c.y + s * 0.035f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.55f), fill, fill.copy(alpha = 0.96f)),
            center = Offset(c.x - s * 0.18f, c.y - s * 0.22f),
            radius = s * 0.70f
        ),
        radius = s * 0.43f,
        center = c
    )
    drawCircle(border, radius = s * 0.43f, center = c, style = Stroke(s * 0.035f))
}

private fun DrawScope.drawEmotionFace(key: String) {
    val s = min(size.width, size.height)
    val c = center()
    val face = when (key) {
        "angry" -> Color(0xFFFF8A35)
        "fear" -> Color(0xFF86B6FF)
        "disgust" -> Color(0xFFA8D96B)
        "sad" -> Color(0xFFFFD968)
        "surprise" -> Color(0xFFFFD15B)
        else -> Color(0xFFFFD64D)
    }
    drawCircle(Color(0xFF53657A).copy(alpha = 0.16f), s * 0.46f, Offset(c.x, c.y + s * 0.035f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.72f), face, face.copy(alpha = 0.92f)),
            center = Offset(c.x - s * 0.17f, c.y - s * 0.22f),
            radius = s * 0.68f
        ),
        radius = s * 0.43f,
        center = c
    )
    if (key == "fear") {
        drawArc(
            color = Color.White.copy(alpha = 0.56f),
            startAngle = 198f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(c.x - s * 0.27f, c.y - s * 0.39f),
            size = Size(s * 0.54f, s * 0.22f),
            style = Stroke(s * 0.045f, cap = StrokeCap.Round)
        )
    }
    drawCircle(Color(0xFF7A5200).copy(alpha = 0.16f), s * 0.43f, c, style = Stroke(s * 0.024f))
    drawCircle(Color.White.copy(alpha = 0.42f), s * 0.105f, Offset(c.x - s * 0.16f, c.y - s * 0.19f))

    val dark = Color(0xFF273044)
    val stroke = Stroke(s * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val eyeY = c.y - s * 0.09f
    val le = Offset(c.x - s * 0.15f, eyeY)
    val re = Offset(c.x + s * 0.15f, eyeY)

    when (key) {
        "happy" -> {
            drawCircle(Color(0xFFFF8FB8).copy(alpha = 0.24f), s * 0.065f, Offset(c.x - s * 0.24f, c.y + s * 0.045f))
            drawCircle(Color(0xFFFF8FB8).copy(alpha = 0.24f), s * 0.065f, Offset(c.x + s * 0.24f, c.y + s * 0.045f))
            drawArc(dark, 200f, 140f, false, Offset(le.x - s * 0.075f, le.y - s * 0.035f), Size(s * 0.15f, s * 0.12f), style = stroke)
            drawArc(dark, 200f, 140f, false, Offset(re.x - s * 0.075f, re.y - s * 0.035f), Size(s * 0.15f, s * 0.12f), style = stroke)
            drawArc(dark, 18f, 144f, false, Offset(c.x - s * 0.23f, c.y - s * 0.02f), Size(s * 0.46f, s * 0.31f), style = stroke)
        }
        "sad" -> {
            drawArc(dark, 208f, 120f, false, Offset(le.x - s * 0.10f, le.y - s * 0.12f), Size(s * 0.20f, s * 0.11f), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
            drawArc(dark, 212f, 120f, false, Offset(re.x - s * 0.10f, re.y - s * 0.12f), Size(s * 0.20f, s * 0.11f), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
            drawCircle(dark, s * 0.038f, le)
            drawCircle(dark, s * 0.038f, re)
            drawPath(Path().apply {
                moveTo(re.x + s * 0.07f, re.y + s * 0.05f)
                cubicTo(re.x + s * 0.17f, re.y + s * 0.13f, re.x + s * 0.10f, re.y + s * 0.24f, re.x + s * 0.04f, re.y + s * 0.25f)
            }, Color(0xFF46BFF4), style = Stroke(s * 0.047f, cap = StrokeCap.Round))
            drawArc(dark, 205f, 130f, false, Offset(c.x - s * 0.18f, c.y + s * 0.13f), Size(s * 0.36f, s * 0.24f), style = stroke)
        }
        "angry" -> {
            drawLine(dark, Offset(le.x - s * 0.09f, le.y - s * 0.10f), Offset(le.x + s * 0.08f, le.y - s * 0.02f), s * 0.055f, StrokeCap.Round)
            drawLine(dark, Offset(re.x + s * 0.09f, re.y - s * 0.10f), Offset(re.x - s * 0.08f, re.y - s * 0.02f), s * 0.055f, StrokeCap.Round)
            drawCircle(dark, s * 0.036f, le)
            drawCircle(dark, s * 0.036f, re)
            drawArc(dark, 205f, 130f, false, Offset(c.x - s * 0.18f, c.y + s * 0.12f), Size(s * 0.36f, s * 0.20f), style = stroke)
        }
        "fear" -> {
            drawCircle(dark, s * 0.054f, le)
            drawCircle(dark, s * 0.054f, re)
            drawOval(dark, Offset(c.x - s * 0.085f, c.y + s * 0.09f), Size(s * 0.17f, s * 0.22f))
            drawCircle(Color.White.copy(alpha = 0.40f), s * 0.06f, Offset(c.x - s * 0.25f, c.y + s * 0.06f))
            drawCircle(Color.White.copy(alpha = 0.40f), s * 0.06f, Offset(c.x + s * 0.25f, c.y + s * 0.06f))
        }
        "surprise" -> {
            drawArc(dark, 202f, 136f, false, Offset(le.x - s * 0.09f, le.y - s * 0.13f), Size(s * 0.18f, s * 0.08f), style = Stroke(s * 0.033f, cap = StrokeCap.Round))
            drawArc(dark, 202f, 136f, false, Offset(re.x - s * 0.09f, re.y - s * 0.13f), Size(s * 0.18f, s * 0.08f), style = Stroke(s * 0.033f, cap = StrokeCap.Round))
            drawCircle(dark, s * 0.048f, le)
            drawCircle(dark, s * 0.048f, re)
            drawOval(dark, Offset(c.x - s * 0.10f, c.y + s * 0.08f), Size(s * 0.20f, s * 0.23f))
        }
        "disgust" -> {
            drawLine(dark, Offset(le.x - s * 0.07f, le.y - s * 0.02f), Offset(le.x + s * 0.07f, le.y + s * 0.02f), s * 0.052f, StrokeCap.Round)
            drawLine(dark, Offset(re.x - s * 0.07f, re.y + s * 0.02f), Offset(re.x + s * 0.07f, re.y - s * 0.02f), s * 0.052f, StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(c.x - s * 0.06f, c.y + s * 0.03f)
                quadraticBezierTo(c.x, c.y + s * 0.10f, c.x + s * 0.06f, c.y + s * 0.03f)
            }, dark, style = stroke)
            drawArc(dark, 198f, 144f, false, Offset(c.x - s * 0.18f, c.y + s * 0.13f), Size(s * 0.36f, s * 0.22f), style = stroke)
        }
        else -> {
            drawCircle(dark, s * 0.04f, le)
            drawCircle(dark, s * 0.04f, re)
            drawLine(dark, Offset(c.x - s * 0.13f, c.y + s * 0.17f), Offset(c.x + s * 0.13f, c.y + s * 0.17f), s * 0.052f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawUserBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFED9D), border = Color.White.copy(alpha = 0.85f))
    drawCircle(Color(0xFF0B7DE3), s * 0.105f, Offset(c.x, c.y - s * 0.10f))
    drawRoundRect(
        Color(0xFF0B7DE3),
        Offset(c.x - s * 0.22f, c.y + s * 0.04f),
        Size(s * 0.44f, s * 0.22f),
        CornerRadius(s * 0.12f, s * 0.12f)
    )
}

private fun DrawScope.drawSettingsBadge() {
    drawBadgeBase(Color(0xFFE7F4FF))
    drawGear(EgDesign.blue, fillCenter = true)
}

private fun DrawScope.drawReportBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFE9F5FF), Offset(c.x - s * 0.30f, c.y - s * 0.34f), Size(s * 0.60f, s * 0.68f), CornerRadius(s * 0.08f, s * 0.08f))
    drawRoundRect(Color.White, Offset(c.x - s * 0.22f, c.y - s * 0.24f), Size(s * 0.44f, s * 0.52f), CornerRadius(s * 0.05f, s * 0.05f))
    drawRoundRect(Color(0xFFFFA83D), Offset(c.x - s * 0.13f, c.y - s * 0.34f), Size(s * 0.26f, s * 0.12f), CornerRadius(s * 0.04f, s * 0.04f))
    repeat(3) { i ->
        val y = c.y - s * 0.11f + i * s * 0.13f
        drawLine(EgDesign.blue, Offset(c.x - s * 0.13f, y), Offset(c.x + s * 0.14f, y), s * 0.035f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSparkleBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawStar(Offset(c.x, c.y), s * 0.36f, s * 0.16f, Color(0xFFFFC928))
    drawStar(Offset(c.x - s * 0.29f, c.y - s * 0.19f), s * 0.12f, s * 0.05f, Color(0xFFFFE584))
    drawStar(Offset(c.x + s * 0.29f, c.y + s * 0.20f), s * 0.10f, s * 0.04f, Color(0xFFFFE584))
}

private fun DrawScope.drawBulbBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFF2B8), border = Color.White.copy(alpha = 0.8f))
    drawCircle(Color(0xFFFFC928), s * 0.20f, Offset(c.x, c.y - s * 0.08f))
    drawLine(Color(0xFFB7791F), Offset(c.x - s * 0.12f, c.y + s * 0.14f), Offset(c.x + s * 0.12f, c.y + s * 0.14f), s * 0.055f, StrokeCap.Round)
    drawLine(Color(0xFFB7791F), Offset(c.x - s * 0.08f, c.y + s * 0.25f), Offset(c.x + s * 0.08f, c.y + s * 0.25f), s * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawWarningBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFE2A7))
    val p = Path().apply {
        moveTo(c.x, c.y - s * 0.30f)
        lineTo(c.x + s * 0.34f, c.y + s * 0.28f)
        lineTo(c.x - s * 0.34f, c.y + s * 0.28f)
        close()
    }
    drawPath(p, Color(0xFFFFB020))
    drawLine(Color.White, Offset(c.x, c.y - s * 0.08f), Offset(c.x, c.y + s * 0.10f), s * 0.05f, StrokeCap.Round)
    drawCircle(Color.White, s * 0.032f, Offset(c.x, c.y + s * 0.22f))
}

private fun DrawScope.drawLockBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFE9F5FF))
    drawArc(EgDesign.blue, 205f, 130f, false, Offset(c.x - s * 0.18f, c.y - s * 0.31f), Size(s * 0.36f, s * 0.36f), style = Stroke(s * 0.065f, cap = StrokeCap.Round))
    drawRoundRect(EgDesign.blue, Offset(c.x - s * 0.24f, c.y - s * 0.03f), Size(s * 0.48f, s * 0.34f), CornerRadius(s * 0.06f, s * 0.06f))
}

private fun DrawScope.drawCameraBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFE8F3FF))
    drawRoundRect(Color(0xFF3A4658), Offset(c.x - s * 0.33f, c.y - s * 0.18f), Size(s * 0.66f, s * 0.45f), CornerRadius(s * 0.08f, s * 0.08f))
    drawRoundRect(Color(0xFF9CA3AF), Offset(c.x - s * 0.20f, c.y - s * 0.30f), Size(s * 0.22f, s * 0.12f), CornerRadius(s * 0.03f, s * 0.03f))
    drawCircle(Color(0xFF81D4FA), s * 0.14f, c)
    drawCircle(Color(0xFF0F172A), s * 0.09f, c)
    drawCircle(Color.White.copy(alpha = 0.75f), s * 0.03f, Offset(c.x - s * 0.03f, c.y - s * 0.04f))
}

private fun DrawScope.drawGamepadBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFF596579), Offset(c.x - s * 0.40f, c.y - s * 0.19f), Size(s * 0.80f, s * 0.40f), CornerRadius(s * 0.18f, s * 0.18f))
    drawLine(Color.White, Offset(c.x - s * 0.27f, c.y), Offset(c.x - s * 0.12f, c.y), s * 0.045f, StrokeCap.Round)
    drawLine(Color.White, Offset(c.x - s * 0.195f, c.y - s * 0.075f), Offset(c.x - s * 0.195f, c.y + s * 0.075f), s * 0.045f, StrokeCap.Round)
    drawCircle(Color(0xFF69C7FF), s * 0.045f, Offset(c.x + s * 0.16f, c.y - s * 0.03f))
    drawCircle(Color(0xFFFFD54F), s * 0.045f, Offset(c.x + s * 0.28f, c.y + s * 0.05f))
}

private fun DrawScope.drawChatBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(EgDesign.blue, Offset(c.x - s * 0.35f, c.y - s * 0.25f), Size(s * 0.70f, s * 0.47f), CornerRadius(s * 0.15f, s * 0.15f))
    drawPath(Path().apply {
        moveTo(c.x - s * 0.08f, c.y + s * 0.20f)
        lineTo(c.x - s * 0.22f, c.y + s * 0.35f)
        lineTo(c.x - s * 0.01f, c.y + s * 0.24f)
        close()
    }, EgDesign.blue)
    repeat(3) { i ->
        drawCircle(Color.White, s * 0.035f, Offset(c.x - s * 0.13f + i * s * 0.13f, c.y - s * 0.02f))
    }
}

private fun DrawScope.drawPhoneBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFF56B6F7), Offset(c.x - s * 0.20f, c.y - s * 0.34f), Size(s * 0.40f, s * 0.68f), CornerRadius(s * 0.08f, s * 0.08f))
    drawRoundRect(Color.White, Offset(c.x - s * 0.15f, c.y - s * 0.25f), Size(s * 0.30f, s * 0.47f), CornerRadius(s * 0.04f, s * 0.04f))
    drawCircle(Color(0xFF56B6F7), s * 0.018f, Offset(c.x, c.y + s * 0.27f))
}

private fun DrawScope.drawMailBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFEFF7FF), Offset(c.x - s * 0.36f, c.y - s * 0.24f), Size(s * 0.72f, s * 0.48f), CornerRadius(s * 0.07f, s * 0.07f))
    drawRoundRect(EgDesign.blue, Offset(c.x - s * 0.36f, c.y - s * 0.24f), Size(s * 0.72f, s * 0.48f), CornerRadius(s * 0.07f, s * 0.07f), style = Stroke(s * 0.04f))
    drawLine(EgDesign.blue, Offset(c.x - s * 0.31f, c.y - s * 0.17f), Offset(c.x, c.y + s * 0.06f), s * 0.035f, StrokeCap.Round)
    drawLine(EgDesign.blue, Offset(c.x + s * 0.31f, c.y - s * 0.17f), Offset(c.x, c.y + s * 0.06f), s * 0.035f, StrokeCap.Round)
}

private fun DrawScope.drawCalendarBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color.White, Offset(c.x - s * 0.33f, c.y - s * 0.31f), Size(s * 0.66f, s * 0.64f), CornerRadius(s * 0.07f, s * 0.07f))
    drawRoundRect(EgDesign.blue, Offset(c.x - s * 0.33f, c.y - s * 0.31f), Size(s * 0.66f, s * 0.64f), CornerRadius(s * 0.07f, s * 0.07f), style = Stroke(s * 0.04f))
    drawRoundRect(Color(0xFFFF7A59), Offset(c.x - s * 0.33f, c.y - s * 0.31f), Size(s * 0.66f, s * 0.18f), CornerRadius(s * 0.07f, s * 0.07f))
    drawCircle(EgDesign.blue, s * 0.035f, Offset(c.x - s * 0.14f, c.y + s * 0.03f))
    drawCircle(EgDesign.blue, s * 0.035f, Offset(c.x + s * 0.14f, c.y + s * 0.03f))
}

private fun DrawScope.drawCakeBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFFFB7C5), Offset(c.x - s * 0.30f, c.y - s * 0.02f), Size(s * 0.60f, s * 0.26f), CornerRadius(s * 0.06f, s * 0.06f))
    drawRoundRect(Color(0xFFFFE2A8), Offset(c.x - s * 0.30f, c.y + s * 0.15f), Size(s * 0.60f, s * 0.18f), CornerRadius(s * 0.04f, s * 0.04f))
    drawLine(EgDesign.blue, Offset(c.x, c.y - s * 0.28f), Offset(c.x, c.y - s * 0.05f), s * 0.04f, StrokeCap.Round)
    drawOval(Color(0xFFFFC928), Offset(c.x - s * 0.05f, c.y - s * 0.39f), Size(s * 0.10f, s * 0.12f))
}

private fun DrawScope.drawTrophyBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFF0AD))
    drawRoundRect(Color(0xFFFFC928), Offset(c.x - s * 0.19f, c.y - s * 0.28f), Size(s * 0.38f, s * 0.34f), CornerRadius(s * 0.06f, s * 0.06f))
    drawArc(Color(0xFFFFC928), 90f, 120f, false, Offset(c.x - s * 0.44f, c.y - s * 0.25f), Size(s * 0.30f, s * 0.25f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawArc(Color(0xFFFFC928), -30f, 120f, false, Offset(c.x + s * 0.14f, c.y - s * 0.25f), Size(s * 0.30f, s * 0.25f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawRoundRect(Color(0xFFEAB308), Offset(c.x - s * 0.05f, c.y + s * 0.07f), Size(s * 0.10f, s * 0.19f), CornerRadius(s * 0.04f, s * 0.04f))
    drawRoundRect(Color(0xFFEAB308), Offset(c.x - s * 0.20f, c.y + s * 0.25f), Size(s * 0.40f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
}

private fun DrawScope.drawEyeBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFEFF6FF))
    drawOval(Color.White, Offset(c.x - s * 0.36f, c.y - s * 0.18f), Size(s * 0.72f, s * 0.36f))
    drawOval(EgDesign.blue, Offset(c.x - s * 0.36f, c.y - s * 0.18f), Size(s * 0.72f, s * 0.36f), style = Stroke(s * 0.04f))
    drawCircle(Color(0xFF5EC4FF), s * 0.13f, c)
    drawCircle(Color(0xFF142033), s * 0.07f, c)
}

private fun DrawScope.drawTargetBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFEEF1))
    drawCircle(Color(0xFFFF6B6B), s * 0.30f, c)
    drawCircle(Color.White, s * 0.20f, c)
    drawCircle(Color(0xFFFF6B6B), s * 0.10f, c)
    drawLine(EgDesign.blue, Offset(c.x + s * 0.18f, c.y - s * 0.20f), Offset(c.x + s * 0.36f, c.y - s * 0.36f), s * 0.035f, StrokeCap.Round)
}

private fun DrawScope.drawPuzzleBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFEFFAF2))
    val p = Path().apply {
        moveTo(c.x - s * 0.26f, c.y - s * 0.22f)
        lineTo(c.x + s * 0.03f, c.y - s * 0.22f)
        quadraticBezierTo(c.x + s * 0.05f, c.y - s * 0.37f, c.x + s * 0.17f, c.y - s * 0.31f)
        quadraticBezierTo(c.x + s * 0.27f, c.y - s * 0.24f, c.x + s * 0.17f, c.y - s * 0.15f)
        lineTo(c.x + s * 0.31f, c.y - s * 0.15f)
        lineTo(c.x + s * 0.31f, c.y + s * 0.25f)
        lineTo(c.x - s * 0.26f, c.y + s * 0.25f)
        close()
    }
    drawPath(p, Color(0xFF6DD47E))
}

private fun DrawScope.drawPaletteBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFF3E3))
    drawOval(Color(0xFFFFD18A), Offset(c.x - s * 0.33f, c.y - s * 0.25f), Size(s * 0.66f, s * 0.52f))
    drawCircle(Color(0xFFE74C3C), s * 0.045f, Offset(c.x - s * 0.15f, c.y - s * 0.07f))
    drawCircle(Color(0xFF2E86DE), s * 0.045f, Offset(c.x + s * 0.03f, c.y - s * 0.12f))
    drawCircle(Color(0xFF2ECC71), s * 0.045f, Offset(c.x - s * 0.03f, c.y + s * 0.07f))
    drawCircle(Color.White, s * 0.08f, Offset(c.x + s * 0.20f, c.y + s * 0.10f))
}

private fun DrawScope.drawSpeakerBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawPath(Path().apply {
        moveTo(c.x - s * 0.33f, c.y - s * 0.10f)
        lineTo(c.x - s * 0.17f, c.y - s * 0.10f)
        lineTo(c.x + s * 0.02f, c.y - s * 0.26f)
        lineTo(c.x + s * 0.02f, c.y + s * 0.26f)
        lineTo(c.x - s * 0.17f, c.y + s * 0.10f)
        lineTo(c.x - s * 0.33f, c.y + s * 0.10f)
        close()
    }, Color(0xFF58B6F6))
    drawArc(Color(0xFF58B6F6), -45f, 90f, false, Offset(c.x - s * 0.05f, c.y - s * 0.23f), Size(s * 0.38f, s * 0.46f), style = Stroke(s * 0.045f, cap = StrokeCap.Round))
}

private fun DrawScope.drawBellBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFFFCA55), Offset(c.x - s * 0.20f, c.y - s * 0.25f), Size(s * 0.40f, s * 0.46f), CornerRadius(s * 0.18f, s * 0.18f))
    drawLine(Color(0xFFC27A00), Offset(c.x - s * 0.25f, c.y + s * 0.22f), Offset(c.x + s * 0.25f, c.y + s * 0.22f), s * 0.045f, StrokeCap.Round)
    drawCircle(Color(0xFFC27A00), s * 0.045f, Offset(c.x, c.y + s * 0.29f))
}

private fun DrawScope.drawClockBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawCircle(Color(0xFFEFF7FF), s * 0.36f, c)
    drawCircle(EgDesign.blue, s * 0.36f, c, style = Stroke(s * 0.045f))
    drawLine(EgDesign.blue, Offset(c.x, c.y), Offset(c.x, c.y - s * 0.18f), s * 0.045f, StrokeCap.Round)
    drawLine(EgDesign.blue, Offset(c.x, c.y), Offset(c.x + s * 0.16f, c.y + s * 0.08f), s * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawSaveBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFF58B6F6), Offset(c.x - s * 0.31f, c.y - s * 0.31f), Size(s * 0.62f, s * 0.62f), CornerRadius(s * 0.06f, s * 0.06f))
    drawRoundRect(Color.White, Offset(c.x - s * 0.19f, c.y - s * 0.23f), Size(s * 0.31f, s * 0.16f), CornerRadius(s * 0.03f, s * 0.03f))
    drawRoundRect(Color(0xFFBFE5FF), Offset(c.x - s * 0.20f, c.y + s * 0.06f), Size(s * 0.40f, s * 0.19f), CornerRadius(s * 0.03f, s * 0.03f))
}

private fun DrawScope.drawExitBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFFFE5E5), Offset(c.x - s * 0.30f, c.y - s * 0.32f), Size(s * 0.38f, s * 0.64f), CornerRadius(s * 0.05f, s * 0.05f), style = Stroke(s * 0.045f))
    drawLine(Color(0xFFE84646), Offset(c.x - s * 0.02f, c.y), Offset(c.x + s * 0.30f, c.y), s * 0.055f, StrokeCap.Round)
    drawLine(Color(0xFFE84646), Offset(c.x + s * 0.30f, c.y), Offset(c.x + s * 0.19f, c.y - s * 0.10f), s * 0.055f, StrokeCap.Round)
    drawLine(Color(0xFFE84646), Offset(c.x + s * 0.30f, c.y), Offset(c.x + s * 0.19f, c.y + s * 0.10f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawBookBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFFFF5E5), Offset(c.x - s * 0.34f, c.y - s * 0.28f), Size(s * 0.32f, s * 0.56f), CornerRadius(s * 0.04f, s * 0.04f))
    drawRoundRect(Color(0xFFEAF4FF), Offset(c.x + s * 0.02f, c.y - s * 0.28f), Size(s * 0.32f, s * 0.56f), CornerRadius(s * 0.04f, s * 0.04f))
    drawLine(EgDesign.blue, Offset(c.x, c.y - s * 0.27f), Offset(c.x, c.y + s * 0.28f), s * 0.035f, StrokeCap.Round)
}

private fun DrawScope.drawGiftBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFF3C4))
    drawRoundRect(Color(0xFFFFB020), Offset(c.x - s * 0.30f, c.y - s * 0.07f), Size(s * 0.60f, s * 0.36f), CornerRadius(s * 0.06f, s * 0.06f))
    drawRoundRect(Color(0xFFFFD76A), Offset(c.x - s * 0.34f, c.y - s * 0.18f), Size(s * 0.68f, s * 0.16f), CornerRadius(s * 0.05f, s * 0.05f))
    drawRoundRect(Color(0xFFEF4444), Offset(c.x - s * 0.05f, c.y - s * 0.18f), Size(s * 0.10f, s * 0.47f), CornerRadius(s * 0.03f, s * 0.03f))
    drawCircle(Color(0xFFEF4444), s * 0.09f, Offset(c.x - s * 0.08f, c.y - s * 0.27f), style = Stroke(s * 0.045f))
    drawCircle(Color(0xFFEF4444), s * 0.09f, Offset(c.x + s * 0.08f, c.y - s * 0.27f), style = Stroke(s * 0.045f))
}

private fun DrawScope.drawTrashBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFFFFE8E8), Offset(c.x - s * 0.24f, c.y - s * 0.12f), Size(s * 0.48f, s * 0.44f), CornerRadius(s * 0.05f, s * 0.05f), style = Stroke(s * 0.05f))
    drawLine(Color(0xFFE84646), Offset(c.x - s * 0.30f, c.y - s * 0.20f), Offset(c.x + s * 0.30f, c.y - s * 0.20f), s * 0.05f, StrokeCap.Round)
    drawLine(Color(0xFFE84646), Offset(c.x - s * 0.10f, c.y - s * 0.29f), Offset(c.x + s * 0.10f, c.y - s * 0.29f), s * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawMicBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(Color(0xFF58B6F6), Offset(c.x - s * 0.11f, c.y - s * 0.33f), Size(s * 0.22f, s * 0.40f), CornerRadius(s * 0.10f, s * 0.10f))
    drawArc(Color(0xFF58B6F6), 20f, 140f, false, Offset(c.x - s * 0.25f, c.y - s * 0.10f), Size(s * 0.50f, s * 0.36f), style = Stroke(s * 0.055f, cap = StrokeCap.Round))
    drawLine(Color(0xFF58B6F6), Offset(c.x, c.y + s * 0.22f), Offset(c.x, c.y + s * 0.35f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawInfoBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFE9F6FF))
    drawCircle(EgDesign.blue, s * 0.17f, c)
    drawCircle(Color.White, s * 0.035f, Offset(c.x, c.y - s * 0.10f))
    drawLine(Color.White, Offset(c.x, c.y - s * 0.01f), Offset(c.x, c.y + s * 0.14f), s * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawRefreshBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFE8FAF0))
    drawArc(Color(0xFF39B772), -38f, 285f, false, Offset(c.x - s * 0.25f, c.y - s * 0.25f), Size(s * 0.50f, s * 0.50f), style = Stroke(s * 0.065f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(c.x + s * 0.23f, c.y - s * 0.22f)
        lineTo(c.x + s * 0.34f, c.y - s * 0.24f)
        lineTo(c.x + s * 0.26f, c.y - s * 0.11f)
        close()
    }, Color(0xFF39B772))
}

private fun DrawScope.drawEditBadge() {
    val s = min(size.width, size.height)
    val c = center()
    drawBadgeBase(Color(0xFFFFF3D8))
    drawLine(Color(0xFFF59E0B), Offset(c.x - s * 0.20f, c.y + s * 0.18f), Offset(c.x + s * 0.20f, c.y - s * 0.22f), s * 0.075f, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(c.x - s * 0.29f, c.y + s * 0.28f)
        lineTo(c.x - s * 0.16f, c.y + s * 0.24f)
        lineTo(c.x - s * 0.25f, c.y + s * 0.14f)
        close()
    }, Color(0xFFF59E0B))
}

private fun DrawScope.drawMonoGlyph(key: String, color: Color) {
    when (key) {
        "settings" -> drawGear(color, fillCenter = false)
        "user", "child" -> {
            val s = min(size.width, size.height)
            val c = center()
            drawCircle(color, s * 0.13f, Offset(c.x, c.y - s * 0.14f))
            drawRoundRect(color, Offset(c.x - s * 0.25f, c.y + s * 0.05f), Size(s * 0.50f, s * 0.24f), CornerRadius(s * 0.13f, s * 0.13f))
        }
        "report", "document", "book" -> drawLineDocument(color)
        "sparkle", "star" -> drawStar(center(), min(size.width, size.height) * 0.34f, min(size.width, size.height) * 0.15f, color)
        "gamepad" -> drawGamepadLine(color)
        "chat" -> drawChatLine(color)
        "lock" -> drawLockLine(color)
        "camera" -> drawCameraLine(color)
        "mail" -> drawMailLine(color)
        "calendar" -> drawCalendarLine(color)
        "phone" -> drawPhoneLine(color)
        "trophy" -> drawTrophyLine(color)
        "eye" -> drawEyeLine(color)
        "target" -> drawTargetLine(color)
        "puzzle" -> drawPuzzleLine(color)
        "palette" -> drawPaletteLine(color)
        "bulb" -> drawBulbLine(color)
        "warning" -> drawWarningLine(color)
        "speaker" -> drawSpeakerBadge()
        "bell" -> drawBellBadge()
        "clock" -> drawClockBadge()
        "check" -> drawCheckLine(color)
        "close" -> drawCloseLine(color)
        "edit" -> drawEditLine(color)
        "refresh" -> drawRefreshLine(color)
        "play", "next" -> drawPlayTriangle(color)
        "download" -> drawDownloadLine(color)
        "fullscreen" -> drawFullscreenLine(color)
        "trash" -> drawTrashBadge()
        "home" -> drawHomeLine(color)
        "back" -> drawBackArrowLine(color)
        else -> drawStar(center(), min(size.width, size.height) * 0.34f, min(size.width, size.height) * 0.15f, color)
    }
}

private fun DrawScope.center(): Offset = Offset(size.width / 2f, size.height / 2f)

private fun DrawScope.drawGear(color: Color, fillCenter: Boolean) {
    val s = min(size.width, size.height)
    val c = center()
    repeat(8) { i ->
        val a = Math.toRadians((i * 45).toDouble())
        drawLine(
            color,
            Offset(c.x + cos(a).toFloat() * s * 0.25f, c.y + sin(a).toFloat() * s * 0.25f),
            Offset(c.x + cos(a).toFloat() * s * 0.39f, c.y + sin(a).toFloat() * s * 0.39f),
            s * 0.07f,
            StrokeCap.Round
        )
    }
    drawCircle(color, s * 0.23f, c, style = Stroke(s * 0.065f))
    if (fillCenter) drawCircle(color, s * 0.07f, c) else drawCircle(color, s * 0.045f, c)
}

private fun DrawScope.drawStar(center: Offset, outer: Float, inner: Float, color: Color) {
    val p = Path()
    repeat(10) { i ->
        val angle = Math.toRadians((i * 36 - 90).toDouble())
        val r = if (i % 2 == 0) outer else inner
        val point = Offset(center.x + cos(angle).toFloat() * r, center.y + sin(angle).toFloat() * r)
        if (i == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
    }
    p.close()
    drawPath(p, color)
}

private fun DrawScope.drawLineDocument(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.27f, c.y - s * 0.35f), Size(s * 0.54f, s * 0.70f), CornerRadius(s * 0.055f, s * 0.055f), style = Stroke(s * 0.06f))
    repeat(3) { i ->
        val y = c.y - s * 0.14f + i * s * 0.16f
        drawLine(color, Offset(c.x - s * 0.14f, y), Offset(c.x + s * 0.15f, y), s * 0.045f, StrokeCap.Round)
    }
}

private fun DrawScope.drawGamepadLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.40f, c.y - s * 0.18f), Size(s * 0.80f, s * 0.40f), CornerRadius(s * 0.18f, s * 0.18f), style = Stroke(s * 0.06f))
    drawLine(color, Offset(c.x - s * 0.27f, c.y), Offset(c.x - s * 0.13f, c.y), s * 0.05f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.20f, c.y - s * 0.07f), Offset(c.x - s * 0.20f, c.y + s * 0.07f), s * 0.05f, StrokeCap.Round)
    drawCircle(color, s * 0.035f, Offset(c.x + s * 0.15f, c.y - s * 0.03f))
    drawCircle(color, s * 0.035f, Offset(c.x + s * 0.27f, c.y + s * 0.04f))
}

private fun DrawScope.drawChatLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.34f, c.y - s * 0.25f), Size(s * 0.68f, s * 0.45f), CornerRadius(s * 0.13f, s * 0.13f), style = Stroke(s * 0.06f))
    drawLine(color, Offset(c.x - s * 0.08f, c.y + s * 0.20f), Offset(c.x - s * 0.22f, c.y + s * 0.34f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawLockLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawArc(color, 205f, 130f, false, Offset(c.x - s * 0.20f, c.y - s * 0.32f), Size(s * 0.40f, s * 0.38f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawRoundRect(color, Offset(c.x - s * 0.25f, c.y - s * 0.02f), Size(s * 0.50f, s * 0.36f), CornerRadius(s * 0.05f, s * 0.05f), style = Stroke(s * 0.06f))
}

private fun DrawScope.drawCameraLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.34f, c.y - s * 0.18f), Size(s * 0.68f, s * 0.45f), CornerRadius(s * 0.06f, s * 0.06f), style = Stroke(s * 0.06f))
    drawCircle(color, s * 0.12f, c, style = Stroke(s * 0.055f))
    drawLine(color, Offset(c.x - s * 0.16f, c.y - s * 0.25f), Offset(c.x + s * 0.02f, c.y - s * 0.25f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawMailLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.36f, c.y - s * 0.24f), Size(s * 0.72f, s * 0.48f), CornerRadius(s * 0.06f, s * 0.06f), style = Stroke(s * 0.055f))
    drawLine(color, Offset(c.x - s * 0.33f, c.y - s * 0.18f), Offset(c.x, c.y + s * 0.06f), s * 0.045f, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.33f, c.y - s * 0.18f), Offset(c.x, c.y + s * 0.06f), s * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawCalendarLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.33f, c.y - s * 0.30f), Size(s * 0.66f, s * 0.62f), CornerRadius(s * 0.06f, s * 0.06f), style = Stroke(s * 0.055f))
    drawLine(color, Offset(c.x - s * 0.30f, c.y - s * 0.10f), Offset(c.x + s * 0.30f, c.y - s * 0.10f), s * 0.05f, StrokeCap.Round)
}

private fun DrawScope.drawPhoneLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.20f, c.y - s * 0.36f), Size(s * 0.40f, s * 0.72f), CornerRadius(s * 0.09f, s * 0.09f), style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.025f, Offset(c.x, c.y + s * 0.26f))
}

private fun DrawScope.drawTrophyLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.20f, c.y - s * 0.28f), Size(s * 0.40f, s * 0.36f), CornerRadius(s * 0.06f, s * 0.06f), style = Stroke(s * 0.055f))
    drawLine(color, Offset(c.x, c.y + s * 0.08f), Offset(c.x, c.y + s * 0.28f), s * 0.055f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.20f, c.y + s * 0.32f), Offset(c.x + s * 0.20f, c.y + s * 0.32f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawEyeLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawOval(color, Offset(c.x - s * 0.36f, c.y - s * 0.18f), Size(s * 0.72f, s * 0.36f), style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.10f, c)
}

private fun DrawScope.drawTargetLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawCircle(color, s * 0.31f, c, style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.18f, c, style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.055f, c)
}

private fun DrawScope.drawPuzzleLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawRoundRect(color, Offset(c.x - s * 0.28f, c.y - s * 0.24f), Size(s * 0.56f, s * 0.50f), CornerRadius(s * 0.07f, s * 0.07f), style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.07f, Offset(c.x + s * 0.03f, c.y - s * 0.24f))
}

private fun DrawScope.drawPaletteLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawOval(color, Offset(c.x - s * 0.33f, c.y - s * 0.25f), Size(s * 0.66f, s * 0.52f), style = Stroke(s * 0.055f))
    drawCircle(color, s * 0.04f, Offset(c.x - s * 0.14f, c.y - s * 0.06f))
    drawCircle(color, s * 0.04f, Offset(c.x + s * 0.03f, c.y - s * 0.10f))
    drawCircle(color, s * 0.04f, Offset(c.x - s * 0.02f, c.y + s * 0.07f))
}

private fun DrawScope.drawBulbLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawCircle(color, s * 0.20f, Offset(c.x, c.y - s * 0.08f), style = Stroke(s * 0.055f))
    drawLine(color, Offset(c.x - s * 0.12f, c.y + s * 0.14f), Offset(c.x + s * 0.12f, c.y + s * 0.14f), s * 0.05f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.08f, c.y + s * 0.25f), Offset(c.x + s * 0.08f, c.y + s * 0.25f), s * 0.04f, StrokeCap.Round)
}

private fun DrawScope.drawWarningLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val p = Path().apply {
        moveTo(c.x, c.y - s * 0.35f)
        lineTo(c.x + s * 0.37f, c.y + s * 0.32f)
        lineTo(c.x - s * 0.37f, c.y + s * 0.32f)
        close()
    }
    drawPath(p, color, style = Stroke(s * 0.055f, join = StrokeJoin.Round))
    drawLine(color, Offset(c.x, c.y - s * 0.08f), Offset(c.x, c.y + s * 0.10f), s * 0.05f, StrokeCap.Round)
    drawCircle(color, s * 0.03f, Offset(c.x, c.y + s * 0.23f))
}

private fun DrawScope.drawCheckLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawLine(color, Offset(c.x - s * 0.25f, c.y), Offset(c.x - s * 0.06f, c.y + s * 0.18f), s * 0.08f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.06f, c.y + s * 0.18f), Offset(c.x + s * 0.28f, c.y - s * 0.22f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.drawCloseLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawLine(color, Offset(c.x - s * 0.23f, c.y - s * 0.23f), Offset(c.x + s * 0.23f, c.y + s * 0.23f), s * 0.065f, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.23f, c.y - s * 0.23f), Offset(c.x - s * 0.23f, c.y + s * 0.23f), s * 0.065f, StrokeCap.Round)
}

private fun DrawScope.drawEditLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawLine(color, Offset(c.x - s * 0.21f, c.y + s * 0.20f), Offset(c.x + s * 0.20f, c.y - s * 0.21f), s * 0.075f, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.28f, c.y + s * 0.28f), Offset(c.x - s * 0.13f, c.y + s * 0.23f), s * 0.055f, StrokeCap.Round)
}

private fun DrawScope.drawRefreshLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    drawArc(color, -40f, 270f, false, Offset(c.x - s * 0.29f, c.y - s * 0.29f), Size(s * 0.58f, s * 0.58f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    drawLine(color, Offset(c.x + s * 0.18f, c.y - s * 0.25f), Offset(c.x + s * 0.30f, c.y - s * 0.26f), s * 0.06f, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.18f, c.y - s * 0.25f), Offset(c.x + s * 0.20f, c.y - s * 0.12f), s * 0.06f, StrokeCap.Round)
}

private fun DrawScope.drawPlayTriangle(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val p = Path().apply {
        moveTo(c.x - s * 0.16f, c.y - s * 0.25f)
        lineTo(c.x + s * 0.24f, c.y)
        lineTo(c.x - s * 0.16f, c.y + s * 0.25f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawDownloadLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val stroke = s * 0.07f
    drawLine(color, Offset(c.x, c.y - s * 0.30f), Offset(c.x, c.y + s * 0.12f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y + s * 0.12f), Offset(c.x - s * 0.17f, c.y - s * 0.04f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y + s * 0.12f), Offset(c.x + s * 0.17f, c.y - s * 0.04f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.28f, c.y + s * 0.29f), Offset(c.x + s * 0.28f, c.y + s * 0.29f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawFullscreenLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val stroke = s * 0.055f
    drawLine(color, Offset(c.x - s * 0.32f, c.y - s * 0.12f), Offset(c.x - s * 0.32f, c.y - s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.32f, c.y - s * 0.32f), Offset(c.x - s * 0.12f, c.y - s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.32f, c.y - s * 0.12f), Offset(c.x + s * 0.32f, c.y - s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.32f, c.y - s * 0.32f), Offset(c.x + s * 0.12f, c.y - s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.32f, c.y + s * 0.12f), Offset(c.x - s * 0.32f, c.y + s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.32f, c.y + s * 0.32f), Offset(c.x - s * 0.12f, c.y + s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.32f, c.y + s * 0.12f), Offset(c.x + s * 0.32f, c.y + s * 0.32f), stroke, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.32f, c.y + s * 0.32f), Offset(c.x + s * 0.12f, c.y + s * 0.32f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawHomeLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val p = Path().apply {
        moveTo(c.x - s * 0.34f, c.y - s * 0.02f)
        lineTo(c.x, c.y - s * 0.32f)
        lineTo(c.x + s * 0.34f, c.y - s * 0.02f)
    }
    drawPath(p, color, style = Stroke(s * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRoundRect(color, Offset(c.x - s * 0.24f, c.y - s * 0.02f), Size(s * 0.48f, s * 0.32f), CornerRadius(s * 0.04f, s * 0.04f), style = Stroke(s * 0.06f))
}

private fun DrawScope.drawBackArrowBadge() {
    drawBadgeBase(Color(0xFFE9F5FF))
    drawBackArrowLine(EgDesign.blue)
}

private fun DrawScope.drawBackArrowLine(color: Color) {
    val s = min(size.width, size.height)
    val c = center()
    val strokeWidth = s * 0.12f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Horizontal line
    drawLine(color, Offset(c.x + s * 0.28f, c.y), Offset(c.x - s * 0.28f, c.y), strokeWidth, StrokeCap.Round)

    // Arrow heads
    val p = Path().apply {
        moveTo(c.x - s * 0.04f, c.y - s * 0.22f)
        lineTo(c.x - s * 0.28f, c.y)
        lineTo(c.x - s * 0.04f, c.y + s * 0.22f)
    }
    drawPath(p, color, style = stroke)
}
