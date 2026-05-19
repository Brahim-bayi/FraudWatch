package com.fraudwatch.data.remote

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // ── Détection émulateur ───────────────────────────────────────────────────

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.FINGERPRINT.contains("sdk_gphone")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("sdk_gphone")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    // ── URLs Ollama (port 11434) ──────────────────────────────────────────────

    private val OLLAMA_URL: String
        get() = if (isEmulator()) "http://10.0.2.2:11434/" else "http://10.19.33.229:11434/"

    // ── URLs Backend personnel (port 8000) ───────────────────────────────────

    private val BACKEND_URL: String
        get() = if (isEmulator()) "http://10.0.2.2:8000/" else "http://10.19.33.229:8000/"

    // ── Client OkHttp commun ──────────────────────────────────────────────────

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Services Retrofit ─────────────────────────────────────────────────────

    val ollamaService: OllamaService
        get() = Retrofit.Builder()
            .baseUrl(OLLAMA_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OllamaService::class.java)

    val backendImageService: BackendImageService
        get() = Retrofit.Builder()
            .baseUrl(BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendImageService::class.java)
}
