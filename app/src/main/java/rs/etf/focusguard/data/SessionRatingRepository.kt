package rs.etf.focusguard.data

import android.util.Log
import com.google.gson.Gson
import rs.etf.focusguard.BuildConfig
import rs.etf.focusguard.LOG_TAG
import rs.etf.focusguard.data.retrofit.GeminiApi
import rs.etf.focusguard.data.retrofit.GeminiRequest
import rs.etf.focusguard.data.retrofit.SessionRatingJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Produces the rating shown against a finished session.
 *
 * Always returns a rating. A missing key, a flat network, an exhausted quota or a malformed
 * reply all fall back to [LocalSessionRater], because a session the user actually completed
 * should never end up unscored because of something outside their control.
 */
@Singleton
class SessionRatingRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val gson: Gson,
) {
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    val isAiConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun rate(summary: SessionSummary): SessionRating {
        val local = LocalSessionRater.rate(summary)

        if (!isAiConfigured) {
            Log.d(LOG_TAG, "No Gemini key configured; using local rating")
            return local
        }

        return runCatching { requestAiRating(summary) }
            .onFailure { Log.w(LOG_TAG, "Gemini rating failed: ${it.message}") }
            .getOrNull()
            ?: local
    }

    private suspend fun requestAiRating(summary: SessionSummary): SessionRating? {
        val response = geminiApi.generateContent(
            model = GeminiApi.DEFAULT_MODEL,
            apiKey = apiKey,
            request = GeminiRequest.ofPrompt(buildPrompt(summary)),
        )

        val text = response.text?.trim()
        if (text.isNullOrEmpty()) {
            Log.w(LOG_TAG, "Gemini returned no text")
            return null
        }

        val parsed = runCatching { gson.fromJson(text, SessionRatingJson::class.java) }.getOrNull()
        val score = parsed?.score
        val comment = parsed?.comment?.takeIf { it.isNotBlank() }
        val analysis = parsed?.analysis?.takeIf { it.isNotBlank() }

        if (score == null || comment == null || analysis == null) {
            Log.w(LOG_TAG, "Gemini reply missing fields: $text")
            return null
        }

        return SessionRating(
            score = score.coerceIn(0, 100),
            comment = comment,
            analysis = analysis,
        )
    }

    /**
     * The prompt states the facts and the app's opinion of what matters, then constrains the
     * output shape. It deliberately does not send the local score: an independent judgement
     * is more useful than a rubber stamp.
     */
    private fun buildPrompt(summary: SessionSummary): String = buildString {
        appendLine(
            "You are reviewing one focus session from a productivity app. " +
                "Judge it and reply with JSON only."
        )
        appendLine()
        appendLine("Facts:")
        appendLine("- Session name: ${summary.name}")
        appendLine("- Goal: ${summary.goalMinutes} minutes of focus")
        appendLine(
            "- Actually focused: ${summary.focusedDescription} " +
                "(${(summary.goalCompletion * 100).roundToInt()}% of the goal), pauses excluded"
        )
        appendLine(
            "- Planned pauses: ${summary.plannedPausesTaken} taken of " +
                "${summary.plannedPauseCount} planned"
        )
        appendLine(
            "- Unplanned pauses: ${summary.unplannedPauseCount}, " +
                "totalling ${summary.unplannedPauseMinutes} minutes"
        )
        appendLine("- Share of readings in poor light: ${summary.darkFraction.asPercent()}")
        appendLine("- Share of readings in a loud room: ${summary.loudFraction.asPercent()}")
        appendLine("- Share of readings with the phone moving: ${summary.movementFraction.asPercent()}")
        appendLine()
        appendLine("How to weigh it:")
        appendLine("- Reaching the goal matters most.")
        appendLine("- Exceeding the goal is a success, not a problem.")
        appendLine("- Unplanned pauses signal lost concentration and should lower the score.")
        appendLine("- Taking planned pauses is discipline and should not lower the score.")
        appendLine("- Darkness, noise and handling the phone all work against focus.")
        appendLine()
        appendLine("Reply with exactly this JSON and nothing else:")
        appendLine("""{"score": <integer 0-100>, "comment": "<one short sentence>", """ +
            """"analysis": "<2-4 sentences addressed to the user as 'you', naming the """ +
            """single most useful change for next time>"}""")
    }

    private fun Double.asPercent(): String = "${(this * 100).roundToInt()}%"
}
