package com.rootilabs.wmeCardiac.data.auth

import android.util.Log
import com.rootilabs.wmeCardiac.Constants
import com.rootilabs.wmeCardiac.data.api.AuthApi
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApiProvider: () -> AuthApi
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val oldToken = tokenManager.accessToken
        Log.w(TAG, "========== TOKEN EXPIRED (401) ==========")
        Log.w(TAG, "401 detected for request: ${response.request.url}, attempting to refresh token...")
        
        // Prevent infinite loop if the refresh token itself is unauthorized 
        if (response.responseCount > 1) {
            Log.e(TAG, "Token refresh failed or looping. Stop retrying.")
            return null
        }

        val authApi = authApiProvider()
        return try {
            val tokenResponse = runBlocking {
                authApi.getToken(
                    basicAuth = Constants.BASIC_AUTH,
                    body = mapOf("grant_type" to "client_credentials")
                )
            }
            
            if (tokenResponse.isSuccessful) {
                val newToken = tokenResponse.body()?.accessToken
                if (newToken != null) {
                    tokenManager.accessToken = newToken
                    val oldPreview = oldToken?.takeLast(6) ?: "null"
                    val newPreview = newToken.takeLast(6)
                    Log.w(TAG, "Token refreshed successfully! (Old ends with: $oldPreview -> New ends with: $newPreview)")
                    Log.w(TAG, "=========================================")
                    
                    // Retry the request with the new token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                } else {
                    Log.e(TAG, "New token is null.")
                    null
                }
            } else {
                Log.e(TAG, "Failed to refresh token: HTTP ${tokenResponse.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
            null
        }
    }
    
    private val Response.responseCount: Int
        get() {
            var result = 1
            var prior = priorResponse
            while (prior != null) {
                result++
                prior = prior.priorResponse
            }
            return result
        }
}
