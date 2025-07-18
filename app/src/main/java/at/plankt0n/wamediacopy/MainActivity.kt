package at.plankt0n.wamediacopy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import at.plankt0n.wamediacopy.FoldersFragment
import at.plankt0n.wamediacopy.BlacklistFragment
import at.plankt0n.wamediacopy.LogsFragment
import at.plankt0n.wamediacopy.ReportsFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_settings -> SettingsFragment()
                R.id.nav_folders -> FoldersFragment()
                R.id.nav_blacklist -> BlacklistFragment()
                R.id.nav_logs -> LogsFragment()
                R.id.nav_reports -> ReportsFragment()
                else -> null
            }
            fragment?.let {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, it)
                    .commit()
                true
            } ?: false
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_settings
        }
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val REQ_PERMS = 1234
    }
}
