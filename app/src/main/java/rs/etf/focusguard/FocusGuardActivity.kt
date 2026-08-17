package rs.etf.focusguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import rs.etf.focusguard.ui.elements.FocusGuardApp
import rs.etf.focusguard.ui.elements.theme.FocusGuardTheme

@AndroidEntryPoint
class FocusGuardActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForPermissions()
        setContent {
            FocusGuardTheme {
                FocusGuardApp()
            }
        }
    }

    /**
     * Notifications carry the session timer while the app is backgrounded, and the microphone
     * drives loudness detection. Neither is required for the app to run — a denied microphone
     * simply disables noise warnings — so the result is not acted upon.
     */
    private fun askForPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }

        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
    }
}
