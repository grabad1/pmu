package rs.etf.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import rs.etf.focusguard.ui.elements.FocusGuardApp
import rs.etf.focusguard.ui.elements.theme.FocusGuardTheme

@AndroidEntryPoint
class FocusGuardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FocusGuardTheme {
                FocusGuardApp()
            }
        }
    }
}
