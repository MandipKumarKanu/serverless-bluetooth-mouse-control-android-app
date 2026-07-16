@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.example.R
import com.example.data.SettingsEntity
import com.example.data.ShortcutEntity
import com.example.viewmodel.AirMouseViewModel
import com.example.bluetooth.getSafeName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

object Routes {
    const val SPLASH = "splash"
    const val PERMISSIONS = "permissions"
    const val DASHBOARD = "dashboard"
    const val TOUCHPAD = "touchpad"
    const val AIR_MOUSE = "air_mouse"
    const val KEYBOARD = "keyboard"
    const val MEDIA_REMOTE = "media_remote"
    const val PRESENTATION = "presentation"
    const val SHORTCUTS = "shortcuts"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

// ==========================================
// SPLASH SCREEN
// ==========================================
@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    var startAnim by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "LogoScale"
    )
    val opacity = animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(1200),
        label = "LogoOpacity"
    )

    LaunchedEffect(key1 = true) {
        startAnim = true
        delay(1800)
        
        // Determine whether permissions are already granted
        val reqPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = reqPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        } else {
            navController.navigate(Routes.PERMISSIONS) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scale.value)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.9f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Air Mouse Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "AirMouse",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Serverless Bluetooth Controller",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ==========================================
// PERMISSIONS SCREEN
// ==========================================
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.PERMISSIONS) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = "Bluetooth Permission Required",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Bluetooth & Sensors Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "AirMouse functions 100% serverless by registering directly as a hardware device over standard Bluetooth HID.\n\nTo establish connections and stream motion data, we require Bluetooth discovery, connection, and motion sensor permissions.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { launcher.launch(requiredPermissions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("request_permissions_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// ==========================================
// MAIN DASHBOARD (HOME SCREEN)
// ==========================================
@Composable
fun DashboardScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val isAppRegistered by viewModel.isAppRegistered.collectAsState()
    val isProfileReady by viewModel.isProfileReady.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val lastConnectedDeviceAddress by viewModel.lastConnectedDeviceAddress.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPairedDevices()
                viewModel.hidManager.registerApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AirMouse Console", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.vibrate(30)
                            viewModel.refreshPairedDevices()
                        },
                        modifier = Modifier.testTag("refresh_devices")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Paired", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Connection Status Card
            item {
                val isBluetoothPowerOn by viewModel.isBluetoothPowerOn.collectAsState()
                val cardColor = when {
                    !isBluetoothPowerOn -> MaterialTheme.colorScheme.errorContainer
                    isConnected -> Color(0xFF064E3B) // Dark green for connected
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val statusText = when {
                    !isBluetoothPowerOn -> "Bluetooth is Off - Tap to turn on"
                    connectionState == BluetoothProfile.STATE_CONNECTED -> "Connected to ${connectedDevice?.getSafeName() ?: "Unknown Device"}"
                    connectionState == BluetoothProfile.STATE_CONNECTING -> "Connecting..."
                    connectionState == BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Offline"
                }
                val statusIcon = if (isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.BluetoothDisabled
                val tintColor = when {
                    !isBluetoothPowerOn -> MaterialTheme.colorScheme.error
                    isConnected -> Color(0xFF10B981) // Green for connected
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isBluetoothPowerOn) {
                            viewModel.enableBluetooth()
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(tintColor.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Connection State",
                                tint = tintColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusText,
                                fontSize = 16.sp,
                                color = if (isConnected || !isBluetoothPowerOn) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isConnected) {
                            IconButton(
                                onClick = { viewModel.disconnectDevice() },
                                modifier = Modifier.testTag("disconnect_host")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Disconnect",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Control Modes Grid (Only shown when connected)
            if (isConnected) {
                item {
                    Text(
                        text = "Control Modes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                item {
                    val screens = listOf(
                        ControlScreenTile(Routes.TOUCHPAD, "Touchpad", Icons.Outlined.TouchApp, Color(0xFF3B82F6)),
                        ControlScreenTile(Routes.AIR_MOUSE, "Air Mouse", Icons.Outlined.Mouse, Color(0xFF10B981)),
                        ControlScreenTile(Routes.KEYBOARD, "Keyboard", Icons.Outlined.Keyboard, Color(0xFFF59E0B)),
                        ControlScreenTile(Routes.MEDIA_REMOTE, "Media Remote", Icons.Outlined.PlayCircle, Color(0xFFEF4444)),
                        ControlScreenTile(Routes.PRESENTATION, "Presentation", Icons.Outlined.CoPresent, Color(0xFF8B5CF6)),
                        ControlScreenTile(Routes.SHORTCUTS, "Shortcuts", Icons.Outlined.SettingsApplications, Color(0xFFEC4899))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in screens.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val tile1 = screens[i]
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp)
                                        .clickable {
                                            viewModel.vibrate(30)
                                            navController.navigate(tile1.route)
                                        }
                                        .testTag("tile_${tile1.route}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(tile1.color.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = tile1.icon,
                                                contentDescription = tile1.title,
                                                tint = tile1.color,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = tile1.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (i + 1 < screens.size) {
                                    val tile2 = screens[i + 1]
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(100.dp)
                                            .clickable {
                                                viewModel.vibrate(30)
                                                navController.navigate(tile2.route)
                                            }
                                            .testTag("tile_${tile2.route}"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(tile2.color.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tile2.icon,
                                                    contentDescription = tile2.title,
                                                    tint = tile2.color,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = tile2.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bluetooth Hosts Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paired Host Devices",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    TextButton(onClick = { viewModel.refreshPairedDevices() }) {
                        Text("Scan Bonded", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (pairedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No Devices",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No paired devices found.\nPlease pair your target PC/TV in your phone's system Bluetooth settings first.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            } else {
                items(pairedDevices) { device ->
                    val isConnecting = viewModel.hidManager.connectedDevice.value == device && connectionState == BluetoothProfile.STATE_CONNECTING
                    val isThisConnected = viewModel.hidManager.connectedDevice.value == device && connectionState == BluetoothProfile.STATE_CONNECTED

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isThisConnected) {
                                    viewModel.disconnectDevice()
                                } else {
                                    viewModel.connectToDevice(device)
                                }
                            }
                            .testTag("device_card_${device.address}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isThisConnected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isThisConnected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.bluetoothClass?.majorDeviceClass == 256) Icons.Default.Computer else Icons.Default.Tv,
                                contentDescription = "Device Type",
                                tint = if (isThisConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.getSafeName(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = device.address,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else if (isThisConnected) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("ACTIVE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                }
                            } else if (device.address == lastConnectedDeviceAddress) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) {
                                    Text("LAST ACTIVE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                }
                            } else {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Connect", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Quick Setup Navigation Link Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.SETTINGS) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Configure Mouse & Scroll Speeds", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.ABOUT) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Pairing Guide & Documentation", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

data class ControlScreenTile(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

// ==========================================
// TOUCHPAD SCREEN
// ==========================================
@Composable
fun TouchpadScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var isRightScrollActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Scroll inertia state
    var scrollVelocity by remember { mutableFloatStateOf(0f) }
    var isInertiaScrolling by remember { mutableStateOf(false) }
    var inertiaJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Laptop Touchpad", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            // Touchpad Instruction Alert
            Text(
                text = "Tap for Left-Click • Double Tap for Double-Click • Swipe Scroll bar to Scroll",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Touch Area Box Row
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Main Touchpad Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .testTag("touchpad_area")
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                var lastTapTime = 0L
                                while (true) {
                                    val down = awaitFirstDown()
                                    val startTime = System.currentTimeMillis()
                                    val startPos = down.position
                                    var isDrag = false
                                    var prevPosition = startPos

                                    val dragResult = drag(down.id) { change ->
                                        val currentPosition = change.position
                                        val dragAmount = currentPosition - prevPosition
                                        prevPosition = currentPosition

                                        val dist = (currentPosition - startPos).getDistance()
                                        if (dist > 8f) {
                                            isDrag = true
                                        }

                                        if (isDrag) {
                                            change.consume()
                                            // Send mouse movement
                                            viewModel.sendTouchMove(dragAmount.x, dragAmount.y)
                                        }
                                    }

                                    if (!isDrag) {
                                        val tapTime = System.currentTimeMillis()
                                        if (tapTime - lastTapTime < 250) {
                                            viewModel.sendMouseClick(1)
                                            viewModel.sendMouseClick(1)
                                            lastTapTime = 0L
                                        } else {
                                            viewModel.sendMouseClick(1)
                                            lastTapTime = tapTime
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Central subtle grid decorative visualizer
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeColor = Color(0x0AFFFFFF)
                        val step = 40.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(strokeColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(strokeColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }
                    Text("Touchpad Canvas", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Scroll Bar Area on the right (with inertia)
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .testTag("touchpad_scroll_bar")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isRightScrollActive = true
                                    inertiaJob?.cancel()
                                    isInertiaScrolling = false
                                },
                                onDragEnd = {
                                    isRightScrollActive = false
                                    // Apply inertia based on final velocity
                                    if (kotlin.math.abs(scrollVelocity) > 0.5f) {
                                        isInertiaScrolling = true
                                        inertiaJob = coroutineScope.launch {
                                            var velocity = scrollVelocity
                                            while (kotlin.math.abs(velocity) > 0.1f) {
                                                val tick = if (velocity > 0) 1 else -1
                                                viewModel.hidManager.sendMouseInput(0, 0, 0, tick.toByte())
                                                delay(30)
                                                velocity *= 0.9f // Deceleration factor
                                            }
                                            isInertiaScrolling = false
                                        }
                                    }
                                    scrollVelocity = 0f
                                },
                                onDragCancel = {
                                    isRightScrollActive = false
                                    scrollVelocity = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Track velocity for inertia
                                    scrollVelocity = dragAmount.y
                                    // Send scroll relative ticks
                                    val tick = if (dragAmount.y > 0) -1 else 1
                                    viewModel.hidManager.sendMouseInput(0, 0, 0, tick.toByte())
                                    viewModel.vibrate(10)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Scroll Up", tint = MaterialTheme.colorScheme.onSurface)
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Scroll Indicators",
                            tint = if (isRightScrollActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Physical Mouse Buttons (Left Click, Middle/Scroll toggle, Right Click)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Click Card
                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(1) }
                        .testTag("mouse_left_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Left Click", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Middle Scroll Click Card
                Card(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(4) }
                        .testTag("mouse_middle_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = "Middle Click", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Right Click Card
                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(2) }
                        .testTag("mouse_right_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Right Click", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// AIR MOUSE SCREEN
// ==========================================
@Composable
fun AirMouseScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val isAppRegistered by viewModel.isAppRegistered.collectAsState()
    val connectionState by viewModel.bluetoothState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var isFreeMode by remember { mutableStateOf(false) }
    var isFreeStreaming by remember { mutableStateOf(false) }
    
    // Hold to Move detection
    val holdInteractionSource = remember { MutableInteractionSource() }
    val isHoldPressed by holdInteractionSource.collectIsPressedAsState()

    // Unified streaming logic based on selected mode
    val isStreaming = if (isFreeMode) isFreeStreaming else isHoldPressed

    // Trigger action based on streaming state
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            viewModel.vibrate(20)
            viewModel.startAirMouse(buttonsState = 0)
        } else {
            viewModel.stopAirMouse()
        }
    }

    DisposableEffect(key1 = true) {
        onDispose {
            viewModel.stopAirMouse()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Air Mouse Controller", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSensitivityDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // Connection alert check
            if (connectionState != BluetoothProfile.STATE_CONNECTED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Device not connected. Connect to a host in the home screen to stream movement reports.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Mode Selector: Hold Mode vs Free Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hold Mode Option
                val isHoldSelected = !isFreeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isHoldSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (isFreeMode) {
                                viewModel.vibrate(25)
                                isFreeMode = false
                                isFreeStreaming = false // reset free streaming when switching
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BackHand,
                            contentDescription = "Hold Mode",
                            tint = if (isHoldSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hold Mode",
                            color = if (isHoldSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Free Mode Option
                val isFreeSelected = isFreeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFreeSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (!isFreeMode) {
                                viewModel.vibrate(25)
                                isFreeMode = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mouse,
                            contentDescription = "Free Mode",
                            tint = if (isFreeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Free Mode",
                            color = if (isFreeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Central Glowing Mouse Activation Circle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    if (isStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.primary,
                                    if (isStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                        .then(
                            if (isFreeMode) {
                                Modifier.clickable {
                                    viewModel.vibrate(30)
                                    isFreeStreaming = !isFreeStreaming
                                }
                            } else Modifier
                        )
                        .testTag("air_mouse_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(208.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isFreeMode) Icons.Default.Mouse else Icons.Default.ScreenRotation,
                                contentDescription = "Gyroscope Status",
                                tint = if (isStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isStreaming) "STREAMING" else if (isFreeMode) "TAP TO START" else "READY",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isFreeMode) "Tap center circle or bottom button to toggle motion streaming" else "Calibrate sensor if mouse cursor drifts automatically",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Interactive Buttons (Calibration, hold controller, click buttons)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dual Action Mouse Controller Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Click Trigger
                    Button(
                        onClick = { viewModel.sendMouseClick(1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("air_mouse_left_click"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Left Click", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Right Click Trigger
                    Button(
                        onClick = { viewModel.sendMouseClick(2) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("air_mouse_right_click"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Right Click", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Streaming control button based on mode
                if (isFreeMode) {
                    Button(
                        onClick = {
                            viewModel.vibrate(30)
                            isFreeStreaming = !isFreeStreaming
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("air_mouse_free_toggle"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFreeStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = if (isFreeStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Free Mouse",
                            tint = if (isFreeStreaming) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (isFreeStreaming) "Stop Streaming" else "Start Free Mouse",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFreeStreaming) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Button(
                        onClick = {},
                        interactionSource = holdInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("air_mouse_hold_move"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHoldPressed) Color(0xFF10B981) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BackHand,
                            contentDescription = "Hold to Move",
                            tint = if (isHoldPressed) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (isHoldPressed) "Streaming..." else "Hold to Move",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHoldPressed) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Calibration Trigger Button
                OutlinedButton(
                    onClick = { viewModel.calibrateAirMouse() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("air_mouse_calibrate"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(imageVector = Icons.Default.FilterCenterFocus, contentDescription = "Calibrate", tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calibrate Gyro Scope", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // Sensitivity Settings Dialog
    if (showSensitivityDialog) {
        AlertDialog(
            onDismissRequest = { showSensitivityDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Sensitivity Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Gyro Sensitivity",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Adjust the speed of the Air Mouse cursor pointer motion relative to physical device rotation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Value",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format("%.1fx", settings.sensitivity),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = settings.sensitivity,
                        onValueChange = { viewModel.updateSettings(settings.copy(sensitivity = it)) },
                        valueRange = 0.2f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag("air_mouse_gyro_sensitivity")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSensitivityDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ==========================================
// KEYBOARD SCREEN
// ==========================================
@Composable
fun KeyboardScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }
    
    // Toggle modifier button states
    var ctrlPressed by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var altPressed by remember { mutableStateOf(false) }
    var winPressed by remember { mutableStateOf(false) }

    fun getModifierByte(): Byte {
        var mask = 0
        if (ctrlPressed) mask = mask or 0x01
        if (shiftPressed) mask = mask or 0x02
        if (altPressed) mask = mask or 0x04
        if (winPressed) mask = mask or 0x08
        return mask.toByte()
    }

    // Maps a standard ASCII char and transmits over HID
    fun transmitCharacter(char: Char) {
        var modifier: Byte = getModifierByte()
        var keyCode: Byte = 0
        
        when (char) {
            in 'a'..'z' -> keyCode = (0x04 + (char - 'a')).toByte()
            in 'A'..'Z' -> {
                modifier = (modifier.toInt() or 0x02).toByte() // Shift
                keyCode = (0x04 + (char - 'A')).toByte()
            }
            in '1'..'9' -> keyCode = (0x1E + (char - '1')).toByte()
            '0' -> keyCode = 0x27.toByte()
            ' ' -> keyCode = 0x2C.toByte()
            '\n' -> keyCode = 0x28.toByte()
            '\t' -> keyCode = 0x2B.toByte()
            '!' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x1E.toByte() }
            '@' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x1F.toByte() }
            '#' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x20.toByte() }
            '$' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x21.toByte() }
            '%' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x22.toByte() }
            '^' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x23.toByte() }
            '&' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x24.toByte() }
            '*' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x25.toByte() }
            '(' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x26.toByte() }
            ')' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x27.toByte() }
            '-' -> keyCode = 0x2D.toByte()
            '_' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x2D.toByte() }
            '=' -> keyCode = 0x2E.toByte()
            '+' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x2E.toByte() }
            '[' -> keyCode = 0x2F.toByte()
            '{' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x2F.toByte() }
            ']' -> keyCode = 0x30.toByte()
            '}' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x30.toByte() }
            '\\' -> keyCode = 0x31.toByte()
            '|' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x31.toByte() }
            ';' -> keyCode = 0x33.toByte()
            ':' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x33.toByte() }
            '\'' -> keyCode = 0x34.toByte()
            '"' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x34.toByte() }
            ',' -> keyCode = 0x36.toByte()
            '<' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x36.toByte() }
            '.' -> keyCode = 0x37.toByte()
            '>' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x37.toByte() }
            '/' -> keyCode = 0x38.toByte()
            '?' -> { modifier = (modifier.toInt() or 0x02).toByte(); keyCode = 0x38.toByte() }
        }
        
        if (keyCode != 0.toByte()) {
            viewModel.hidManager.sendKeyPress(modifier, keyCode)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Keyboard Input", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Input Box for full string transmissions
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type Text to Transmit", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keyboard_text_field"),
                        placeholder = { Text("Enter sentence here...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.vibrate(30)
                            coroutineScope.launch {
                                val text = textInput
                                textInput = ""
                                text.forEach { char ->
                                    transmitCharacter(char)
                                    delay(15) // small latency gap between characters
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_text_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send String to Host", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Keyboard Modifier Switches Row (Mechanical style toggles)
            Text("Modifiers (Toggles)", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modifiers = listOf(
                    ModifierTile("CTRL", ctrlPressed) { ctrlPressed = !ctrlPressed },
                    ModifierTile("SHIFT", shiftPressed) { shiftPressed = !shiftPressed },
                    ModifierTile("ALT", altPressed) { altPressed = !altPressed },
                    ModifierTile("WIN", winPressed) { winPressed = !winPressed }
                )
                modifiers.forEach { mod ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable {
                                viewModel.vibrate(20)
                                mod.onClick()
                            }
                            .testTag("modifier_${mod.label}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (mod.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, if (mod.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = mod.label,
                                    color = if (mod.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (mod.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // Modern Virtual QWERTY Keyboard
            Text(
                text = "Interactive Virtual Keyboard",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Number Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val numRow = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
                        numRow.forEach { num ->
                            KeycapButton(
                                char = num,
                                isUppercase = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(num)
                                }
                            )
                        }
                    }

                    // Row 1 (QWERTY)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
                        row1.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }
                    }

                    // Row 2 (ASDF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
                        row2.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.weight(0.2f))
                    }

                    // Row 3 (ZXCV + Shift + Backspace)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Shift Key
                        Card(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    shiftPressed = !shiftPressed
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (shiftPressed) MaterialTheme.colorScheme.primary else Color(0xFF1E293B)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Shift",
                                    tint = if (shiftPressed) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
                        row3.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }

                        // Backspace Key
                        Card(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2A.toByte()) // Backspace scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3F1A1A)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF5F2D2D))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Row 4 (Tab + Space + Enter)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab key
                        Card(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2B.toByte()) // Tab scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("TAB", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Space key
                        Card(
                            modifier = Modifier
                                .weight(5f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2C.toByte()) // Space scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("SPACE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Enter key
                        Card(
                            modifier = Modifier
                                .weight(2f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x28.toByte()) // Enter scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("ENTER", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Arrow Keys and D-Pad Controls
            Text("Navigation & Utilities", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad Cross Keypad (Left side)
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("D-PAD", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        
                        // Row 1: Up Arrow
                        Row {
                            Spacer(modifier = Modifier.size(44.dp))
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x52.toByte())
                                    }
                                    .testTag("key_arrow_up"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.size(44.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Row 2: Left, Center (OK), Right
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x50.toByte())
                                    }
                                    .testTag("key_arrow_left"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White)
                                }
                            }
                            
                            // Center space dot
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF334155), CircleShape))
                            }
                            
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x4F.toByte())
                                    }
                                    .testTag("key_arrow_right"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", tint = Color.White)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Row 3: Down Arrow
                        Row {
                            Spacer(modifier = Modifier.size(44.dp))
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x51.toByte())
                                    }
                                    .testTag("key_arrow_down"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.size(44.dp))
                        }
                    }
                    
                    // Vertical separator line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(130.dp)
                            .background(Color(0xFF1E293B))
                    )
                    
                    // Utility Actions Column (Right side)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SHORTCUTS", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        
                        val actions = listOf(
                            Pair("Escape (ESC)", 0x29.toByte()),
                            Pair("Backspace", 0x2A.toByte()),
                            Pair("Tab Key", 0x2B.toByte()),
                            Pair("Enter Key", 0x28.toByte())
                        )
                        actions.forEach { (label, scanCode) ->
                            Button(
                                onClick = {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), scanCode)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("key_shortcut_${label.lowercase().replace(" ", "_").replace("(", "").replace(")", "")}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Desktop Navigation Utilities
            Text("Desktop Utilities", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                val keysList = listOf(
                    Triple("Home", 0x4A.toByte(), "home"),
                    Triple("End", 0x4D.toByte(), "end"),
                    Triple("Pg Up", 0x4B.toByte(), "page_up"),
                    Triple("Pg Dn", 0x4E.toByte(), "page_down")
                )
                items(keysList) { key ->
                    Button(
                        onClick = {
                            viewModel.vibrate(15)
                            viewModel.sendKeyboardKey(getModifierByte(), key.second)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("key_${key.third}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key.first, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun KeycapButton(
    char: Char,
    isUppercase: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val text = if (isUppercase) char.uppercaseChar().toString() else char.toString()
    Card(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class ModifierTile(
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit
)

// ==========================================
// MEDIA REMOTE SCREEN
// ==========================================
@Composable
fun MediaRemoteScreen(navController: NavController, viewModel: AirMouseViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Media Remote", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Power & Home Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // System Power Key
                Button(
                    onClick = { viewModel.sendMediaAction(0x40) }, // Bit 6 (Power)
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("media_power"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Power", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Home Key
                Button(
                    onClick = { viewModel.sendMediaAction(0x80.toByte()) }, // Bit 7 (Menu / Home)
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("media_home"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Home", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 2. Circular D-Pad Controller
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Center SELECT/OK button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.sendKeyboardKey(0, 0x28.toByte()) } // Enter Key
                        .testTag("media_ok"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Up Arrow
                IconButton(
                    onClick = { viewModel.sendKeyboardKey(0, 0x52.toByte()) }, // Up Arrow
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(48.dp)
                        .testTag("media_dpad_up")
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }

                // Down Arrow
                IconButton(
                    onClick = { viewModel.sendKeyboardKey(0, 0x51.toByte()) }, // Down Arrow
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .size(48.dp)
                        .testTag("media_dpad_down")
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }

                // Left Arrow
                IconButton(
                    onClick = { viewModel.sendKeyboardKey(0, 0x50.toByte()) }, // Left Arrow
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .testTag("media_dpad_left")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }

                // Right Arrow
                IconButton(
                    onClick = { viewModel.sendKeyboardKey(0, 0x4F.toByte()) }, // Right Arrow
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(48.dp)
                        .testTag("media_dpad_right")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }
            }

            // 3. Navigation Shortcuts Row: Back & Enter/Select
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Escape key
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x29.toByte()) }, // Escape / Back
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("media_back_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Direct Select / Enter key
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x28.toByte()) }, // Enter / Select
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("media_enter_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Enter", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 4. Playback Controls Card with Fast Forward and Rewind Added
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track
                    IconButton(
                        onClick = { viewModel.sendMediaAction(0x20) }, // Bit 5
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_prev")
                    ) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev Track", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }

                    // Fast Rewind / Skip Backward
                    IconButton(
                        onClick = { viewModel.sendKeyboardKey(0, 0x50.toByte()) }, // Left Arrow (universal Rewind)
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_rewind")
                    ) {
                        Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Rewind", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }

                    // Play/Pause Playback
                    IconButton(
                        onClick = { viewModel.sendMediaAction(0x08) }, // Bit 3
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("media_play_pause")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Pause", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    }

                    // Fast Forward / Skip Forward
                    IconButton(
                        onClick = { viewModel.sendKeyboardKey(0, 0x4F.toByte()) }, // Right Arrow (universal Fast Forward)
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_fast_forward")
                    ) {
                        Icon(imageVector = Icons.Default.FastForward, contentDescription = "Fast Forward", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }

                    // Next Track
                    IconButton(
                        onClick = { viewModel.sendMediaAction(0x10) }, // Bit 4
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_next")
                    ) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next Track", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // 5. Volume Control Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vol Down
                    IconButton(
                        onClick = { viewModel.sendMediaAction(0x02) }, // Bit 1
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_vol_down")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }

                    // Mute
                    Button(
                        onClick = { viewModel.sendMediaAction(0x04) }, // Bit 2
                        modifier = Modifier
                            .height(44.dp)
                            .width(120.dp)
                            .testTag("media_mute"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeMute, contentDescription = "Mute", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mute", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Vol Up
                    IconButton(
                        onClick = { viewModel.sendMediaAction(0x01) }, // Bit 0
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("media_vol_up")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// PRESENTATION REMOTE SCREEN
// ==========================================
@Composable
fun PresentationScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var timerSeconds by remember { mutableStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timerRunning) {
                delay(1000)
                timerSeconds++
            }
        }
    }

    fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Presentation Controller", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Visual presentation active timer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Slide Timer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatTime(timerSeconds),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.vibrate(30)
                            timerRunning = !timerRunning
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timerRunning) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                        )
                    ) {
                        Text(if (timerRunning) "Pause" else "Start", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // GIANT slide previous/next controllers
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Giant Next Slide Card (Main trigger)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clickable {
                            // USB HID Page Down key (0x4E) acts as standard PowerPoint/Keynote Next Slide
                            viewModel.sendKeyboardKey(0, 0x4E.toByte())
                        }
                        .testTag("pres_next_slide"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(72.dp))
                        Text("Next Slide", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                // Giant Previous Slide Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            // USB HID Page Up key (0x4B) acts as standard slide previous
                            viewModel.sendKeyboardKey(0, 0x4B.toByte())
                        }
                        .testTag("pres_prev_slide"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Prev", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Previous Slide", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // PowerPoint system key controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Full Screen toggle: F5
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x3E.toByte()) }, // F5
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("pres_full_screen"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Full Screen", tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play (F5)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Black Screen: B Key (0x05)
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x05.toByte()) }, // B key
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("pres_black_screen"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = "Black Screen", tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Black Screen", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// CUSTOM SHORTCUTS SCREEN
// ==========================================
@Composable
fun ShortcutsScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val shortcuts by viewModel.shortcutsState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var shortcutName by remember { mutableStateOf("") }
    var ctrlSelected by remember { mutableStateOf(false) }
    var shiftSelected by remember { mutableStateOf(false) }
    var altSelected by remember { mutableStateOf(false) }
    var guiSelected by remember { mutableStateOf(false) }
    var selectedKeyCodeStr by remember { mutableStateOf("6") } // Defaults to C

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Custom Shortcuts", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.vibrate(30)
                                showAddDialog = true
                            },
                            modifier = Modifier.testTag("add_shortcut_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Shortcut", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (shortcuts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Keyboard, contentDescription = "No Shortcuts", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Custom Shortcuts Added", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Press the + icon on top to create customized hotkey macros.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shortcuts) { shortcut ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.triggerCustomShortcut(shortcut) }
                                .testTag("shortcut_card_${shortcut.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.SettingsSystemDaydream, contentDescription = "Macro", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(shortcut.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        val modLabel = buildString {
                                            if (shortcut.modifiers and 0x01 != 0) append("Ctrl ")
                                            if (shortcut.modifiers and 0x02 != 0) append("Shift ")
                                            if (shortcut.modifiers and 0x04 != 0) append("Alt ")
                                            if (shortcut.modifiers and 0x08 != 0) append("Win ")
                                        }
                                        Text(
                                            text = "Keys: $modLabel+ HID_CODE ${shortcut.keyCodes}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.vibrate(30)
                                        viewModel.deleteShortcut(shortcut.id)
                                    },
                                    modifier = Modifier.testTag("delete_shortcut_${shortcut.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // ADD DIALOG
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("Add Shortcut Macro", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = shortcutName,
                                onValueChange = { shortcutName = it },
                                label = { Text("Shortcut Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_name_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Text("Select Modifiers", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckboxLabel("Ctrl", ctrlSelected) { ctrlSelected = it }
                                CheckboxLabel("Shift", shiftSelected) { shiftSelected = it }
                                CheckboxLabel("Alt", altSelected) { altSelected = it }
                                CheckboxLabel("Win", guiSelected) { guiSelected = it }
                            }

                            OutlinedTextField(
                                value = selectedKeyCodeStr,
                                onValueChange = { selectedKeyCodeStr = it },
                                label = { Text("HID Key ScanCode (Integer, standard C is 6)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_code_field"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (shortcutName.isNotBlank() && selectedKeyCodeStr.isNotBlank()) {
                                    var modifiers = 0
                                    if (ctrlSelected) modifiers = modifiers or 0x01
                                    if (shiftSelected) modifiers = modifiers or 0x02
                                    if (altSelected) modifiers = modifiers or 0x04
                                    if (guiSelected) modifiers = modifiers or 0x08

                                    viewModel.addCustomShortcut(shortcutName, modifiers, selectedKeyCodeStr)

                                    // Reset Dialog variables
                                    shortcutName = ""
                                    ctrlSelected = false
                                    shiftSelected = false
                                    altSelected = false
                                    guiSelected = false
                                    selectedKeyCodeStr = "6"
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier.testTag("dialog_confirm")
                        ) {
                            Text("Add Macro")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CheckboxLabel(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ==========================================
// SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val settings by viewModel.settingsState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Pointer Speeds & Calibration", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            // Cursor Sensitivity Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cursor Sensitivity", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(String.format("%.1fx", settings.sensitivity), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = settings.sensitivity,
                    onValueChange = { viewModel.updateSettings(settings.copy(sensitivity = it)) },
                    valueRange = 0.2f..3.0f,
                    modifier = Modifier.testTag("setting_sensitivity")
                )
            }

            // Motion Smoothing Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Motion Smoothing (Low Pass)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(String.format("%.1fx", settings.smoothing), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = settings.smoothing,
                    onValueChange = { viewModel.updateSettings(settings.copy(smoothing = it)) },
                    valueRange = 0.05f..0.9f,
                    modifier = Modifier.testTag("setting_smoothing")
                )
            }

            // Gyro Dead Zone Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gyro Dead Zone", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(String.format("%.2f", settings.deadZone), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = settings.deadZone,
                    onValueChange = { viewModel.updateSettings(settings.copy(deadZone = it)) },
                    valueRange = 0.01f..0.2f,
                    modifier = Modifier.testTag("setting_deadzone")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text("Feedback & Device", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            // Vibration switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Haptic Touch Feedback", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Vibrate phone during mouse clicks and taps", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = settings.vibrationFeedback,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(vibrationFeedback = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_vibrate")
                )
            }

            // Invert X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Invert Horizontal (X) Motion", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Inverts mouse left and right pointer actions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = settings.invertX,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(invertX = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_invert_x")
                )
            }

            // Invert Y
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Invert Vertical (Y) Motion", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Inverts mouse up and down pointer actions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = settings.invertY,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(invertY = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_invert_y")
                )
            }

            // Auto Reconnect Toggle
            val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Reconnect Device", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Automatically reconnect to your last active device when Bluetooth starts up", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = autoReconnectEnabled,
                    onCheckedChange = { viewModel.setAutoReconnectEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_auto_reconnect")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text("Appearance", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            // Dark Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark Theme", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Switch between dark and light theme", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.themeDark,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(themeDark = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_dark_theme")
                )
            }

            // Material You Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Material You", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Use wallpaper-based dynamic colors (Android 12+)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.useDynamicColors,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(useDynamicColors = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_dynamic_colors")
                )
            }

            // Keep Screen Awake Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Screen Awake", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Prevent screen from turning off while using the app", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.keepScreenAwake,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(keepScreenAwake = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("setting_keep_screen_awake")
                )
            }
        }
    }
}

// ==========================================
// ABOUT & DOCUMENTATION SCREEN
// ==========================================
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About & Help", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AirMouse Guide",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Thank you for using AirMouse! This application turns your Android phone into an ultra-low latency, highly responsive, serverless Bluetooth controller.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text("Setup Instructions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Text(
                text = "1. Turn ON Bluetooth on your phone and target device (PC, Tablet, macOS, or Smart TV).\n" +
                        "2. Go to your phone's system settings and Pair your target host device via standard Bluetooth settings.\n" +
                        "3. Return to the AirMouse app console, refresh, and click on your host under 'Paired Host Devices'.\n" +
                        "4. Your phone will register securely as a combined hardware mouse and keyboard. Once connected, open any control screen to begin!",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 24.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text("Troubleshooting", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Text(
                text = "• Connection Rejected: If your PC/TV fails to connect, unpair (forget) the AirMouse phone on both devices, restart Bluetooth, and pair again.\n" +
                        "• Drift: If the Air Mouse cursor wanders around automatically without moving, place the phone flat on a table and click 'Calibrate Gyro Scope' inside the Air Mouse screen.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text("Specifications", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Text(
                text = "• Platform: Native Android (Kotlin & Jetpack Compose)\n" +
                        "• Bluetooth HID API Level: 28 (Android 9.0+) Required\n" +
                        "• Profile: official Android BluetoothHidDevice (SDP Combo)\n" +
                        "• Version: 1.2.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun StickyConnectionIndicator(viewModel: AirMouseViewModel, navController: NavController? = null) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val isBluetoothPowerOn by viewModel.isBluetoothPowerOn.collectAsState()

    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED
    val isConnecting = connectionState == BluetoothProfile.STATE_CONNECTING

    val backgroundColor = when {
        !isBluetoothPowerOn -> MaterialTheme.colorScheme.errorContainer
        isConnected -> Color(0xFF064E3B) // Dark green for connected
        isConnecting -> Color(0xFF451A03) // Dark amber for connecting
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        !isBluetoothPowerOn -> MaterialTheme.colorScheme.error
        isConnected -> Color(0xFF10B981) // Green for connected
        isConnecting -> Color(0xFFF59E0B) // Amber for connecting
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when {
        !isBluetoothPowerOn -> "BLUETOOTH IS TURNED OFF - CLICK TO TURN ON"
        isConnected -> "CONNECTED: ${connectedDevice?.getSafeName() ?: "Host Device"}"
        isConnecting -> "CONNECTING: ${connectedDevice?.getSafeName() ?: "Host Device"}..."
        else -> "DISCONNECTED - TAP TO RECONNECT"
    }

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.vibrate(30)
                if (!isBluetoothPowerOn) {
                    viewModel.enableBluetooth()
                } else if (!isConnected) {
                    navController?.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.DASHBOARD) { inclusive = false }
                    }
                }
            },
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isConnected || isConnecting || !isBluetoothPowerOn) pulseAlpha else 1f)
                    .background(
                        color = contentColor,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = if (isConnected) MaterialTheme.colorScheme.onPrimary else contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

