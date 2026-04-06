package com.noncey.android.ui.trace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.noncey.android.NonceyApp
import com.noncey.android.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TraceFragment : Fragment() {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_trace, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { render(it) }
    }

    private fun render(view: View) {
        val app     = requireContext().applicationContext as NonceyApp
        val entries = app.traceLog.entries()
        val tvLog   = view.findViewById<TextView>(R.id.tvLog)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val scroll  = view.findViewById<ScrollView>(R.id.scrollView)

        if (entries.isEmpty()) {
            scroll.visibility  = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            scroll.visibility  = View.VISIBLE
            tvLog.text = entries.joinToString("\n") { e ->
                val ts = timeFmt.format(Instant.ofEpochMilli(e.epochMs))
                "$ts  ${e.message}"
            }
        }
    }
}
