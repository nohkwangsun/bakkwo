package com.bakkwo.translate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bakkwo.translate.R
import com.bakkwo.translate.data.GeminiClient
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.databinding.ActivityShareBinding
import com.bakkwo.translate.widget.BakkwoWidgetProvider
import kotlinx.coroutines.launch

/**
 * Shown when the user selects text in any app, taps Share, and picks 바꿔. Translates
 * immediately with no extra input needed, then offers a one-tap copy.
 */
class ShareTranslateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnShareClose.setOnClickListener { finish() }
        binding.btnShareCopy.setOnClickListener {
            val text = binding.textShareResult.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("bakkwo_translation", text))
                Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        } else {
            null
        }

        if (sharedText.isNullOrEmpty()) {
            finish()
            return
        }

        translate(sharedText)
    }

    private fun translate(text: String) {
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isNullOrBlank()) {
            binding.textShareResult.setText(R.string.msg_key_missing)
            return
        }

        binding.textShareResult.setText(R.string.translating)
        lifecycleScope.launch {
            when (val result = GeminiClient.translate(apiKey, text)) {
                is GeminiClient.Result.Success -> {
                    binding.textShareResult.text = result.translation
                    Prefs.setResult(this@ShareTranslateActivity, text, result.translation)
                    BakkwoWidgetProvider.updateAllWidgets(this@ShareTranslateActivity)
                }
                is GeminiClient.Result.Failure -> {
                    binding.textShareResult.text = getString(R.string.translate_error) + "\n" + result.message
                }
            }
        }
    }
}
