package rs.etf.focusguard.hilt

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rs.etf.focusguard.BuildConfig
import rs.etf.focusguard.data.retrofit.GeminiApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    @Provides
    @Singleton
    fun providesGson(): Gson = Gson()

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC, never BODY: the request carries the API key in a header, and BODY would
            // put it in logcat.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            // Generous, because nothing is waiting on it. The rating runs after the session is
            // stored and the timer screen released, so the user never sees this delay — but
            // when it expired the score silently became the local one instead. Measured
            // replies took 16-26 seconds, so the old 20 lost roughly a third of them.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(GeminiApi.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun providesGeminiApi(retrofit: Retrofit): GeminiApi = retrofit.create(GeminiApi::class.java)
}
