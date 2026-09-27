package com.bakkwo.translate.ui

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bakkwo.translate.R
import com.bakkwo.translate.data.GeminiClient
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.databinding.ActivityMainBinding
import com.bakkwo.translate.widget.BakkwoWidgetProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val history = mutableListOf<GeminiClient.ChatMessage>()
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Every app open is a fresh session — chat history isn't persisted across restarts.
        // But if the widget (or share popup) has a translation waiting, pick up from there
        // instead of showing a blank screen with no way to see what it means.
        if (Prefs.getLastState(this) == Prefs.STATE_DONE) {
            val source = Prefs.getLastSource(this)
            val result = Prefs.getLastResult(this)
            if (!source.isNullOrBlank() && !result.isNullOrBlank()) {
                addMessage(GeminiClient.ChatMessage.ROLE_USER, source)
                addMessage(GeminiClient.ChatMessage.ROLE_MODEL, result)
            }
        }
        updateEmptyHintVisibility()

        binding.btnSend.setOnClickListener { onSendClicked() }
        binding.editSourceText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendClicked()
                true
            } else {
                false
            }
        }

        binding.btnMenu.setOnClickListener { showMenu() }
        binding.textAppTitle.setOnLongClickListener {
            showPromptEditorDialog()
            true
        }

        if (!Prefs.hasApiKey(this)) {
            showKeySetupDialog()
        }
    }

    private fun onSendClicked() {
        val text = binding.editSourceText.text?.toString()?.trim().orEmpty()
        if (text.isBlank() || sending) return

        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_key_missing, Toast.LENGTH_LONG).show()
            showKeySetupDialog()
            return
        }

        binding.editSourceText.setText("")
        addMessage(GeminiClient.ChatMessage.ROLE_USER, text)

        val thinkingBubble = addBubble(GeminiClient.ChatMessage.ROLE_MODEL, getString(R.string.translating))
        sending = true
        binding.btnSend.isEnabled = false

        val prompt = Prefs.getActivePrompt(this)
        lifecycleScope.launch {
            when (val result = GeminiClient.chat(apiKey, history.toList(), prompt)) {
                is GeminiClient.Result.Success -> {
                    thinkingBubble.text = result.translation
                    addMessage(GeminiClient.ChatMessage.ROLE_MODEL, result.translation, alreadyShown = true)
                    Prefs.setResult(this@MainActivity, text, result.translation)
                    BakkwoWidgetProvider.updateAllWidgets(this@MainActivity)
                }
                is GeminiClient.Result.Failure -> {
                    val message = getString(R.string.translate_error) + "\n" + result.message
                    thinkingBubble.text = message
                }
            }
            sending = false
            binding.btnSend.isEnabled = true
        }
    }

    /** Adds a message to this session's (in-memory only) history and the on-screen chat. */
    private fun addMessage(role: String, text: String, alreadyShown: Boolean = false) {
        history.add(GeminiClient.ChatMessage(role, text))
        if (!alreadyShown) addBubble(role, text)
        updateEmptyHintVisibility()
    }

    /** Adds only the on-screen bubble (used for the user's own message, and as a placeholder
     * for the model's reply while it's loading) and returns its TextView so callers can update
     * the text in place once the real result arrives. */
    private fun addBubble(role: String, text: String): TextView {
        val layoutRes = if (role == GeminiClient.ChatMessage.ROLE_USER) {
            R.layout.item_chat_user
        } else {
            R.layout.item_chat_model
        }
        val view = layoutInflater.inflate(layoutRes, binding.messagesContainer, false)
        val bubbleText = view.findViewById<TextView>(R.id.text_bubble)
        bubbleText.text = text
        if (role == GeminiClient.ChatMessage.ROLE_MODEL) {
            bubbleText.setOnLongClickListener {
                copyToClipboard(bubbleText.text?.toString().orEmpty())
                true
            }
        }
        binding.messagesContainer.addView(view)
        updateEmptyHintVisibility()
        binding.scrollMessages.post { binding.scrollMessages.fullScroll(View.FOCUS_DOWN) }
        return bubbleText
    }

    private fun updateEmptyHintVisibility() {
        binding.emptyHint.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("bakkwo_translation", text))
        Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showMenu() {
        PopupMenu(this, binding.btnMenu).apply {
            menu.add(0, 1, 0, R.string.menu_change_key)
            menu.add(0, 2, 1, R.string.menu_widget_help)
            menu.add(0, 3, 2, R.string.menu_edit_prompt)
            menu.add(0, 4, 3, R.string.menu_clear_chat)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showKeySetupDialog()
                    2 -> showWidgetHelpDialog()
                    3 -> showPromptEditorDialog()
                    4 -> confirmClearChat()
                }
                true
            }
        }.show()
    }

    private fun showKeySetupDialog() {
        val editText = EditText(this).apply {
            setText(Prefs.getApiKey(this@MainActivity))
            hint = getString(R.string.hint_api_key)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(editText)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.key_setup_title)
            .setMessage(if (Prefs.hasApiKey(this)) R.string.key_status_saved else R.string.key_status_missing)
            .setView(container)
            .setPositiveButton(R.string.btn_save_key) { _, _ ->
                val key = editText.text?.toString()?.trim().orEmpty()
                if (key.isNotBlank()) {
                    Prefs.setApiKey(this, key)
                    Toast.makeText(this, R.string.msg_key_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(R.string.btn_get_key) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_get_key))))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWidgetHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_help_title)
            .setMessage(R.string.widget_steps)
            .setPositiveButton(R.string.btn_pin_widget) { _, _ -> requestPinWidget() }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun showPromptEditorDialog() {
        val editText = EditText(this).apply {
            setText(Prefs.getActivePrompt(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(editText)
        }

        MaterialAlertDialogBuilder(this)
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

    private fun confirmClearChat() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_clear_chat)
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                history.clear()
                binding.messagesContainer.removeAllViews()
                updateEmptyHintVisibility()
                Toast.makeText(this, R.string.msg_chat_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, BakkwoWidgetProvider::class.java)

        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.msg_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}
