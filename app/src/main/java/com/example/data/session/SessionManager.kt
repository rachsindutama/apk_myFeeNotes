package com.example.data.session

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)

    fun saveUserSession(email: String, name: String, isGoogle: Boolean) {
        prefs.edit().apply {
            putString("KEY_USER_EMAIL", email)
            putString("KEY_USER_NAME", name)
            putBoolean("KEY_IS_GOOGLE", isGoogle)
            putBoolean("KEY_IS_LOGGED_IN", true)
            apply()
        }
    }

    fun getLoggedInUser(): UserSession? {
        val isLoggedIn = prefs.getBoolean("KEY_IS_LOGGED_IN", false)
        if (!isLoggedIn) return null
        return UserSession(
            email = prefs.getString("KEY_USER_EMAIL", "") ?: "",
            name = prefs.getString("KEY_USER_NAME", "") ?: "",
            isGoogle = prefs.getBoolean("KEY_IS_GOOGLE", false)
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}

data class UserSession(
    val email: String,
    val name: String,
    val isGoogle: Boolean
)
