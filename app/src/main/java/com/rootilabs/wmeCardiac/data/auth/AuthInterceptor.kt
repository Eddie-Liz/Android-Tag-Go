package com.rootilabs.wmeCardiac.data.auth

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val token = tokenProvider()

        val requestBuilder = originalRequest.newBuilder()
            .addHeader("Content-Type", "application/json")

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        android.util.Log.d("AuthInterceptor", "Request: ${request.method} ${request.url}")
        android.util.Log.d("AuthInterceptor", "Headers: ${request.headers}")
        
        return chain.proceed(request)
    }
}
