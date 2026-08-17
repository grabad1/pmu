package rs.etf.focusguard.data.retrofit

import com.google.gson.annotations.SerializedName

/**
 * Request body for Gemini's `generateContent`.
 *
 * Only the fields this app needs are modelled; the API accepts many more.
 */
data class GeminiRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig,
) {
    data class Content(
        @SerializedName("parts") val parts: List<Part>,
    )

    data class Part(
        @SerializedName("text") val text: String,
    )

    /**
     * `responseMimeType` asks for JSON back, which removes the need to scrape a score out of
     * prose and makes a malformed reply obvious rather than silently mis-parsed.
     */
    data class GenerationConfig(
        @SerializedName("temperature") val temperature: Float = 0.4f,
        /**
         * Generous, because current flash models spend part of the output budget on internal
         * reasoning. At 400 the JSON came back truncated mid-string.
         */
        @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 2000,
        @SerializedName("responseMimeType") val responseMimeType: String = "application/json",
    )

    companion object {
        fun ofPrompt(prompt: String) = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(),
        )
    }
}

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<Candidate>?,
) {
    data class Candidate(
        @SerializedName("content") val content: Content?,
    )

    data class Content(
        @SerializedName("parts") val parts: List<Part>?,
    )

    data class Part(
        @SerializedName("text") val text: String?,
    )

    /** The generated text, or null when the model returned nothing usable. */
    val text: String?
        get() = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

/** The JSON the model is asked to produce, parsed from [GeminiResponse.text]. */
data class SessionRatingJson(
    @SerializedName("score") val score: Int?,
    @SerializedName("comment") val comment: String?,
    @SerializedName("analysis") val analysis: String?,
)
