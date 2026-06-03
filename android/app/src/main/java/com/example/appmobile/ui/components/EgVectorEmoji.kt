package com.example.appmobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import kotlin.math.min

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
    if (key in EgEmotionDisplayOrder) {
        Image(
            painter = painterResource(id = notoEmotionDrawable(key)),
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else if (key == "neutral") {
        Canvas(modifier = modifier.size(size)) {
            drawEmotionFace(key)
        }
    } else {
        Icon(
            imageVector = egMaterialIcon(key),
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint ?: egFunctionalIconColor(key)
        )
    }
}

private fun notoEmotionDrawable(key: String): Int = when (key) {
    "happy" -> R.drawable.noto_emoji_happy
    "sad" -> R.drawable.noto_emoji_sad
    "angry" -> R.drawable.noto_emoji_angry
    "fear" -> R.drawable.noto_emoji_fear
    "surprise" -> R.drawable.noto_emoji_surprise
    "disgust" -> R.drawable.noto_emoji_disgust
    else -> R.drawable.noto_emoji_happy
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
        raw == "\u2B07\uFE0F" || raw == "\u2B07" || "download" in lower -> "download"
        raw == "\uD83D\uDEAA" -> "exit"
        raw == "\uD83D\uDCD6" || raw == "\uD83D\uDCDA" -> "book"
        raw == "\uD83C\uDF81" -> "gift"
        raw == "\uD83D\uDDD1\uFE0F" || raw == "\uD83D\uDDD1" -> "trash"
        raw == "\uD83D\uDCA7" -> "water"
        raw == "\u2600\uFE0F" || raw == "\u2600" -> "sunlight"
        "back" in lower || "undo" in lower -> "back"
        else -> lower
    }
}

private fun egMaterialIcon(key: String): ImageVector = when (key) {
    "home" -> Icons.Rounded.Home
    "back" -> Icons.AutoMirrored.Rounded.ArrowBack
    "close" -> Icons.Rounded.Close
    "check" -> Icons.Rounded.Check
    "edit" -> Icons.Rounded.Edit
    "expand" -> Icons.Rounded.ExpandMore
    "refresh" -> Icons.Rounded.Refresh
    "play" -> Icons.Rounded.PlayArrow
    "pause" -> Icons.Rounded.Pause
    "next" -> Icons.Rounded.ChevronRight
    "download" -> Icons.Rounded.FileDownload
    "fullscreen" -> Icons.Rounded.Fullscreen
    "trash" -> Icons.Rounded.DeleteOutline
    "lock" -> Icons.Rounded.Lock
    "user", "child" -> Icons.Rounded.Person
    "settings" -> Icons.Rounded.Settings
    "report", "document" -> Icons.Rounded.Description
    "sparkle", "star" -> Icons.Rounded.AutoAwesome
    "bulb" -> Icons.Rounded.Lightbulb
    "warning" -> Icons.Rounded.WarningAmber
    "camera" -> Icons.Rounded.PhotoCamera
    "gamepad" -> Icons.Rounded.SportsEsports
    "chat" -> Icons.Rounded.ChatBubbleOutline
    "phone" -> Icons.Rounded.Phone
    "mail" -> Icons.Rounded.MailOutline
    "calendar" -> Icons.Rounded.CalendarMonth
    "cake" -> Icons.Rounded.Cake
    "trophy" -> Icons.Rounded.EmojiEvents
    "microphone" -> Icons.Rounded.Mic
    "eye" -> Icons.Rounded.Visibility
    "target" -> Icons.Rounded.GpsFixed
    "puzzle" -> Icons.Rounded.Extension
    "palette" -> Icons.Rounded.Palette
    "speaker" -> Icons.AutoMirrored.Rounded.VolumeUp
    "bell" -> Icons.Rounded.NotificationsNone
    "clock" -> Icons.Rounded.Schedule
    "save" -> Icons.Rounded.Save
    "exit" -> Icons.AutoMirrored.Rounded.Logout
    "book" -> Icons.AutoMirrored.Rounded.MenuBook
    "gift" -> Icons.Rounded.CardGiftcard
    "info" -> Icons.Rounded.Info
    "mouth" -> Icons.Rounded.EmojiEmotions
    "album" -> Icons.Rounded.PhotoAlbum
    "privacy", "shield" -> Icons.Rounded.Shield
    "water" -> Icons.Rounded.WaterDrop
    "sun", "sunlight" -> Icons.Rounded.WbSunny
    "garden", "plant" -> Icons.Rounded.Yard
    else -> Icons.Rounded.AutoAwesome
}

