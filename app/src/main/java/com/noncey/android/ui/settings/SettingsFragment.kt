package com.noncey.android.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.noncey.android.NonceyApp
import com.noncey.android.databinding.FragmentSettingsBinding
import com.noncey.android.ui.LoginActivity

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as NonceyApp

        // Populate current values
        binding.etUrl.setText(app.prefs.daemonUrl)
        binding.etSpoolRetention.setText(app.prefs.spoolRetentionMinutes.toString())
        binding.etSpoolRetry.setText(app.prefs.spoolRetrySeconds.toString())
        binding.etCountryCode.setText(app.prefs.countryCallingCode)
        binding.switchAutoForward.isChecked = app.prefs.autoForwardEnabled

        binding.btnSave.setOnClickListener {
            val url       = binding.etUrl.text.toString().trim().trimEnd('/')
            val retention = binding.etSpoolRetention.text.toString().toIntOrNull()
            val retry     = binding.etSpoolRetry.text.toString().toIntOrNull()
            val code      = binding.etCountryCode.text.toString().trim()

            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Daemon URL is required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (retention == null || retention < 1) {
                Toast.makeText(requireContext(), "Spool retention must be ≥ 1 minute.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (retry == null || retry < 1) {
                Toast.makeText(requireContext(), "Retry interval must be ≥ 1 second.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            app.prefs.daemonUrl             = url
            app.prefs.spoolRetentionMinutes = retention
            app.prefs.spoolRetrySeconds     = retry
            app.prefs.countryCallingCode    = code
            app.prefs.autoForwardEnabled    = binding.switchAutoForward.isChecked

            app.traceLog.add("Settings saved: url=$url retention=${retention}m retry=${retry}s countryCode=$code autoForward=${binding.switchAutoForward.isChecked}")
            Toast.makeText(requireContext(), "Settings saved.", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            app.traceLog.add("Logout: user=${app.prefs.username}")
            app.prefs.clear()
            app.cache.invalidate()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
