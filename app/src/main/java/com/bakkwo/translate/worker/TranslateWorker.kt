package com.bakkwo.translate.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bakkwo.translate.R
import com.bakkwo.translate.data.GeminiClient
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.widget.BakkwoWidgetProvider

class TranslateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT).orEmpty()
        if (text.isBlank()) return Result.failure()

        val apiKey = Prefs.getApiKey(applicationContext)
        if (apiKey.isNullOrBlank()) {
            Prefs.setError(applicationContext, applicationContext.getString(R.string.msg_key_missing))
            BakkwoWidgetProvider.updateAllWidgets(applicationContext)
            return Result.failure()
        }

        val prompt = Prefs.getActivePrompt(applicationContext)

        // The widget keeps one running conversation across taps (until reset from the widget's
        // own refresh button), rather than treating every tap as an isolated one-off call.
        val sessionId = Prefs.getOrCreateWidgetSessionId(applicationContext)
        val history = Prefs.getSessionMessages(applicationContext, sessionId).toMutableList()
        history.add(GeminiClient.ChatMessage(GeminiClient.ChatMessage.ROLE_USER, text))

        return when (val result = GeminiClient.chat(apiKey, history, prompt)) {
            is GeminiClient.Result.Success -> {
                history.add(GeminiClient.ChatMessage(GeminiClient.ChatMessage.ROLE_MODEL, result.translation))
                Prefs.upsertSession(applicationContext, sessionId, history)
                Prefs.setResult(applicationContext, text, result.translation)
                BakkwoWidgetProvider.updateAllWidgets(applicationContext)
                Result.success()
            }
            is GeminiClient.Result.Failure -> {
                Prefs.setError(applicationContext, result.message)
                BakkwoWidgetProvider.updateAllWidgets(applicationContext)
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_TEXT = "text"

        fun enqueue(context: Context, text: String) {
            val request = OneTimeWorkRequestBuilder<TranslateWorker>()
                .setInputData(Data.Builder().putString(KEY_TEXT, text).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
