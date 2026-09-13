package com.rootilabs.wmeCardiac.data.auth

import android.util.Log
import com.rootilabs.wmeCardiac.Constants
import com.rootilabs.wmeCardiac.data.api.AuthApi
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Re-issues the OAuth token when the server rejects a request with 401.
 *
 * The backend signs `client_credentials` tokens with a 1-day lifetime and the app has no
 * proactive renewal, so without this the whole session dies silently once a day.
 *
 * IMPORTANT: [authApiProvider] must resolve to an API built on a *separate* OkHttpClient that
 * does NOT carry this authenticator. Sharing one client would deadlock — the blocking refresh
 * below occupies one of the dispatcher's `maxRequestsPerHost` (default 5) slots, so 5 concurrent
 * 401s would leave no slot for the refresh call itself. See ServiceLocator.initApis().
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApiProvider: () -> AuthApi
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only ever retry once: priorResponse is non-null on the follow-up of an earlier 401.
        if (response.priorResponse != null) {
            Log.e(TAG, "Refreshed token was rejected as well, giving up on ${response.request.url}")
            return null
        }

        val usedToken = response.request.header("Authorization")?.removePrefix(BEARER_PREFIX)

        // Serialized: concurrent 401s would otherwise each fire their own /oauth/token request and
        // overwrite each other's result in SharedPreferences.
        synchronized(this) {
            val currentToken = tokenManager.accessToken
            if (!currentToken.isNullOrBlank() && currentToken != usedToken) {
                // Another thread already refreshed while this request was in flight.
                Log.d(TAG, "Token was refreshed by another request, retrying with it")
                return response.request.newBuilder()
                    .header("Authorization", BEARER_PREFIX + currentToken)
                    .build()
            }

            Log.d(TAG, "401 on ${response.request.url}, refreshing token")
            val newToken = try {
                val tokenResponse = runBlocking {
                    authApiProvider().getToken(
                        basicAuth = Constants.BASIC_AUTH,
                        body = mapOf("grant_type" to "client_credentials")
                    )
                }
                if (!tokenResponse.isSuccessful) {
                    Log.e(TAG, "Token refresh failed: HTTP ${tokenResponse.code()}")
                    return null
                }
                tokenResponse.body()?.accessToken
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                return null
            }

            if (newToken.isNullOrBlank()) {
                Log.e(TAG, "Token refresh returned an empty token")
                return null
            }

            tokenManager.accessToken = newToken
            Log.d(TAG, "Token refreshed, retrying request")
            return response.request.newBuilder()
                .header("Authorization", BEARER_PREFIX + newToken)
                .build()
        }
    }
}
