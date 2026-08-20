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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.viewModels
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
import com.example.ui.screens.NavRoute
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TouchpadScreen
import com.example.ui.screens.UpdateDialog
import com.example.ui.screens.UpdateSuccessfulDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.service.AirMouseService
import com.example.update.UpdateChecker
import com.example.update.UpdateInfo
import com.example.viewmodel.AirMouseViewModel
import kotlinx.coroutines.launch

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
            val settings by mainViewModel.settingsState.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            // Update check state
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            // Update successful dialog state
            var showUpdateSuccessDialog by remember { mutableStateOf(false) }
            var updateSuccessVersion by remember { mutableStateOf("") }
            var updateSuccessChangelog by remember { mutableStateOf("") }

            // Check for updates AND detect post-update on launch
            LaunchedEffect(Unit) {
                val currentVersion = BuildConfig.VERSION_NAME
                val prefs = getSharedPreferences("air_mouse_prefs", MODE_PRIVATE)
                val lastSeenVersion = prefs.getString("last_seen_version", null)

                // Detect if we just updated: current version differs from last seen
                if (lastSeenVersion != null && lastSeenVersion != currentVersion) {
                    // Fetch changelog for the new version from GitHub
                    val info = UpdateChecker.checkForUpdate(currentVersion)
                    updateSuccessVersion = currentVersion
                    updateSuccessChangelog = info.changelog
                    showUpdateSuccessDialog = true
                }

                // Always save current version as last seen
                prefs.edit().putString("last_seen_version", currentVersion).apply()

                // Check for newer updates
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

                // Show update successful dialog after app update
                if (showUpdateSuccessDialog) {
                    UpdateSuccessfulDialog(
                        version = updateSuccessVersion,
                        changelog = updateSuccessChangelog,
                        onDismiss = { showUpdateSuccessDialog = false }
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
                                mainViewModel.onAppBackground()
                                Log.d("MainActivity", "App backgrounded - sensors paused")
                            }
                            Lifecycle.Event.ON_START -> {
                                // App came to foreground - ready to resume
                                mainViewModel.onAppForeground()
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
                        mainViewModel.currentRoute.value = destination.route
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
                            startDestination = NavRoute.Splash.path,
                            // Short, snappy fades — the input guard below is
                            // sized to cover exactly this window.
                            enterTransition = { fadeIn(tween(NAV_TRANSITION_MS)) },
                            exitTransition = { fadeOut(tween(NAV_TRANSITION_MS)) },
                            popEnterTransition = { fadeIn(tween(NAV_TRANSITION_MS)) },
                            popExitTransition = { fadeOut(tween(NAV_TRANSITION_MS)) }
                        ) {
                            composable(NavRoute.Splash.path) {
                                SplashScreen(navController)
                            }
                            composable(NavRoute.Permissions.path) {
                                PermissionsScreen(navController)
                            }
                            composable(NavRoute.Dashboard.path) {
                                DashboardScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Touchpad.path) {
                                TouchpadScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.AirMouse.path) {
                                AirMouseScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Keyboard.path) {
                                KeyboardScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.MediaRemote.path) {
                                MediaRemoteScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Presentation.path) {
                                PresentationScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Gamepad.path) {
                                GamepadScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Gesture.path) {
                                GestureScreen(navController, mainViewModel)
                            }
                            composable(NavRoute.Shortcuts.path) {
                                ShortcutsScreen(navController, mainViewModel)
                            }
                        composable(NavRoute.Settings.path) {
                            SettingsScreen(navController, mainViewModel)
                        }
                        composable(NavRoute.DeviceSettings.path) {
                            DeviceSettingsScreen(navController, mainViewModel)
                        }
                        composable(NavRoute.About.path) {
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
            if (route == NavRoute.Presentation.path) {
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
            } else if (route == NavRoute.MediaRemote.path || route == NavRoute.AirMouse.path || route == NavRoute.Touchpad.path) {
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

        if (isConnected && (route == NavRoute.Presentation.path || route == NavRoute.MediaRemote.path || route == NavRoute.AirMouse.path || route == NavRoute.Touchpad.path)) {
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
