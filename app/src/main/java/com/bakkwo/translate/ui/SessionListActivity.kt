package com.bakkwo.translate.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bakkwo.translate.R
import com.bakkwo.translate.data.Prefs
import com.bakkwo.translate.databinding.ActivitySessionListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lets the user pick a past chat to reopen and continue in [MainActivity]. */
class SessionListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        val sessions = Prefs.listSessions(this)
        binding.textEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE

        val dateFormat = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        sessions.forEach { session ->
            val row = layoutInflater.inflate(R.layout.item_session, binding.sessionsContainer, false)
            row.findViewById<TextView>(R.id.text_preview).text =
                session.preview.ifBlank { getString(R.string.session_empty_preview) }
            row.findViewById<TextView>(R.id.text_date).text = dateFormat.format(Date(session.timestamp))
            row.setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SESSION_ID, session.id))
                finish()
            }
            binding.sessionsContainer.addView(row)
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
