package com.noncey.android.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noncey.android.NonceyApp
import com.noncey.android.data.ApiClient
import com.noncey.android.data.LoginRequest
import com.noncey.android.databinding.FragmentServerBinding
import com.noncey.android.ui.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerFragment : Fragment() {

    private var _binding: FragmentServerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as NonceyApp

        binding.etUrl.setText(app.prefs.daemonUrl)
        binding.etUsername.setText(app.prefs.username)

        updateButtonState(app.prefs.isLoggedIn())

        binding.btnConnect.setOnClickListener {
            val url      = binding.etUrl.text.toString().trim().trimEnd('/')
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "URL is required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                app.prefs.daemonUrl = url
                Toast.makeText(requireContext(), "URL saved.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                Toast.makeText(requireContext(), "Username is required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnConnect.isEnabled = false
            lifecycleScope.launch {
                try {
                    app.prefs.daemonUrl = url
                    val service = ApiClient.rebuild(app.prefs)
                    val resp = withContext(Dispatchers.IO) {
                        service.login(LoginRequest(username, password))
                    }
                    if (resp.isSuccessful) {
                        val body = resp.body()!!
                        app.prefs.token          = body.token
                        app.prefs.tokenExpiresAt = body.expires_at
                        app.prefs.username       = username
                        withContext(Dispatchers.IO) { app.cache.refresh() }
                        binding.etPassword.text?.clear()
                        app.traceLog.add("Login: user=$username url=$url")
                        updateButtonState(true)
                        Toast.makeText(requireContext(), "Connected.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(),
                            "Login failed (${resp.code()})", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(),
                        "Cannot reach daemon: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    if (!app.prefs.isLoggedIn()) binding.btnConnect.isEnabled = true
                }
            }
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

    private fun updateButtonState(loggedIn: Boolean) {
        binding.btnConnect.visibility = if (loggedIn) View.GONE else View.VISIBLE
        binding.btnLogout.visibility  = if (loggedIn) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
