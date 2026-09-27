package com.bakkwo.translate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Talks to the Anthropic Messages API directly from the device. No backend server: the API key
 * the user pastes into the app is used as-is for every request.
 */
object AnthropicClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"
    private const val MODEL = "claude-haiku-4-5-20251001"

    // Baked into the app so the user never has to type a translation prompt themselves.
    private const val SYSTEM_PROMPT = """You are a translation engine embedded in a one-tap widget.
Detect the language of the user's message.
- If it is Korean, translate it into natural, fluent English.
- If it is any other language, translate it into natural, fluent Korean.
Output ONLY the translated text. No quotes, labels, explanations, or extra commentary."""

    sealed class Result {
        data class Success(val translation: String) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun translate(apiKey: String, text: String): Result = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 1024)
                put("system", SYSTEM_PROMPT)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", text)
                ))
            }

            val url = URL(ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", API_VERSION)
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
            val content = json.getJSONArray("content")
            val translated = buildString {
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.optString("type") == "text") {
                        append(block.optString("text"))
                    }
                }
            }.trim()

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
