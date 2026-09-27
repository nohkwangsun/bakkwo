package com.bakkwo.translate.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.bakkwo.translate.R
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.widget.BakkwoWidgetProvider
import com.bakkwo.translate.worker.TranslateWorker

/**
 * Invisible activity launched by the widget's "번역" button. Clipboard reads are only allowed
 * while one of our own activities has focus, so this briefly gains focus, grabs the clipboard
 * text, hands the actual network call off to [TranslateWorker], and closes itself immediately
 * without ever drawing a frame.
 */
class ClipboardTranslateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
