package com.example.appmobile.data.remote

import com.example.appmobile.data.remote.api.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    // LƯU Ý: 10.0.2.2 là địa chỉ IP đặc biệt để máy ảo Android truy cập localhost của máy tính.
    // Nếu bạn dùng máy thật, hãy thay bằng địa chỉ IP LAN của máy tính (ví dụ: 192.168.1.x)
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Interceptor để xem nội dung JSON gửi đi/về trong Logcat
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Khởi tạo Retrofit
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
