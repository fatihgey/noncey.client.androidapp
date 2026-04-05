package com.noncey.android.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.noncey.android.NonceyApp
import com.noncey.android.R
import com.noncey.android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as NonceyApp
        if (!app.prefs.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(true)
            setLogo(R.drawable.ic_noncey_logo)
        }

        navController.addOnDestinationChangedListener { _, dest, _ ->
            val tabName = when (dest.id) {
                R.id.smsListFragment -> "SMS"
                R.id.accountFragment -> "Account"
                else -> ""
            }
            val full = "noncey - $tabName"
            val span = SpannableString(full)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(TypefaceSpan("sans-serif"), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            supportActionBar?.title = span
        }
    }
}
