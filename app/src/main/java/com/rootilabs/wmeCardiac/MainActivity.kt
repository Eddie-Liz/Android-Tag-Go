package com.rootilabs.wmeCardiac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rootilabs.wmeCardiac.di.ServiceLocator
import com.rootilabs.wmeCardiac.ui.navigation.AppNavigation
import com.rootilabs.wmeCardiac.ui.theme.TagGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Edge-to-edge: layout draws behind system bars.
        // Colours are set via theme XML (android:statusBarColor / android:navigationBarColor)
        // to avoid calling the deprecated Window.setStatusBarColor/setNavigationBarColor APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true   // dark icons on TagGoGreen header
            isAppearanceLightNavigationBars = false
        }

        val startDest = if (ServiceLocator.tokenManager.hasActiveSession) "main" else "login"

        setContent {
            TagGoTheme {
                AppNavigation(startDestination = startDest)
            }
        }
    }
}
