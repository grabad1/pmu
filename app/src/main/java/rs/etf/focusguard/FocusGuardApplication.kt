package rs.etf.focusguard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

const val LOG_TAG = "FocusGuard"

@HiltAndroidApp
class FocusGuardApplication : Application()
