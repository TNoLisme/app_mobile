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
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    private const val KEY_THEME_MODE = "theme_mode"

    val assistantBubbleEnabled = mutableStateOf(false)
    val learnVideoAutoplayEnabled = mutableStateOf(true)
    val learnVideoSoundEnabled = mutableStateOf(true)
    val dynamicColorEnabled = mutableStateOf(true)
    val themeMode = mutableStateOf(AppThemeMode.System)
    val activeDarkTheme = mutableStateOf(false)

    fun load(context: Context) {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        assistantBubbleEnabled.value = preferences.getBoolean(KEY_ASSISTANT_BUBBLE, false)
        learnVideoAutoplayEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_AUTOPLAY, true)
        learnVideoSoundEnabled.value = preferences.getBoolean(KEY_LEARN_VIDEO_SOUND, true)
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
