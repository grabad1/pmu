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

    suspend fun rate(summary: SessionSummary, baseline: TopicSummary? = null): SessionRating {
        val local = LocalSessionRater.rate(summary)

        if (!isAiConfigured) {
            Log.d(LOG_TAG, "No Gemini key configured; using local rating")
            return local
        }

        return runCatching { requestAiRating(summary, baseline) }
            .onFailure { Log.w(LOG_TAG, "Gemini rating failed: ${it.message}") }
            .getOrNull()
            ?: local
    }

    private suspend fun requestAiRating(
        summary: SessionSummary,
        baseline: TopicSummary?,
    ): SessionRating? {
        val response = geminiApi.generateContent(
            model = GeminiApi.DEFAULT_MODEL,
            apiKey = apiKey,
            request = GeminiRequest.ofPrompt(buildPrompt(summary, baseline)),
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
     * The prompt states the facts and the app's rubric, then constrains the output shape.
     *
     * The rubric mirrors [LocalSessionRater] so the AI and the fallback cannot disagree about
     * what a good session is. The local score itself is deliberately withheld: an independent
     * judgement is more useful than a rubber stamp.
     */
    private fun buildPrompt(summary: SessionSummary, baseline: TopicSummary?): String = buildString {
        appendLine(
            "You are reviewing one focus session from a productivity app that helps someone " +
                "study without touching their phone. Judge it and reply with JSON only."
        )
        appendLine()
        appendLine("What happened:")
        appendLine("- Session name: ${summary.name}")
        appendLine("- Goal: ${summary.goalMinutes} ${"minute".pluralised(summary.goalMinutes)} of focus")
        appendLine(
            "- Actually focused: ${summary.focusedDescription}, which is " +
                "${(summary.goalCompletion * 100).roundToInt()}% of the goal (pauses excluded)"
        )
        appendLine(
            "- Planned breaks: ${summary.plannedPauseCount} planned, " +
                "${summary.plannedPausesTaken} taken"
        )
        appendLine(
            "- Unplanned breaks: ${summary.unplannedPauseCount}, lasting " +
                "${summary.unplannedPauseSeconds} seconds in total, which is " +
                "${(summary.unplannedShare * 100).roundToInt()}% of the time spent at the desk"
        )
        appendLine("- Phone was being moved or handled during ${summary.movementFraction.asPercent()} of readings")
        appendLine("- Room was noisy during ${summary.loudFraction.asPercent()} of readings")
        appendLine("- Light was poor during ${summary.darkFraction.asPercent()} of readings")
        appendLine(
            "- Time spent in a different app while the timer was running: " +
                "${summary.awayDescription}, which is " +
                "${(summary.awayShare * 100).roundToInt()}% of the focus time claimed"
        )

        // Only offered once there is a real history to compare against: with one or two
        // sessions an "average" is barely more than this session repeated back.
        if (baseline != null && baseline.sessionCount >= 2) {
            appendLine()
            appendLine(
                "For context, how this user's previous ${baseline.label.lowercase()} sessions " +
                    "have gone (${baseline.sessionCount} earlier sessions, this one excluded):"
            )
            baseline.averageScore?.let { appendLine("- Their usual score: $it out of 100") }
            appendLine(
                "- Usual focus time: ${(baseline.averageFocusedSeconds / 60.0).roundToInt()} " +
                    "minutes against a usual goal of ${baseline.averageGoalMinutes} minutes"
            )
            appendLine(
                "- Usual unplanned breaks: " +
                    String.format("%.1f", baseline.averageUnplannedPauses) + " per session"
            )
            appendLine(
                "- Usual conditions: phone handled ${baseline.movementFraction.asPercent()}, " +
                    "noisy ${baseline.loudFraction.asPercent()}, " +
                    "poor light ${baseline.darkFraction.asPercent()} of readings"
            )
            appendLine(
                "Use this only to make the comment and analysis more useful — say whether " +
                    "this session was better or worse than their usual, and mention any " +
                    "pattern that keeps recurring. Do NOT change the score because of it: " +
                    "the score must reflect this session alone, judged by the rules below."
            )
        }
        appendLine()
        appendLine("How to score it, in order of importance:")
        appendLine(
            "1. Reaching the goal matters most. Exceeding it is a success, never a problem."
        )
        appendLine(
            "2. Session length matters on its own. A session under about 5 focused minutes " +
                "cannot score above roughly 60 no matter how clean it was, and under 10 " +
                "minutes cannot score above roughly 75, because a few minutes is not " +
                "sustained focus. Say so plainly and encourage a longer block next time. " +
                "Sessions of 20 minutes or more are not limited at all."
        )
        appendLine(
            "3. Handling the phone is the most serious environmental problem, noise is less " +
                "serious, and poor light is the least serious — reduce the score only " +
                "slightly for light."
        )
        appendLine(
            "4. Ignore any environmental problem present for only a small share of readings " +
                "(roughly a fifth or less). Someone briefly carrying the phone to another " +
                "room is not a failure."
        )
        appendLine(
            "5. Unplanned breaks reduce the score in proportion to how much of the session " +
                "they consumed. Ignore short ones of about two minutes or less — a quick " +
                "phone call is ordinary life. Many separate short breaks are worse than one " +
                "longer break, because the session never settles."
        )
        appendLine(
            "6. Planned breaks are healthy and taking them is discipline, never a penalty. " +
                "But too many for the length of the goal is itself a problem: roughly one " +
                "break per 20 minutes is sensible, so ${summary.goalMinutes} minutes warrants " +
                "about ${summary.reasonablePauseCount}. A 45-minute session with 5 planned " +
                "breaks is chopped up and should score lower than one with none."
        )
        appendLine(
            "7. Time spent in another app while the timer ran is the most damaging of all, " +
                "because unlike a break the session went on counting it as focus. Ignore " +
                "under about half a minute — glancing at a message is ordinary — but beyond " +
                "that reduce the score in proportion, and say so directly."
        )
        appendLine()
        appendLine(
            "Be honest rather than encouraging. If the session was poor, say why and name the " +
                "single change that would help most next time."
        )
        appendLine()
        appendLine("Reply with exactly this JSON and nothing else:")
        appendLine(
            """{"score": <integer 0-100>, "comment": "<one short sentence>", """ +
                """"analysis": "<2-4 sentences addressed to the user as 'you', naming the """ +
                """single most useful change for next time>"}"""
        )
    }

    private fun Double.asPercent(): String = "${(this * 100).roundToInt()}%"

    private fun String.pluralised(count: Int): String = if (count == 1) this else "${this}s"
}
