package com.bakkwo.translate.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bakkwo.translate.R
import com.bakkwo.translate.data.AnthropicClient
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

        return when (val result = AnthropicClient.translate(apiKey, text)) {
            is AnthropicClient.Result.Success -> {
                Prefs.setResult(applicationContext, text, result.translation)
                BakkwoWidgetProvider.updateAllWidgets(applicationContext)
                Result.success()
            }
            is AnthropicClient.Result.Failure -> {
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
