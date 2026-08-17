package rs.etf.focusguard.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.etf.focusguard.LOG_TAG
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Supplies room loudness in decibels relative to the quietest measurable level.
 *
 * An interface because the emulator has no injectable microphone: the real implementation can
 * only be verified on a device, so tests substitute a fake rather than pretending otherwise.
 */
interface NoiseSource {

    /** True when the source can actually run — e.g. the microphone permission is granted. */
    fun isAvailable(): Boolean

    fun start()

    /** Current loudness, or null when unavailable or not yet started. */
    fun readLevelDb(): Float?

    fun stop()
}

/**
 * Reads short buffers from the microphone and converts their RMS amplitude to decibels.
 *
 * Audio is never recorded, retained or written anywhere — each buffer is reduced to a single
 * number and discarded. That does not change the permission Android requires, but it is the
 * behaviour the feature depends on being true.
 */
class MicrophoneNoiseSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NoiseSource {

    private var recorder: AudioRecord? = null
    private var bufferSize: Int = 0
    private var buffer: ShortArray = ShortArray(0)

    override fun isAvailable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun start() {
        if (!isAvailable()) {
            Log.w(LOG_TAG, "Microphone permission not granted; noise monitoring disabled")
            return
        }
        if (recorder != null) return

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            Log.w(LOG_TAG, "Microphone unavailable (buffer size $minBuffer)")
            return
        }
        bufferSize = minBuffer
        buffer = ShortArray(bufferSize)

        val created = try {
            @Suppress("MissingPermission")
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Microphone denied: ${e.message}")
            null
        }

        if (created == null || created.state != AudioRecord.STATE_INITIALIZED) {
            created?.release()
            Log.w(LOG_TAG, "Could not initialise AudioRecord")
            return
        }

        recorder = created
        created.startRecording()
        Log.d(LOG_TAG, "Noise monitoring started")
    }

    override fun readLevelDb(): Float? {
        val active = recorder ?: return null
        val read = active.read(buffer, 0, bufferSize)
        if (read <= 0) return null

        var sumOfSquares = 0.0
        for (i in 0 until read) {
            val sample = buffer[i].toDouble()
            sumOfSquares += sample * sample
        }

        val rms = sqrt(sumOfSquares / read)
        if (rms <= 0.0) return 0f

        // 20·log10 of amplitude, offset so a silent room lands near zero rather than negative.
        return (20 * log10(rms)).toFloat().coerceAtLeast(0f)
    }

    override fun stop() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        buffer = ShortArray(0)
        Log.d(LOG_TAG, "Noise monitoring stopped")
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
