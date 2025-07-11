package at.plankt0n.wamediacopy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val bottom = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SettingsFragment())
                        .commit()
                    true
                }
                R.id.nav_copied -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CopiedListFragment())
                        .commit()
                    true
                }
                R.id.nav_log -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, LogFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            bottom.selectedItemId = R.id.nav_settings
        }
    }

    private fun checkPermissions() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
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
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
