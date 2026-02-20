package com.kilu.pocketagent.core.network

import com.kilu.pocketagent.core.storage.DeviceProfileStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ApiClient(private val store: DeviceProfileStore) {
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        store.getSessionToken()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getBaseUrl(): String = store.getControlPlaneUrl()
}
