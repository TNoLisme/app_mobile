package com.example.appmobile.data.local

import android.content.Context

object AppSession {
    private const val PREF_NAME = "app_session"
    private const val KEY_BACKEND_USER_ID = "backend_user_id"
    @Volatile private var cachedBackendUserId: String? = null

    fun saveBackendUserId(context: Context, userId: String) {
        cachedBackendUserId = userId
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_USER_ID, userId)
            .apply()
    }

    fun getBackendUserId(context: Context): String? {
        val userId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_USER_ID, null)
            ?.takeIf { it.isNotBlank() }
        cachedBackendUserId = userId
        return userId
    }

    fun currentBackendUserId(): String? {
        return cachedBackendUserId?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        cachedBackendUserId = null
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BACKEND_USER_ID)
            .apply()
    }
}
