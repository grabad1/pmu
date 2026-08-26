package rs.etf.focusguard.interruptions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.InterruptionRecorder
import javax.inject.Inject

/**
 * Notices incoming calls during a session.
 *
 * A call is the one interruption nobody chooses and nobody can ignore, which is why it is
 * tracked separately from notifications — but it still never costs the user any score.
 *
 * Lifecycle-aware and attached to the session service, exactly like the sensors, so it stops
 * listening when the session does.
 */
class CallLifecycleAwareMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recorder: InterruptionRecorder,
) : DefaultLifecycleObserver {

    private var telephonyManager: TelephonyManager? = null
    private var callback: Any? = null

    /**
     * Only the transition *into* ringing counts. The state is reported again when the call is
     * answered and when it ends, and one call should be one interruption.
     */
    private var wasRinging = false

    override fun onCreate(owner: LifecycleOwner) {
        if (!hasPermission()) {
            Log.d(LOG_TAG, "No READ_PHONE_STATE permission; not watching for calls")
            return
        }

        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (manager == null) {
            Log.w(LOG_TAG, "No telephony on this device; not watching for calls")
            return
        }
        telephonyManager = manager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val modern = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            callback = modern
            manager.registerTelephonyCallback(context.mainExecutor, modern)
        } else {
            // PhoneStateListener is deprecated but is the only option below Android 12, and
            // the app supports API 26.
            @Suppress("DEPRECATION")
            val legacy = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    handleState(state)
            }
            callback = legacy
            @Suppress("DEPRECATION")
            manager.listen(legacy, PhoneStateListener.LISTEN_CALL_STATE)
        }
        Log.d(LOG_TAG, "Call monitoring started")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        val manager = telephonyManager ?: return
        when (val current = callback) {
            is TelephonyCallback ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager.unregisterTelephonyCallback(current)
                }

            is PhoneStateListener -> {
                @Suppress("DEPRECATION")
                manager.listen(current, PhoneStateListener.LISTEN_NONE)
            }
        }
        callback = null
        telephonyManager = null
        Log.d(LOG_TAG, "Call monitoring stopped")
    }

    private fun handleState(state: Int) {
        val ringing = state == TelephonyManager.CALL_STATE_RINGING
        if (ringing && !wasRinging) {
            recorder.recordCall(packageName = dialerPackage())
        }
        wasRinging = ringing
    }

    /**
     * Calls have no originating app the way notifications do, so the phone's own dialer is
     * used as the source. That keeps the interruptions list uniform — every entry is
     * attributed to something the user recognises.
     */
    private fun dialerPackage(): String = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull() ?: DEFAULT_DIALER

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val DEFAULT_DIALER = "com.android.dialer"
    }
}
