package com.example.appmobile.ui.state

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object UserAvatarState {
    private const val PREF_NAME = "user_avatar"
    private const val KEY_PREFIX = "avatar_uri_"

    val avatarUri = mutableStateOf<String?>(null)

    fun load(context: Context, userId: String): String? {
        val savedUri = context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + userId, null)
            ?.takeIf { it.isNotBlank() }

        avatarUri.value = savedUri
        return savedUri
    }

    fun save(context: Context, userId: String, uri: String) {
        // TODO: Upload avatar to backend and persist avatarUrl when the API supports it.
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + userId, uri)
            .apply()

        avatarUri.value = uri
    }
}
