package com.bakkwo.translate.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.bakkwo.translate.R
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.widget.BakkwoWidgetProvider
import com.bakkwo.translate.worker.TranslateWorker

/**
 * Invisible activity launched by the widget's "번역" button. Clipboard reads are only allowed
 * while one of our own activities actually has window focus, so this waits for
 * [onWindowFocusChanged] (not [onCreate], which can fire before the window is focused — reading
 * there intermittently sees an empty clipboard even when the user just copied something), grabs
 * the clipboard text, hands the actual network call off to [TranslateWorker], and closes itself
 * without ever drawing a visible frame.
 */
class ClipboardTranslateActivity : Activity() {

    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        } else {
            null
        }

        if (text.isNullOrEmpty()) {
            Toast.makeText(this, R.string.widget_empty_clipboard, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Prefs.setLoading(this, text)
        BakkwoWidgetProvider.updateAllWidgets(this)
        TranslateWorker.enqueue(applicationContext, text)

        finish()
    }
}
