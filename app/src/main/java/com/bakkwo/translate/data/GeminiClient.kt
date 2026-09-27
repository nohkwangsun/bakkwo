package com.bakkwo.translate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Talks to the Google Gemini API directly from the device. No backend server: the API key the
 * user pastes into the app (free tier available from Google AI Studio) is used as-is for every
 * request.
 */
object GeminiClient {

    private const val MODEL = "gemini-3.8-flash"
    private const val ENDPOINT_TEMPLATE =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    // Retried automatically: the model is momentarily overloaded (HTTP 429/503), not a real
    // failure, so a one-tap widget shouldn't make the user retry it by hand.
    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(1500, 3000)

    // Baked into the app so the user never has to type a translation prompt themselves. Can be
    // overridden per-device via the hidden prompt editor (long-press the title in MainActivity),
    // stored through Prefs.getCustomPrompt/setCustomPrompt.
    const val DEFAULT_SYSTEM_PROMPT = """You are a translation assistant embedded in a one-tap widget/app.
Detect the language of the user's message.
- If it is Korean, translate it into natural, fluent English.
- If it is any other language, translate it into natural, fluent Korean.
Reply in this shape, plain text only (no markdown, no headers):
1. The main translation on its own line.
2. If there's a genuinely useful nuance, tone difference, or a natural alternative phrasing,
   add up to two short lines for it (e.g. "격식체: ...", "더 캐주얼하게: ..."). Skip this part
   entirely for simple, unambiguous text where it wouldn't add anything.
Keep the whole reply short — a handful of lines, never a full essay."""

    sealed class Result {
        data class Success(val translation: String) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun translate(
        apiKey: String,
        text: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): Result {
        var lastFailure: Result.Failure? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAYS_MS[attempt - 1])

            when (val result = translateOnce(apiKey, text, systemPrompt)) {
                is Result.Success -> return result
                is Result.Failure -> {
                    lastFailure = result
                    if (!isRetryable(result.message)) return result
                }
            }
        }
        return lastFailure ?: Result.Failure("알 수 없는 오류")
    }

    private fun isRetryable(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("high demand") ||
            lower.contains("overloaded") ||
            lower.contains("unavailable") ||
            lower.contains("http 429") ||
            lower.contains("http 503")
    }

    private suspend fun translateOnce(
        apiKey: String,
        text: String,
        systemPrompt: String
    ): Result = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("system_instruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", systemPrompt))
                ))
                put("contents", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0)
                    put("maxOutputTokens", 400)
                    // Translation doesn't need extended reasoning; disabling "thinking" cuts
                    // latency noticeably on models that otherwise think by default.
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }

            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            val url = URL(ENDPOINT_TEMPLATE.format(MODEL, encodedKey))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.let { readAll(it) } ?: ""

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $status")
                return@withContext Result.Failure(message)
            }

            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates")
            val translated = if (candidates != null && candidates.length() > 0) {
                val parts = candidates.getJSONObject(0).getJSONObject("content").optJSONArray("parts")
                buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            append(parts.getJSONObject(i).optString("text"))
                        }
                    }
                }.trim()
            } else {
                ""
            }

            if (translated.isEmpty()) {
                Result.Failure("빈 응답을 받았습니다")
            } else {
                Result.Success(translated)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): String {
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            return reader.readText()
        }
    }
}
