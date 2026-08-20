package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AirMouseScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceSettingsScreen
import com.example.ui.screens.GamepadScreen
import com.example.ui.screens.GestureScreen
import com.example.ui.screens.KeyboardScreen
import com.example.ui.screens.MediaRemoteScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PresentationScreen
import com.example.ui.screens.Routes
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TouchpadScreen
import com.example.ui.screens.UpdateDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.service.AirMouseService
import com.example.update.UpdateChecker
import com.example.update.UpdateInfo
import com.example.viewmodel.AirMouseViewModel
import kotlinx.coroutines.launch

import androidx.activity.viewModels
import androidx.navigation.NavController

class MainActivity : ComponentActivity() {
    private val mainViewModel: AirMouseViewModel by viewModels()

    // Navigation transition duration and the input-block window that covers it
    // (slightly longer than the fade so taps can't land mid-animation).
    private companion object {
        const val NAV_TRANSITION_MS = 200 // tween() takes Int millis
        const val NAV_INPUT_BLOCK_MS = 300L // delay() takes Long millis
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AirMouseViewModel = viewModel()
            val settings by viewModel.settingsState.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            // Update check state
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            // Check for updates on launch
            LaunchedEffect(Unit) {
                val currentVersion = BuildConfig.VERSION_NAME
                val info = UpdateChecker.checkForUpdate(currentVersion)
                if (info.isUpdateAvailable) {
                    updateInfo = info
                    showUpdateDialog = true
                    // Ensure Install Unknown Apps permission is granted for auto-install
                    ensureInstallPermission(this@MainActivity)
                }
            }

            MyApplicationTheme(
                darkTheme = when (settings.themeMode) {
                    1 -> false // Light
                    2 -> true  // Dark
                    3 -> true  // AMOLED — dark with pure black
                    else -> isSystemInDarkTheme() // System Default
                },
                amoled = settings.themeMode == 3,
                dynamicColor = settings.useDynamicColors
            ) {
                // Show update dialog (inside theme for proper colors)
                if (showUpdateDialog && updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateInfo!!,
                        onDismiss = { showUpdateDialog = false }
                    )
                }

                val navController = rememberNavController()
                val lifecycleOwner = LocalLifecycleOwner.current

                // While a navigation transition is playing, the outgoing screen
                // stays composed (and tappable) until the animation ends, so a
                // tap during the transition can hit a button that's already
                // being left behind. We block all pointer input for the short
                // duration of the transition, keyed on every destination change.
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                var transitionInProgress by remember { mutableStateOf(false) }
                LaunchedEffect(currentBackStackEntry?.destination?.route) {
                    if (currentBackStackEntry != null) {
                        transitionInProgress = true
                        delay(NAV_INPUT_BLOCK_MS)
                        transitionInProgress = false
                    }
                }

                // Lifecycle observer for background/foreground transitions
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                // App went to background - pause sensors
                                viewModel.onAppBackground()
                                Log.d("MainActivity", "App backgrounded - sensors paused")
                            }
                            Lifecycle.Event.ON_START -> {
                                // App came to foreground - ready to resume
                                viewModel.onAppForeground()
                                Log.d("MainActivity", "App foregrounded")
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Keep screen awake setting observer
                LaunchedEffect(settings.keepScreenAwake) {
                    if (settings.keepScreenAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                DisposableEffect(navController) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        viewModel.currentRoute.value = destination.route
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.SPLASH,
                            // Short, snappy fades — the input guard below is
                            // sized to cover exactly this window.
                            enterTransition = { fadeIn(tween(NAV_TRANSITION_MS)) },
                            exitTransition = { fadeOut(tween(NAV_TRANSITION_MS)) },
                            popEnterTransition = { fadeIn(tween(NAV_TRANSITION_MS)) },
                            popExitTransition = { fadeOut(tween(NAV_TRANSITION_MS)) }
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
                            composable(Routes.GAMEPAD) {
                                GamepadScreen(navController, viewModel)
                            }
                            composable(Routes.GESTURE) {
                                GestureScreen(navController, viewModel)
                            }
                            composable(Routes.SHORTCUTS) {
                                ShortcutsScreen(navController, viewModel)
                            }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(navController, viewModel)
                        }
                        composable(Routes.DEVICE_SETTINGS) {
                            DeviceSettingsScreen(navController, viewModel)
                        }
                        composable(Routes.ABOUT) {
                                AboutScreen(navController)
                            }
                        }

                        // Touch shield: swallows every pointer event while a
                        // navigation transition is running so buttons on the
                        // outgoing screen can't be pressed mid-animation.
                        if (transitionInProgress) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent().changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val isConnected = mainViewModel.bluetoothState.value == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        val route = mainViewModel.currentRoute.value

        if (isConnected) {
            if (route == Routes.PRESENTATION) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        mainViewModel.sendKeyboardKey(0, 0x4B.toByte()) // Page Up (Prev Slide)
                        mainViewModel.vibrate(30)
                        android.widget.Toast.makeText(this, "Previous Slide (Vol Up)", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        mainViewModel.sendKeyboardKey(0, 0x4E.toByte()) // Page Down (Next Slide)
                        mainViewModel.vibrate(30)
                        android.widget.Toast.makeText(this, "Next Slide (Vol Down)", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
            } else if (route == Routes.MEDIA_REMOTE || route == Routes.AIR_MOUSE || route == Routes.TOUCHPAD) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        mainViewModel.sendMediaAction(0x01) // Volume Up
                        mainViewModel.vibrate(30)
                        android.widget.Toast.makeText(this, "Volume Up", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        mainViewModel.sendMediaAction(0x02) // Volume Down
                        mainViewModel.vibrate(30)
                        android.widget.Toast.makeText(this, "Volume Down", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val isConnected = mainViewModel.bluetoothState.value == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        val route = mainViewModel.currentRoute.value

        if (isConnected && (route == Routes.PRESENTATION || route == Routes.MEDIA_REMOTE || route == Routes.AIR_MOUSE || route == Routes.TOUCHPAD)) {
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the foreground service when app is swiped away from recent apps
        AirMouseService.stopService(this)
    }
}

/**
 * On Android 8+ opening the "Install unknown apps" settings screen
 * lets the user grant the permission so downloaded APKs can be
 * installed automatically. On older versions this is a no-op.
 */
private fun ensureInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }
}
