package com.noncey.android.ui

import android.content.Intent
import android.os.Bundle
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

        // M1 = 10dp set via Theme.Noncey.ActionBar contentInsetStart in themes.xml
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
            // M2 = 3-char gap between icon and tab name (no dash)
            supportActionBar?.title = "   $tabName"
        }
    }
}
