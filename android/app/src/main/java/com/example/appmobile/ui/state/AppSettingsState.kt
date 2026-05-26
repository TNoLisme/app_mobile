package com.example.appmobile.ui.state

import android.content.Context
import androidx.compose.runtime.mutableStateOf

enum class AppThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.key == key } ?: System
        }
    }
}

object AppSettingsState {
    private const val PREF_NAME = "app_settings"
    private const val KEY_ASSISTANT_BUBBLE = "assistant_bubble_enabled"
    private const val KEY_LEARN_VIDEO_AUTOPLAY = "learn_video_autoplay_enabled"
    private const val KEY_LEARN_VIDEO_SOUND = "learn_video_sound_enabled"
    private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    private const val KEY_LEARNING_REMINDER = "learning_reminder_enabled"
    private const val KEY_LEARNING_REMINDER_HOUR = "learning_reminder_hour"
    private const val KEY_LEARNING_REMINDER_MINUTE = "learning_reminder_minute"
    private const val KEY_LEGAL_ACCEPTED = "legal_policy_accepted"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    private const val KEY_THEME_MODE = "theme_mode"

    val assistantBubbleEnabled = mutableStateOf(true)
    val learnVideoAutoplayEnabled = mutableStateOf(true)
    val learnVideoSoundEnabled = mutableStateOf(true)
    val soundEffectsEnabled = mutableStateOf(true)
    val learningReminderEnabled = mutableStateOf(false)
    val learningReminderHour = mutableStateOf(19)
    val learningReminderMinute = mutableStateOf(0)
    val legalPolicyAccepted = mutableStateOf(false)
    val dynamicColorEnabled = mutableStateOf(true)
    val themeMode = mutableStateOf(AppThemeMode.System)
    val activeDarkTheme = mutableStateOf(false)

    fun load(context: Context) {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        assistantBubbleEnabled.value = preferences.getBoolean(KEY_ASSISTANT_BUBBLE, true)
        learnVideoAutoplayEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_AUTOPLAY, true)
        learnVideoSoundEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_SOUND, true)
        soundEffectsEnabled.value = preferences.getBoolean(KEY_SOUND_EFFECTS, true)
        learningReminderEnabled.value = preferences.getBoolean(KEY_LEARNING_REMINDER, false)
        learningReminderHour.value = preferences.getInt(KEY_LEARNING_REMINDER_HOUR, 19)
        learningReminderMinute.value = preferences.getInt(KEY_LEARNING_REMINDER_MINUTE, 0)
        legalPolicyAccepted.value = preferences.getBoolean(KEY_LEGAL_ACCEPTED, false)
        dynamicColorEnabled.value = preferences.getBoolean(KEY_DYNAMIC_COLOR, true)
        themeMode.value = AppThemeMode.fromKey(preferences.getString(KEY_THEME_MODE, AppThemeMode.System.key))
    }

    fun setAssistantBubbleEnabled(context: Context, enabled: Boolean) {
        assistantBubbleEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_ASSISTANT_BUBBLE, enabled).apply()
    }

    fun setLearnVideoAutoplayEnabled(context: Context, enabled: Boolean) {
        learnVideoAutoplayEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_LEARN_VIDEO_AUTOPLAY, enabled).apply()
    }

    fun setLearnVideoSoundEnabled(context: Context, enabled: Boolean) {
        learnVideoSoundEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_LEARN_VIDEO_SOUND, enabled).apply()
    }

    fun setSoundEffectsEnabled(context: Context, enabled: Boolean) {
        soundEffectsEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
    }

    fun setLearningReminderEnabled(context: Context, enabled: Boolean) {
        learningReminderEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_LEARNING_REMINDER, enabled).apply()
    }

    fun setLearningReminderTime(context: Context, hour: Int, minute: Int) {
        learningReminderHour.value = hour
        learningReminderMinute.value = minute
        context.settingsEditor()
            .putInt(KEY_LEARNING_REMINDER_HOUR, hour)
            .putInt(KEY_LEARNING_REMINDER_MINUTE, minute)
            .apply()
    }

    fun setLegalPolicyAccepted(context: Context, accepted: Boolean) {
        legalPolicyAccepted.value = accepted
        context.settingsEditor().putBoolean(KEY_LEGAL_ACCEPTED, accepted).apply()
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        dynamicColorEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        themeMode.value = mode
        context.settingsEditor().putString(KEY_THEME_MODE, mode.key).apply()
    }

    fun setActiveDarkTheme(enabled: Boolean) {
        activeDarkTheme.value = enabled
    }

    fun resetLocalPreferences(context: Context) {
        context.settingsEditor().clear().apply()
        load(context)
    }

    private fun Context.settingsEditor() =
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
}
