package com.bakkwo.translate.ui

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bakkwo.translate.R
import com.bakkwo.translate.data.AnthropicClient
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.databinding.ActivityMainBinding
import com.bakkwo.translate.widget.BakkwoWidgetProvider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var translateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputLayoutApiKey.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE

        Prefs.getApiKey(this)?.let { binding.editApiKey.setText(it) }

        binding.btnSaveKey.setOnClickListener {
            val key = binding.editApiKey.text?.toString().orEmpty()
            if (key.isBlank()) return@setOnClickListener
            Prefs.setApiKey(this, key)
            Toast.makeText(this, R.string.msg_key_saved, Toast.LENGTH_SHORT).show()
        }

        binding.editSourceText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scheduleTranslate(s?.toString().orEmpty())
            }
        })

        binding.btnCopyResult.setOnClickListener {
            val result = binding.textResult.text?.toString().orEmpty()
            if (result.isBlank()) return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("bakkwo_translation", result))
            Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnPinWidget.setOnClickListener { requestPinWidget() }
    }

    private fun scheduleTranslate(text: String) {
        translateJob?.cancel()
        if (text.isBlank()) {
            binding.textResult.text = ""
            return
        }
        translateJob = lifecycleScope.launch {
            delay(DEBOUNCE_MS)

            val apiKey = Prefs.getApiKey(this@MainActivity)
            if (apiKey.isNullOrBlank()) {
                binding.textResult.setText(R.string.msg_key_missing)
                return@launch
            }

            binding.textResult.setText(R.string.translating)
            when (val result = AnthropicClient.translate(apiKey, text)) {
                is AnthropicClient.Result.Success -> {
                    binding.textResult.text = result.translation
                    Prefs.setResult(this@MainActivity, text, result.translation)
                    BakkwoWidgetProvider.updateAllWidgets(this@MainActivity)
                }
                is AnthropicClient.Result.Failure -> {
                    binding.textResult.text = getString(R.string.translate_error) + "\n" + result.message
                }
            }
        }
    }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, BakkwoWidgetProvider::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.msg_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 600L
    }
}
