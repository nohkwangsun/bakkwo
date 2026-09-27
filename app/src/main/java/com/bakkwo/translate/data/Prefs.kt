package com.bakkwo.translate.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, app-private storage for the Gemini API key and the last translation shown in the
 * widget. The key never leaves the device except in the direct HTTPS request to Google.
 */
object Prefs {
    private const val FILE_NAME = "bakkwo_prefs"

    private const val KEY_API_KEY = "api_key"
    private const val KEY_LAST_SOURCE = "last_source"
    private const val KEY_LAST_RESULT = "last_result"
    private const val KEY_LAST_STATE = "last_state"
    private const val KEY_CUSTOM_PROMPT = "custom_system_prompt"
    private const val KEY_CHAT_HISTORY = "chat_history"

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

    /** Null means "use the built-in default" ([GeminiClient.DEFAULT_SYSTEM_PROMPT]). */
    fun getCustomPrompt(context: Context): String? = prefs(context).getString(KEY_CUSTOM_PROMPT, null)

    fun setCustomPrompt(context: Context, prompt: String?) {
        val editor = prefs(context).edit()
        if (prompt.isNullOrBlank()) {
            editor.remove(KEY_CUSTOM_PROMPT)
        } else {
            editor.putString(KEY_CUSTOM_PROMPT, prompt)
        }
        editor.apply()
    }

    /** The prompt actually sent to Gemini: the user's override, or the built-in default. */
    fun getActivePrompt(context: Context): String =
        getCustomPrompt(context)?.takeIf { it.isNotBlank() } ?: GeminiClient.DEFAULT_SYSTEM_PROMPT

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

    /** Persisted so the in-app chat survives closing and reopening the app. */
    fun getChatHistory(context: Context): List<GeminiClient.ChatMessage> {
        val raw = prefs(context).getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                GeminiClient.ChatMessage(obj.getString("role"), obj.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveChatHistory(context: Context, messages: List<GeminiClient.ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject().put("role", message.role).put("text", message.text))
        }
        prefs(context).edit().putString(KEY_CHAT_HISTORY, array.toString()).apply()
    }

    fun clearChatHistory(context: Context) {
        prefs(context).edit().remove(KEY_CHAT_HISTORY).apply()
    }
}
