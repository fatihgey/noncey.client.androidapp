package com.noncey.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.noncey.android.NonceyApp
import com.noncey.android.data.ApiClient
import com.noncey.android.data.LoginRequest
import com.noncey.android.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = applicationContext as NonceyApp

        // Skip login if already authenticated
        if (app.prefs.isLoggedIn()) {
            startMain()
            return
        }

        binding.btnLogin.setOnClickListener { attemptLogin(app) }
    }

    private fun attemptLogin(app: NonceyApp) {
        val url      = binding.etUrl.text.toString().trim().trimEnd('/')
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                app.prefs.daemonUrl = url
                val service = ApiClient.rebuild(app.prefs)
                val resp    = withContext(Dispatchers.IO) {
                    service.login(LoginRequest(username, password))
                }
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    app.prefs.token          = body.token
                    app.prefs.tokenExpiresAt = body.expires_at
                    app.prefs.username       = username
                    // Refresh config cache immediately after login
                    withContext(Dispatchers.IO) { app.cache.refresh() }
                    startMain()
                } else {
                    setLoading(false)
                    Toast.makeText(this@LoginActivity,
                        "Login failed (${resp.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@LoginActivity,
                    "Cannot reach daemon: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled    = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
