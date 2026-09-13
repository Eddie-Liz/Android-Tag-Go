package com.rootilabs.wmeCardiac.di

import android.content.Context
import androidx.room.Room
import com.rootilabs.wmeCardiac.BuildConfig
import com.rootilabs.wmeCardiac.Constants
import com.rootilabs.wmeCardiac.data.api.AuthApi
import com.rootilabs.wmeCardiac.data.api.RootiCareApi
import com.rootilabs.wmeCardiac.data.auth.AuthInterceptor
import com.rootilabs.wmeCardiac.data.auth.TokenManager
import com.rootilabs.wmeCardiac.data.auth.TokenAuthenticator
import com.rootilabs.wmeCardiac.data.local.AppDatabase
import com.rootilabs.wmeCardiac.data.repository.RootiCareRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Simple service locator for dependency injection (no Hilt needed)
 */
object ServiceLocator {

    private var _instance: ServiceLocator? = null
    lateinit var appContext: Context
        private set

    lateinit var tokenManager: TokenManager
        private set
    lateinit var moshi: Moshi
        private set
    lateinit var authApi: AuthApi
        private set
    lateinit var rootiCareApi: RootiCareApi
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var repository: RootiCareRepository
        private set

    fun init(context: Context) {
        if (_instance != null) return
        _instance = this
        appContext = context.applicationContext
        tokenManager = TokenManager(appContext)
        val savedUrl = tokenManager.serverUrl ?: Constants.BASE_URL
        initApis(savedUrl)
        initDatabase()
    }

    fun reinitWithBaseUrl(baseUrl: String) {
        android.util.Log.d("ServiceLocator", "reinitWithBaseUrl: $baseUrl")
        initApis(baseUrl)
        repository = RootiCareRepository(
            authApi = authApi,
            rootiCareApi = rootiCareApi,
            tokenManager = tokenManager,
            database = database,
            moshi = moshi
        )
    }

    private fun initApis(baseUrl: String) {
        android.util.Log.d("ServiceLocator", "initApis: building Retrofit with baseUrl=$baseUrl")
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        // Auth OkHttp (no bearer token, and deliberately no authenticator — see TokenAuthenticator)
        val authClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(httpLoggingInterceptor())
            .build()

        authApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApi::class.java)

        // Main OkHttp (with bearer token)
        val mainClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Bounds the worst case of 401 -> refresh -> retry, which chains three
            // connect+read budgets and would otherwise leave the UI blocked for minutes.
            .callTimeout(90, TimeUnit.SECONDS)
            .authenticator(TokenAuthenticator(tokenManager) { authApi })
            .addInterceptor(AuthInterceptor { tokenManager.accessToken })
            .addInterceptor(httpLoggingInterceptor())
            .build()

        rootiCareApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(mainClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RootiCareApi::class.java)

        repository = RootiCareRepository(
            authApi = authApi,
            rootiCareApi = rootiCareApi,
            tokenManager = tokenManager,
            database = if (::database.isInitialized) database else initDatabase(),
            moshi = moshi
        )
    }

    /**
     * Body-level logging prints the Basic auth header, the Bearer token and every response body
     * (patient ids, event tags and symptom text) to logcat. That is readable by anyone who can run
     * `adb logcat` or export a bug report, so release builds must stay silent.
     */
    private fun httpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    private fun initDatabase(): AppDatabase {
        database = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "rooticare_db"
        ).fallbackToDestructiveMigration().build()
        return database
    }
}
