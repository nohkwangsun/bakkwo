package com.bakkwo.translate.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, app-private storage for the Gemini API key, the widget's last translation, and past
 * chat sessions. Nothing here leaves the device except in direct HTTPS requests to Google.
 */
object Prefs {
    private const val FILE_NAME = "bakkwo_prefs"

    private const val KEY_API_KEY = "api_key"
    private const val KEY_LAST_SOURCE = "last_source"
    private const val KEY_LAST_RESULT = "last_result"
    private const val KEY_LAST_STATE = "last_state"
    private const val KEY_HANDOFF_PENDING = "handoff_pending"
    private const val KEY_CUSTOM_PROMPT = "custom_system_prompt"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 30

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

    fun getLastState(context: Context): String =
        prefs(context).getString(KEY_LAST_STATE, STATE_IDLE) ?: STATE_IDLE

    fun getLastResult(context: Context): String? = prefs(context).getString(KEY_LAST_RESULT, null)

    fun setLoading(context: Context, source: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SOURCE, source)
            .putString(KEY_LAST_STATE, STATE_LOADING)
            .apply()
    }

    /** Called only by the widget/share flows (never by the app's own chat — see [consumeHandoff]). */
    fun setResult(context: Context, source: String, result: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SOURCE, source)
            .putString(KEY_LAST_RESULT, result)
            .putString(KEY_LAST_STATE, STATE_DONE)
            .putBoolean(KEY_HANDOFF_PENDING, true)
            .apply()
    }

    fun setError(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST_RESULT, message)
            .putString(KEY_LAST_STATE, STATE_ERROR)
            .apply()
    }

    /**
     * Returns the widget/share exchange waiting to be shown, and marks it consumed so it is only
     * ever handed off once. Without this, every app open would re-seed the same old exchange
     * forever, which looked exactly like "the session never resets".
     */
    fun consumeHandoff(context: Context): Pair<String, String>? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_HANDOFF_PENDING, false)) return null
        p.edit().putBoolean(KEY_HANDOFF_PENDING, false).apply()
        val source = p.getString(KEY_LAST_SOURCE, null)
        val result = p.getString(KEY_LAST_RESULT, null)
        return if (!source.isNullOrBlank() && !result.isNullOrBlank()) source to result else null
    }

    // --- Past chat sessions (for the "지난 대화" list) ---

    data class SessionSummary(val id: String, val timestamp: Long, val preview: String)

    fun listSessions(context: Context): List<SessionSummary> {
        val array = sessionsArray(context)
        val summaries = mutableListOf<SessionSummary>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val messages = obj.getJSONArray("messages")
            var preview = ""
            for (m in 0 until messages.length()) {
                val message = messages.getJSONObject(m)
                if (message.getString("role") == GeminiClient.ChatMessage.ROLE_USER) {
                    preview = message.getString("text")
                    break
                }
            }
            summaries.add(SessionSummary(obj.getString("id"), obj.getLong("timestamp"), preview))
        }
        return summaries.sortedByDescending { it.timestamp }
    }

    fun getSessionMessages(context: Context, id: String): List<GeminiClient.ChatMessage> {
        val array = sessionsArray(context)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") == id) {
                val messages = obj.getJSONArray("messages")
                return (0 until messages.length()).map {
                    val m = messages.getJSONObject(it)
                    GeminiClient.ChatMessage(m.getString("role"), m.getString("text"))
                }
            }
        }
        return emptyList()
    }

    /** Upserts (by [id]) the full message list for a session, bumping it to the top of the list. */
    fun upsertSession(context: Context, id: String, messages: List<GeminiClient.ChatMessage>) {
        val existing = (0 until sessionsArray(context).length())
            .map { sessionsArray(context).getJSONObject(it) }
            .filterNot { it.getString("id") == id }

        val messagesJson = JSONArray()
        messages.forEach { m -> messagesJson.put(JSONObject().put("role", m.role).put("text", m.text)) }
        val updated = JSONObject()
            .put("id", id)
            .put("timestamp", System.currentTimeMillis())
            .put("messages", messagesJson)

        val trimmed = (existing + updated)
            .sortedByDescending { it.getLong("timestamp") }
            .take(MAX_SESSIONS)

        val result = JSONArray()
        trimmed.forEach { result.put(it) }
        prefs(context).edit().putString(KEY_SESSIONS, result.toString()).apply()
    }

    fun deleteSession(context: Context, id: String) {
        val array = sessionsArray(context)
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != id) kept.put(obj)
        }
        prefs(context).edit().putString(KEY_SESSIONS, kept.toString()).apply()
    }

    private fun sessionsArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }
}
