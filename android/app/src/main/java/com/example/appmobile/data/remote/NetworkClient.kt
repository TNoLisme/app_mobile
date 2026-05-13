package com.example.appmobile.data.remote

import com.example.appmobile.data.remote.api.ApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    // 10.0.2.2 is for Android Emulator. 127.0.0.1 works on real devices after:
    // adb reverse tcp:8000 tcp:8000
    private const val BASE_URL = "http://10.0.2.2:8000/"
    @Volatile private var preferredHost: String? = null

    private val fallbackHosts = listOf(
        "192.168.1.37",
        "10.0.2.2",
        "127.0.0.1",
        "localhost",
        "192.168.1.27",
        "192.168.1.9",
        "10.203.104.216"
    )

    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        var lastError: IOException? = null

        val hosts = (listOfNotNull(preferredHost) + fallbackHosts).distinct()
        hosts.forEach { host ->
            val retryUrl = originalUrl.newBuilder()
                .scheme("http")
                .host(host)
                .port(8000)
                .build()
            val retryRequest = originalRequest.newBuilder().url(retryUrl).build()

            try {
                val response = chain.proceed(retryRequest)
                preferredHost = host
                return@Interceptor response
            } catch (error: IOException) {
                lastError = error
            }
        }

        throw lastError ?: IOException("Cannot connect to backend")
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
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
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
