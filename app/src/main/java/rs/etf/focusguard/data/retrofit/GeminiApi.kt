package rs.etf.focusguard.data.retrofit

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The Gemini generative language API.
 *
 * The key travels in the `x-goog-api-key` header rather than a query parameter, so it cannot
 * end up in URLs, logs or crash reports.
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest,
    ): GeminiResponse

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"

        /** Fast, cheap, and comfortably inside the free tier for one call per session. */
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }
}
