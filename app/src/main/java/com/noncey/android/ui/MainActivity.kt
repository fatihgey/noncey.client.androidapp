package com.noncey.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.noncey.android.NonceyApp
import com.noncey.android.R
import com.noncey.android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val receiveSmsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "SMS receive permission denied — auto-forward will not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            receiveSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(true)
            setLogo(R.drawable.ic_noncey_logo)
        }

        // M1 = 10dp: align logo with SMS list text (card margin 8dp + padding 12dp = 20dp → M1 = 10dp)
        // Must be set on the backing Toolbar directly; ActionBar API lacks setContentInsetsAbsolute.
        val m1 = (10 * resources.displayMetrics.density).toInt()
        window.decorView
            .findViewById<Toolbar>(androidx.appcompat.R.id.action_bar)
            ?.setContentInsetsAbsolute(m1, 0)

        navController.addOnDestinationChangedListener { _, dest, _ ->
            val tabName = when (dest.id) {
                R.id.smsListFragment -> "SMS"
                R.id.accountFragment -> "Account"
                R.id.traceFragment   -> "Trace"
                else -> ""
            }
            // M2 = 3-char gap between icon and tab name (no dash)
            supportActionBar?.title = "   $tabName"
        }
    }
}
