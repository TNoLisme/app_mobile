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
    const val CURRENT_PRIVACY_POLICY_VERSION = "1.0.0"
    const val CURRENT_TERMS_VERSION = "1.0.0"

    private const val PREF_NAME = "app_settings"
    private const val KEY_ASSISTANT_BUBBLE = "assistant_bubble_enabled"
    private const val KEY_LEARN_VIDEO_AUTOPLAY = "learn_video_autoplay_enabled"
    private const val KEY_LEARN_CONTENT_AUTOTRANSITION = "learn_content_autotransition_enabled"
    private const val KEY_LEARN_VIDEO_SOUND = "learn_video_sound_enabled"
    private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    private const val KEY_LEARNING_REMINDER = "learning_reminder_enabled"
    private const val KEY_LEARNING_REMINDER_HOUR = "learning_reminder_hour"
    private const val KEY_LEARNING_REMINDER_MINUTE = "learning_reminder_minute"
    private const val KEY_LEGAL_ACCEPTED = "legal_policy_accepted"
    private const val KEY_ACCEPTED_POLICY_VERSION = "accepted_policy_version"
    private const val KEY_ACCEPTED_TERMS_VERSION = "accepted_terms_version"
    private const val KEY_LEGAL_ACCEPTED_AT = "legal_policy_accepted_at"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    private const val KEY_THEME_MODE = "theme_mode"

    val assistantBubbleEnabled = mutableStateOf(true)
    val learnVideoAutoplayEnabled = mutableStateOf(true)
    val learnContentAutoTransitionEnabled = mutableStateOf(false)
    val learnVideoSoundEnabled = mutableStateOf(true)
    val soundEffectsEnabled = mutableStateOf(true)
    val learningReminderEnabled = mutableStateOf(false)
    val learningReminderHour = mutableStateOf(19)
    val learningReminderMinute = mutableStateOf(0)
    val legalPolicyAccepted = mutableStateOf(false)
    val acceptedPolicyVersion = mutableStateOf<String?>(null)
    val acceptedTermsVersion = mutableStateOf<String?>(null)
    val legalAcceptedAt = mutableStateOf<Long?>(null)
    val dynamicColorEnabled = mutableStateOf(false)
    val themeMode = mutableStateOf(AppThemeMode.System)
    val activeDarkTheme = mutableStateOf(false)

    fun load(context: Context) {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        assistantBubbleEnabled.value = preferences.getBoolean(KEY_ASSISTANT_BUBBLE, true)
        learnVideoAutoplayEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_AUTOPLAY, true)
        learnContentAutoTransitionEnabled.value = preferences.getBoolean(KEY_LEARN_CONTENT_AUTOTRANSITION, false)
        learnVideoSoundEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_SOUND, true)
        soundEffectsEnabled.value = preferences.getBoolean(KEY_SOUND_EFFECTS, true)
        learningReminderEnabled.value = preferences.getBoolean(KEY_LEARNING_REMINDER, false)
        learningReminderHour.value = preferences.getInt(KEY_LEARNING_REMINDER_HOUR, 19)
        learningReminderMinute.value = preferences.getInt(KEY_LEARNING_REMINDER_MINUTE, 0)
        val storedPolicyVersion = preferences.getString(KEY_ACCEPTED_POLICY_VERSION, null)
        val storedTermsVersion = preferences.getString(KEY_ACCEPTED_TERMS_VERSION, null)
        acceptedPolicyVersion.value = storedPolicyVersion
        acceptedTermsVersion.value = storedTermsVersion
        legalAcceptedAt.value = preferences.getLong(KEY_LEGAL_ACCEPTED_AT, 0L).takeIf { it > 0L }
        legalPolicyAccepted.value = preferences.getBoolean(KEY_LEGAL_ACCEPTED, false) &&
            storedPolicyVersion == CURRENT_PRIVACY_POLICY_VERSION &&
            storedTermsVersion == CURRENT_TERMS_VERSION
        dynamicColorEnabled.value = preferences.getBoolean(KEY_DYNAMIC_COLOR, false)
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

    fun setLearnContentAutoTransitionEnabled(context: Context, enabled: Boolean) {
        learnContentAutoTransitionEnabled.value = enabled
        context.settingsEditor().putBoolean(KEY_LEARN_CONTENT_AUTOTRANSITION, enabled).apply()
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
        if (accepted) {
            val acceptedAt = System.currentTimeMillis()
            acceptedPolicyVersion.value = CURRENT_PRIVACY_POLICY_VERSION
            acceptedTermsVersion.value = CURRENT_TERMS_VERSION
            legalAcceptedAt.value = acceptedAt
            context.settingsEditor()
                .putBoolean(KEY_LEGAL_ACCEPTED, true)
                .putString(KEY_ACCEPTED_POLICY_VERSION, CURRENT_PRIVACY_POLICY_VERSION)
                .putString(KEY_ACCEPTED_TERMS_VERSION, CURRENT_TERMS_VERSION)
                .putLong(KEY_LEGAL_ACCEPTED_AT, acceptedAt)
                .apply()
        } else {
            acceptedPolicyVersion.value = null
            acceptedTermsVersion.value = null
            legalAcceptedAt.value = null
            context.settingsEditor()
                .putBoolean(KEY_LEGAL_ACCEPTED, false)
                .remove(KEY_ACCEPTED_POLICY_VERSION)
                .remove(KEY_ACCEPTED_TERMS_VERSION)
                .remove(KEY_LEGAL_ACCEPTED_AT)
                .apply()
        }
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