private fun egFunctionalIconColor(key: String): Color = when (key) {
    "sparkle", "star", "bulb", "trophy", "sun", "sunlight" -> Color(0xFFF4B400)
    "warning" -> Color(0xFFF59E0B)
    "trash", "exit" -> Color(0xFFE55353)
    "check", "garden", "plant" -> Color(0xFF38A169)
    "gift" -> Color(0xFFF59E0B)
    "water" -> Color(0xFF4BA7F5)
    else -> EgDesign.blue
}

private data class EmotionIconPalette(
    val top: Color,
    val bottom: Color,
    val outline: Color,
    val halo: Color,
    val cheek: Color,
    val accent: Color
)

private fun emotionIconPalette(key: String): EmotionIconPalette = when (key) {
    "happy" -> EmotionIconPalette(
        top = Color(0xFFFFF5A8),
        bottom = Color(0xFFFFC94A),
        outline = Color(0xFFE5A91F),
        halo = Color(0xFFFFEEC2),
        cheek = Color(0xFFFF8EA3),
        accent = Color(0xFFFFB92E)
    )
    "sad" -> EmotionIconPalette(
        top = Color(0xFFFFF0B7),
        bottom = Color(0xFFFFC45F),
        outline = Color(0xFFD8952B),
        halo = Color(0xFFDFF1FF),
        cheek = Color(0xFFFFA5A5),
        accent = Color(0xFF4DB6E8)
    )
    "angry" -> EmotionIconPalette(
        top = Color(0xFFFFC08A),
        bottom = Color(0xFFFF735C),
        outline = Color(0xFFD64A34),
        halo = Color(0xFFFFE2D7),
        cheek = Color(0xFFFF785D),
        accent = Color(0xFFE3362D)
    )
    "fear" -> EmotionIconPalette(
        top = Color(0xFFCFE7FF),
        bottom = Color(0xFF83A7F5),
        outline = Color(0xFF5E7ED8),
        halo = Color(0xFFE8F1FF),
        cheek = Color(0xFFA9C5FF),
        accent = Color(0xFF5EA8FF)
    )
    "surprise" -> EmotionIconPalette(
        top = Color(0xFFFFE8A4),
        bottom = Color(0xFFFFB84D),
        outline = Color(0xFFE39A2E),
        halo = Color(0xFFFFF3CF),
        cheek = Color(0xFFFFA082),
        accent = Color(0xFF7C6EF2)
    )
    "disgust" -> EmotionIconPalette(
        top = Color(0xFFD9F5A7),
        bottom = Color(0xFF7DCC65),
        outline = Color(0xFF57A152),
        halo = Color(0xFFEAF8DF),
        cheek = Color(0xFFB9E892),
        accent = Color(0xFF4FA36B)
    )
    else -> emotionIconPalette("happy")
}

