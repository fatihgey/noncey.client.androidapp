package com.noncey.android.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.noncey.android.NonceyApp
import com.noncey.android.databinding.FragmentAccountSettingsBinding

class AccountSettingsFragment : Fragment() {

    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as NonceyApp

        binding.etSpoolRetention.setText(app.prefs.spoolRetentionMinutes.toString())
        binding.etSpoolRetry.setText(app.prefs.spoolRetrySeconds.toString())
        binding.etCountryCode.setText(app.prefs.countryCallingCode)
        binding.switchAutoForward.isChecked = app.prefs.autoForwardEnabled

        binding.btnSave.setOnClickListener {
            val retention = binding.etSpoolRetention.text.toString().toIntOrNull()
            val retry     = binding.etSpoolRetry.text.toString().toIntOrNull()
            val code      = binding.etCountryCode.text.toString().trim()

            if (retention == null || retention < 1) {
                Toast.makeText(requireContext(), "Spool retention must be ≥ 1 minute.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (retry == null || retry < 1) {
                Toast.makeText(requireContext(), "Retry interval must be ≥ 1 second.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            app.prefs.spoolRetentionMinutes = retention
            app.prefs.spoolRetrySeconds     = retry
            app.prefs.countryCallingCode    = code
            app.prefs.autoForwardEnabled    = binding.switchAutoForward.isChecked

            app.traceLog.add("Settings saved: retention=${retention}m retry=${retry}s countryCode=$code autoForward=${binding.switchAutoForward.isChecked}")
            Toast.makeText(requireContext(), "Settings saved.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
