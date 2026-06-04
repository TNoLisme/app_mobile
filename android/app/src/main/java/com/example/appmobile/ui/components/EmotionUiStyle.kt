package com.example.appmobile.ui.components

import androidx.compose.ui.graphics.Color
import com.example.appmobile.ui.catalog.EmotionUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog

val EgEmotionDisplayOrder = listOf("happy", "sad", "angry", "fear", "surprise", "disgust")

val EgEmotionCardBackground = Color(0xFFF8FBFF)
val EgEmotionCardSelectedBackground = Color(0xFFF3F9FF)
val EgEmotionCardBorder = Color(0xFFDCE7F2)
val EgEmotionCardSelectedBorder = Color(0xFF2F80ED)
val EgEmotionCardText = Color(0xFF1F2A44)
val EgEmotionCardSelectedText = Color(0xFF0B3D91)

fun egEmotionKey(emotion: EmotionUiItem): String {
    val idKey = egEmotionKey(emotion.id)
    if (idKey in EgEmotionDisplayOrder) return idKey

    val nameKey = egEmotionKey(emotion.name)
    if (nameKey in EgEmotionDisplayOrder) return nameKey

    return idKey
}

fun egEmotionKey(value: String): String {
    val lower = value.trim().lowercase()
    return when {
        lower.contains("happy") || lower.contains("vui") -> "happy"
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") || lower.contains("buá") -> "sad"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") || lower.contains("tá") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so") || lower.contains("sá") -> "fear"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") || lower.contains("ngá") -> "surprise"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") || lower.contains("ghã") -> "disgust"
        else -> lower
    }
}

fun egEmotionDisplayName(emotion: EmotionUiItem): String = egEmotionDisplayName(egEmotionKey(emotion))

fun egEmotionDisplayName(value: String): String {
    return when (egEmotionKey(value)) {
        "happy" -> "Vui vẻ"
        "sad" -> "Buồn bã"
        "angry" -> "Tức giận"
        "fear" -> "Sợ hãi"
        "surprise" -> "Ngạc nhiên"
        "disgust" -> "Ghê tởm"
        else -> value
    }
}

fun egEmotionRouteValue(value: String): String {
    return when (egEmotionKey(value)) {
        "happy" -> "vui vẻ"
        "sad" -> "buồn bã"
        "angry" -> "tức giận"
        "fear" -> "sợ hãi"
        "surprise" -> "ngạc nhiên"
        "disgust" -> "ghê tởm"
        else -> value
    }
}

fun egEmotionIcon(value: String): String {
    return when (egEmotionKey(value)) {
        "happy" -> "happy"
        "sad" -> "sad"
        "angry" -> "angry"
        "fear" -> "fear"
        "surprise" -> "surprise"
        "disgust" -> "disgust"
        else -> "neutral"
    }
}

fun egLearningEmotionGridItems(emotions: List<EmotionUiItem>): List<EmotionUiItem> {
    val emotionsByKey = emotions.groupBy { egEmotionKey(it) }
    return EgEmotionDisplayOrder.map { key ->
        emotionsByKey[key]?.firstOrNull()
            ?: GameUiCatalog.emotionById(key)
            ?: EmotionUiItem(
                id = key,
                name = egEmotionDisplayName(key),
                emoji = egEmotionIcon(key),
                description = ""
            )
    }
}
