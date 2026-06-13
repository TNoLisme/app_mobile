package com.example.appmobile.data.remote

import com.example.appmobile.BuildConfig
import com.example.appmobile.data.remote.api.ApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val configuredBaseUrl = BuildConfig.BACKEND_URL.toHttpUrl()
    @Volatile private var preferredBaseUrl: HttpUrl? = null

    // 10.0.2.2 is the emulator host. 127.0.0.1 works after:
    // adb reverse tcp:8000 tcp:8000
    private val fallbackBaseUrls = listOf(
        configuredBaseUrl,
        "http://10.0.2.2:${configuredBaseUrl.port}/".toHttpUrl(),
        "http://127.0.0.1:${configuredBaseUrl.port}/".toHttpUrl()
    ).distinctBy { "${it.scheme}://${it.host}:${it.port}" }

    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        var lastError: IOException? = null

        val baseUrls = (listOfNotNull(preferredBaseUrl) + fallbackBaseUrls)
            .distinctBy { "${it.scheme}://${it.host}:${it.port}" }
        baseUrls.forEach { baseUrl ->
            val retryUrl = originalUrl.newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()
            val retryRequest = originalRequest.newBuilder().url(retryUrl).build()

            try {
                val response = chain.proceed(retryRequest)
                preferredBaseUrl = baseUrl
                return@Interceptor response
            } catch (error: IOException) {
                lastError = error
            }
        }

        throw lastError ?: IOException("Cannot connect to backend")
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(hostFallbackInterceptor)
        .addInterceptor(logging)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(configuredBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun backendUrl(path: String): String {
        val cleanPath = path.trimStart('/')
        return (preferredBaseUrl ?: configuredBaseUrl)
            .resolve(cleanPath)
            ?.toString()
            ?: BuildConfig.BACKEND_URL + cleanPath
    }
}
