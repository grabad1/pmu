package rs.etf.focusguard.sensors

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.etf.focusguard.data.room.SensorKind
import javax.inject.Inject

/**
 * Polls the microphone for loudness while a session runs.
 *
 * Unlike the hardware sensors, which push readings, audio has to be pulled — so this owns a
 * small loop. It degrades silently when the microphone is unavailable, since noise detection
 * is one signal among several and should never block a session.
 */
class NoiseLifecycleAwareMonitor @Inject constructor(
    private val noiseSource: NoiseSource,
    private val environmentMonitor: EnvironmentMonitor,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onCreate(owner: LifecycleOwner) {
        if (!noiseSource.isAvailable()) return

        noiseSource.start()
        pollingJob = scope.launch {
            while (isActive) {
                noiseSource.readLevelDb()?.let { level ->
                    environmentMonitor.onReading(SensorKind.NOISE, level)
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        pollingJob?.cancel()
        pollingJob = null
        noiseSource.stop()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
    }
}
