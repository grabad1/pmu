package rs.etf.focusguard.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the user is currently looking at Focus Guard.
 *
 * [ProcessLifecycleOwner] reports the whole app rather than one Activity, so rotating the
 * phone or moving between screens does not register as leaving — only actually going
 * elsewhere does, which is the thing worth reacting to.
 *
 * Deliberately does not try to discover *which* app they went to. That needs the
 * `PACKAGE_USAGE_STATS` special permission, and knowing the name would not change what the
 * app does about it.
 */
@Singleton
class AppForegroundMonitor @Inject constructor() : DefaultLifecycleObserver {

    private val _isForeground = MutableStateFlow(false)
    val isForeground = _isForeground.asStateFlow()

    /** Called once, from `Application.onCreate`. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}
