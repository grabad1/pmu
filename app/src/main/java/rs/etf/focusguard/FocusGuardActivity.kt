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
     * Notifications carry the session timer while the app is backgrounded, the microphone
     * drives loudness detection, and the phone state tells a session when a call interrupted
     * it. None is required for the app to run — a denied microphone simply disables noise
     * warnings, a denied phone state means calls are not counted — so the result is not
     * acted upon.
     *
     * Notification access is deliberately not here: it is a special access that cannot be
     * requested with a dialog, so the home screen offers a card that opens Settings instead.
     */
    private fun askForPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
        }

        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
    }
}
