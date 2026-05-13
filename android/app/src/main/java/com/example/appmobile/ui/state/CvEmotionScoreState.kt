package com.example.appmobile.ui.state

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

object CvEmotionScoreState {
    private const val PREF_NAME = "cv_emotion_scores"
    private const val KEY_PREFIX = "score_v2_"

    val version = mutableIntStateOf(0)

    fun loadScores(context: Context, userId: String): Map<String, Float> {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return cvScoreKeys.associateWith { emotionId ->
            preferences.getFloat(scoreKey(userId, emotionId), 0f).coerceIn(0f, 100f)
        }
    }

    fun saveBestScore(context: Context, userId: String, emotionId: String, score: Float) {
        val key = normalizeCvScoreEmotion(emotionId)
        val normalizedScore = score.coerceIn(0f, 100f)
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val preferenceKey = scoreKey(userId, key)
        val currentScore = preferences.getFloat(preferenceKey, 0f)
        if (normalizedScore > currentScore) {
            preferences.edit().putFloat(preferenceKey, normalizedScore).apply()
            version.intValue += 1
        }
    }

    fun clearScores(context: Context, userId: String) {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        cvScoreKeys.forEach { emotionId ->
            editor.remove(scoreKey(userId, emotionId))
        }
        editor.apply()
        version.intValue += 1
    }

    fun mergeScores(primary: Map<String, Float>, secondary: Map<String, Float>): Map<String, Float> {
        return cvScoreKeys.associateWith { emotionId ->
            val localScore = secondary[emotionId] ?: 0f
            val backendScore = primary[emotionId] ?: 0f
            if (localScore > 0f) {
                localScore
            } else {
                backendScore
            }.coerceIn(0f, 100f)
        }
    }

    private fun scoreKey(userId: String, emotionId: String): String {
        return "$KEY_PREFIX$userId:${normalizeCvScoreEmotion(emotionId)}"
    }
}

private val cvScoreKeys = listOf("happy", "sad", "surprise", "angry", "fear", "disgust")

private fun normalizeCvScoreEmotion(value: String): String {
    val lower = value.trim().lowercase()
    return when {
        lower.contains("happy") || lower.contains("vui") -> "happy"
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") -> "sad"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") -> "surprise"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so") -> "fear"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") -> "disgust"
        else -> lower
    }
}
