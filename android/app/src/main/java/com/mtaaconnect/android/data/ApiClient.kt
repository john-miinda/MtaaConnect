package com.mtaaconnect.android.data

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    baseUrl: String,
    tokenProvider: () -> String?,
    onUnauthorized: () -> Unit,
) {
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = tokenProvider()
        val request = if (token == null) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        val response = chain.proceed(request)
        if (response.code == 401) onUnauthorized()
        response
    }

    val api: MtaaApi = Retrofit.Builder()
        .baseUrl(baseUrl.ensureTrailingSlash())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()
        .create(MtaaApi::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
}
