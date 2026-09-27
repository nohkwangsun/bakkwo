package com.bakkwo.translate.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local, app-private storage for the Anthropic API key and the last translation shown in the
 * widget. The key never leaves the device except in the direct HTTPS request to Anthropic.
 */
object Prefs {
    private const val FILE_NAME = "bakkwo_prefs"

    private const val KEY_API_KEY = "api_key"
    private const val KEY_LAST_SOURCE = "last_source"
    private const val KEY_LAST_RESULT = "last_result"
    private const val KEY_LAST_STATE = "last_state"

    const val STATE_IDLE = "idle"
    const val STATE_LOADING = "loading"
    const val STATE_DONE = "done"
    const val STATE_ERROR = "error"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String? = prefs(context).getString(KEY_API_KEY, null)

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean = !getApiKey(context).isNullOrBlank()

    fun getLastSource(context: Context): String? = prefs(context).getString(KEY_LAST_SOURCE, null)

    fun getLastResult(context: Context): String? = prefs(context).getString(KEY_LAST_RESULT, null)

    fun getLastState(context: Context): String =
        prefs(context).getString(KEY_LAST_STATE, STATE_IDLE) ?: STATE_IDLE

    fun setLoading(context: Context, source: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SOURCE, source)
            .putString(KEY_LAST_STATE, STATE_LOADING)
            .apply()
    }

    fun setResult(context: Context, source: String, result: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SOURCE, source)
            .putString(KEY_LAST_RESULT, result)
            .putString(KEY_LAST_STATE, STATE_DONE)
            .apply()
    }

    fun setError(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST_RESULT, message)
            .putString(KEY_LAST_STATE, STATE_ERROR)
            .apply()
    }
}