private fun DrawScope.drawEmotionFace(key: String) {
    val s = min(size.width, size.height)
    val c = center
    val palette = emotionIconPalette(key)
    val ink = Color(0xFF17324A)
    val softInk = Color(0xFF274763)
    val stroke = Stroke(s * 0.048f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    drawOval(
        Color(0xFF0B2744).copy(alpha = 0.13f),
        topLeft = Offset(c.x - s * 0.34f, c.y + s * 0.31f),
        size = Size(s * 0.68f, s * 0.15f)
    )
    drawCircle(palette.halo.copy(alpha = 0.92f), s * 0.48f, Offset(c.x, c.y + s * 0.01f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                palette.top,
                palette.bottom
            ),
            center = Offset(c.x - s * 0.16f, c.y - s * 0.22f),
            radius = s * 0.62f
        ),
        radius = s * 0.39f,
        center = c
    )
    drawCircle(palette.outline.copy(alpha = 0.44f), s * 0.39f, c, style = Stroke(s * 0.025f))
    drawCircle(Color.White.copy(alpha = 0.62f), s * 0.105f, Offset(c.x - s * 0.14f, c.y - s * 0.19f))

    val eyeY = c.y - s * 0.075f
    val leftEye = Offset(c.x - s * 0.145f, eyeY)
    val rightEye = Offset(c.x + s * 0.145f, eyeY)
    val mouthTop = c.y + s * 0.09f

    when (key) {
        "happy" -> {
            drawBlush(c, s, palette.cheek)
            drawArc(ink, 205f, 130f, false, Offset(leftEye.x - s * 0.068f, leftEye.y - s * 0.035f), Size(s * 0.136f, s * 0.115f), style = stroke)
            drawArc(ink, 205f, 130f, false, Offset(rightEye.x - s * 0.068f, rightEye.y - s * 0.035f), Size(s * 0.136f, s * 0.115f), style = stroke)
            drawArc(ink, 18f, 144f, false, Offset(c.x - s * 0.21f, mouthTop - s * 0.10f), Size(s * 0.42f, s * 0.29f), style = stroke)
            drawSmallSparkle(Offset(c.x + s * 0.29f, c.y - s * 0.24f), s * 0.055f, Color.White.copy(alpha = 0.86f))
        }
        "sad" -> {
            drawLine(softInk, Offset(leftEye.x - s * 0.070f, leftEye.y - s * 0.080f), Offset(leftEye.x + s * 0.060f, leftEye.y - s * 0.035f), s * 0.040f, StrokeCap.Round)
            drawLine(softInk, Offset(rightEye.x + s * 0.070f, rightEye.y - s * 0.080f), Offset(rightEye.x - s * 0.060f, rightEye.y - s * 0.035f), s * 0.040f, StrokeCap.Round)
            drawGlossEye(leftEye, s * 0.041f, ink)
            drawGlossEye(rightEye, s * 0.041f, ink)
            drawTeardrop(Offset(rightEye.x + s * 0.086f, rightEye.y + s * 0.145f), s * 0.080f, palette.accent)
            drawArc(ink, 205f, 130f, false, Offset(c.x - s * 0.18f, mouthTop + s * 0.045f), Size(s * 0.36f, s * 0.22f), style = stroke)
        }
        "angry" -> {
            drawCircle(palette.accent.copy(alpha = 0.18f), s * 0.43f, c)
            drawLine(ink, Offset(leftEye.x - s * 0.100f, leftEye.y - s * 0.105f), Offset(leftEye.x + s * 0.085f, leftEye.y - s * 0.020f), s * 0.052f, StrokeCap.Round)
            drawLine(ink, Offset(rightEye.x + s * 0.100f, rightEye.y - s * 0.105f), Offset(rightEye.x - s * 0.085f, rightEye.y - s * 0.020f), s * 0.052f, StrokeCap.Round)
            drawCircle(ink, s * 0.034f, leftEye)
            drawCircle(ink, s * 0.034f, rightEye)
            drawArc(ink, 205f, 130f, false, Offset(c.x - s * 0.18f, mouthTop + s * 0.045f), Size(s * 0.36f, s * 0.20f), style = stroke)
            drawLine(palette.accent, Offset(c.x - s * 0.29f, c.y - s * 0.30f), Offset(c.x - s * 0.23f, c.y - s * 0.42f), s * 0.030f, StrokeCap.Round)
            drawLine(palette.accent, Offset(c.x + s * 0.29f, c.y - s * 0.30f), Offset(c.x + s * 0.23f, c.y - s * 0.42f), s * 0.030f, StrokeCap.Round)
        }
        "fear" -> {
            drawArc(Color.White.copy(alpha = 0.62f), 198f, 144f, false, Offset(c.x - s * 0.25f, c.y - s * 0.35f), Size(s * 0.50f, s * 0.18f), style = Stroke(s * 0.040f, cap = StrokeCap.Round))
            drawLine(softInk, Offset(leftEye.x - s * 0.085f, leftEye.y - s * 0.095f), Offset(leftEye.x + s * 0.060f, leftEye.y - s * 0.040f), s * 0.041f, StrokeCap.Round)
            drawLine(softInk, Offset(rightEye.x + s * 0.085f, rightEye.y - s * 0.095f), Offset(rightEye.x - s * 0.060f, rightEye.y - s * 0.040f), s * 0.041f, StrokeCap.Round)
            drawGlossEye(leftEye, s * 0.050f, ink)
            drawGlossEye(rightEye, s * 0.050f, ink)
            drawOval(ink, Offset(c.x - s * 0.083f, mouthTop - s * 0.005f), Size(s * 0.166f, s * 0.205f))
            drawTeardrop(Offset(c.x + s * 0.265f, c.y - s * 0.205f), s * 0.073f, palette.accent)
        }
        "surprise" -> {
            drawSmallSparkle(Offset(c.x - s * 0.28f, c.y - s * 0.25f), s * 0.052f, Color.White.copy(alpha = 0.88f))
            drawGlossEye(leftEye, s * 0.054f, ink)
            drawGlossEye(rightEye, s * 0.054f, ink)
            drawOval(ink, Offset(c.x - s * 0.087f, mouthTop - s * 0.005f), Size(s * 0.174f, s * 0.225f))
            drawCircle(Color.White.copy(alpha = 0.20f), s * 0.050f, Offset(c.x, mouthTop + s * 0.065f))
        }
        "disgust" -> {
            drawLine(ink, Offset(leftEye.x - s * 0.070f, leftEye.y - s * 0.010f), Offset(leftEye.x + s * 0.065f, leftEye.y + s * 0.020f), s * 0.050f, StrokeCap.Round)
            drawLine(ink, Offset(rightEye.x - s * 0.065f, rightEye.y + s * 0.020f), Offset(rightEye.x + s * 0.070f, rightEye.y - s * 0.010f), s * 0.050f, StrokeCap.Round)
            drawLine(softInk.copy(alpha = 0.68f), Offset(c.x + s * 0.015f, c.y - s * 0.010f), Offset(c.x - s * 0.045f, c.y + s * 0.055f), s * 0.029f, StrokeCap.Round)
            drawArc(ink, 198f, 144f, false, Offset(c.x - s * 0.18f, mouthTop + s * 0.045f), Size(s * 0.36f, s * 0.21f), style = stroke)
            drawLine(palette.accent, Offset(c.x - s * 0.24f, c.y - s * 0.24f), Offset(c.x - s * 0.16f, c.y - s * 0.31f), s * 0.024f, StrokeCap.Round)
            drawLine(palette.accent, Offset(c.x + s * 0.24f, c.y - s * 0.24f), Offset(c.x + s * 0.16f, c.y - s * 0.31f), s * 0.024f, StrokeCap.Round)
        }
        else -> {
            drawGlossEye(leftEye, s * 0.042f, ink)
            drawGlossEye(rightEye, s * 0.042f, ink)
            drawLine(ink, Offset(c.x - s * 0.13f, mouthTop + s * 0.06f), Offset(c.x + s * 0.13f, mouthTop + s * 0.06f), s * 0.050f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawBlush(center: Offset, size: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.34f), size * 0.058f, Offset(center.x - size * 0.225f, center.y + size * 0.055f))
    drawCircle(color.copy(alpha = 0.34f), size * 0.058f, Offset(center.x + size * 0.225f, center.y + size * 0.055f))
}

private fun DrawScope.drawGlossEye(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
    drawCircle(Color.White.copy(alpha = 0.88f), radius * 0.30f, Offset(center.x - radius * 0.30f, center.y - radius * 0.32f))
}

private fun DrawScope.drawSmallSparkle(center: Offset, radius: Float, color: Color) {
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), radius * 0.30f, StrokeCap.Round)
    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.30f, StrokeCap.Round)
}

private fun DrawScope.drawTeardrop(center: Offset, height: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - height * 0.58f)
        cubicTo(
            center.x - height * 0.42f,
            center.y - height * 0.08f,
            center.x - height * 0.28f,
            center.y + height * 0.46f,
            center.x,
            center.y + height * 0.52f
        )
        cubicTo(
            center.x + height * 0.28f,
            center.y + height * 0.46f,
            center.x + height * 0.42f,
            center.y - height * 0.08f,
            center.x,
            center.y - height * 0.58f
        )
        close()
    }
    drawPath(path, color)
    drawCircle(Color.White.copy(alpha = 0.55f), height * 0.10f, Offset(center.x - height * 0.10f, center.y - height * 0.18f))
}
