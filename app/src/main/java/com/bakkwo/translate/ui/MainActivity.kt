package com.bakkwo.translate.ui

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bakkwo.translate.R
import com.bakkwo.translate.data.GeminiClient
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

        // Hidden setting: long-press the app title to edit the raw translation prompt.
        binding.textAppTitle.setOnLongClickListener {
            showPromptEditorDialog()
            true
        }
    }

    private fun showPromptEditorDialog() {
        val editText = EditText(this).apply {
            setText(Prefs.getActivePrompt(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.prompt_editor_title)
            .setMessage(R.string.prompt_editor_message)
            .setView(container)
            .setPositiveButton(R.string.btn_save_key) { _, _ ->
                Prefs.setCustomPrompt(this, editText.text?.toString())
                Toast.makeText(this, R.string.msg_prompt_saved, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.btn_reset_prompt) { _, _ ->
                Prefs.setCustomPrompt(this, null)
                Toast.makeText(this, R.string.msg_prompt_reset, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            val prompt = Prefs.getActivePrompt(this@MainActivity)
            when (val result = GeminiClient.translate(apiKey, text, prompt)) {
                is GeminiClient.Result.Success -> {
                    binding.textResult.text = result.translation
                    Prefs.setResult(this@MainActivity, text, result.translation)
                    BakkwoWidgetProvider.updateAllWidgets(this@MainActivity)
                }
                is GeminiClient.Result.Failure -> {
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
