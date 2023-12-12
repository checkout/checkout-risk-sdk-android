package com.checkout.risk_sdk_android

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun getRetrofitClient(baseUrl: String): Retrofit {
    val httpInterceptor = HttpLoggingInterceptor()
        .setLevel(HttpLoggingInterceptor.Level.BODY)

    val client = OkHttpClient.Builder()
        .addInterceptor(httpInterceptor)
        .build()

    return Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
}


class ApiException(val errorCode: Int, message: String) : RuntimeException(message)
