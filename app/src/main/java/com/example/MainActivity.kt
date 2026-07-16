package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AirMouseScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.KeyboardScreen
import com.example.ui.screens.MediaRemoteScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PresentationScreen
import com.example.ui.screens.Routes
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TouchpadScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AirMouseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: AirMouseViewModel = viewModel()
                val settings by viewModel.settingsState.collectAsState()

                // Keep screen awake setting observer
                LaunchedEffect(settings.keepScreenAwake) {
                    if (settings.keepScreenAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF020617) // Sleek luxury dark theme canvas
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.SPLASH
                    ) {
                        composable(Routes.SPLASH) {
                            SplashScreen(navController)
                        }
                        composable(Routes.PERMISSIONS) {
                            PermissionsScreen(navController)
                        }
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(navController, viewModel)
                        }
                        composable(Routes.TOUCHPAD) {
                            TouchpadScreen(navController, viewModel)
                        }
                        composable(Routes.AIR_MOUSE) {
                            AirMouseScreen(navController, viewModel)
                        }
                        composable(Routes.KEYBOARD) {
                            KeyboardScreen(navController, viewModel)
                        }
                        composable(Routes.MEDIA_REMOTE) {
                            MediaRemoteScreen(navController, viewModel)
                        }
                        composable(Routes.PRESENTATION) {
                            PresentationScreen(navController, viewModel)
                        }
                        composable(Routes.SHORTCUTS) {
                            ShortcutsScreen(navController, viewModel)
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(navController, viewModel)
                        }
                        composable(Routes.ABOUT) {
                            AboutScreen(navController)
                        }
                    }
                }
            }
        }
    }
}
