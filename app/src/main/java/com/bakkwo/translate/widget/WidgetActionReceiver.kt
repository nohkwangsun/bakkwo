package com.bakkwo.translate.widget

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.bakkwo.translate.R
import com.bakkwo.translate.data.Prefs

/** Handles the widget's "복사" button. Writing to the clipboard needs no special focus. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_RESULT) return

        val result = Prefs.getLastResult(context)
        if (result.isNullOrBlank() || Prefs.getLastState(context) != Prefs.STATE_DONE) {
            Toast.makeText(context, R.string.widget_placeholder_result, Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("bakkwo_translation", result))
        Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY_RESULT = "com.bakkwo.translate.action.COPY_RESULT"
    }
}
