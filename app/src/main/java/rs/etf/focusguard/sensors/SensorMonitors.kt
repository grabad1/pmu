package rs.etf.focusguard.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.room.SensorKind
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ambient light, in lux, forwarded to [EnvironmentMonitor].
 *
 * Lifecycle-aware so registration is tied to the session service: when the service dies the
 * listener is unregistered, which matters because a leaked sensor listener drains battery.
 */
class LightLifecycleAwareMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val environmentMonitor: EnvironmentMonitor,
) : DefaultLifecycleObserver {

    private var sensorManager: SensorManager? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            environmentMonitor.onReading(SensorKind.LIGHT, event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(owner: LifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager

        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            Log.w(LOG_TAG, "No ambient light sensor on this device")
            return
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        sensorManager?.unregisterListener(listener)
        sensorManager = null
    }
}

/**
 * Phone movement, as the magnitude of linear acceleration in m/s².
 *
 * Linear acceleration is used rather than the raw accelerometer because it has gravity
 * removed, so a phone lying still reads near zero and any reading above the threshold really
 * is movement rather than orientation.
 */
class MotionLifecycleAwareMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val environmentMonitor: EnvironmentMonitor,
) : DefaultLifecycleObserver {

    private var sensorManager: SensorManager? = null
    private var usesRawAccelerometer = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)

            // The raw accelerometer includes gravity, so a still phone reads ~9.81 rather
            // than ~0. Subtracting it gives a value comparable to linear acceleration.
            val movement = if (usesRawAccelerometer) {
                abs(magnitude - SensorManager.GRAVITY_EARTH)
            } else {
                magnitude
            }
            environmentMonitor.onReading(SensorKind.MOTION, movement)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(owner: LifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager

        // Not every device reports linear acceleration; the raw accelerometer is the fallback.
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensor == null) {
            Log.w(LOG_TAG, "No motion sensor on this device")
            return
        }
        usesRawAccelerometer = sensor.type == Sensor.TYPE_ACCELEROMETER
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        sensorManager?.unregisterListener(listener)
        sensorManager = null
    }
}

/**
 * Rotation, as the magnitude of angular velocity in rad/s.
 *
 * Complements the accelerometer rather than duplicating it: a phone turned smoothly in the
 * hand barely accelerates, so linear acceleration can miss it entirely, while its orientation
 * changes unmistakably. Both mean the same thing — someone is holding the phone.
 */
class RotationLifecycleAwareMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val environmentMonitor: EnvironmentMonitor,
) : DefaultLifecycleObserver {

    private var sensorManager: SensorManager? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            environmentMonitor.onReading(SensorKind.ROTATION, sqrt(x * x + y * y + z * z))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(owner: LifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager

        val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (sensor == null) {
            Log.w(LOG_TAG, "No gyroscope on this device")
            return
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        sensorManager?.unregisterListener(listener)
        sensorManager = null
    }
}
