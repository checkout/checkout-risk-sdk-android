package com.checkout.risk

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal const val TIMEOUT_DURATION_SECONDS = 5L

internal fun getRetrofitClient(baseUrl: String): Retrofit {
    val httpInterceptor =
        HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)

    val client =
        OkHttpClient.Builder()
            .addInterceptor(httpInterceptor)
            .connectTimeout(
                TIMEOUT_DURATION_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            .build()

    return Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
}
