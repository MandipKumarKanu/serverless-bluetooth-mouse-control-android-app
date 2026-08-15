@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.bluetooth.BluetoothProfile
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.bluetooth.getSafeName
import com.example.bluetooth.isComputer
import com.example.ui.AdaptiveListBody
import com.example.ui.rememberContentMaxWidth

// ==========================================
// MAIN DASHBOARD (HOME SCREEN)
// ==========================================
@Composable
fun DashboardScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val lastConnectedDeviceAddress by viewModel.lastConnectedDeviceAddress.collectAsState()
    val connectionHistory by viewModel.connectionHistory.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isBluetoothPowerOn by viewModel.isBluetoothPowerOn.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Check if Material You (dynamic colors) is enabled
    val useDynamicColors = settings.useDynamicColors

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

    // Stop radio-heavy discovery when leaving the dashboard
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScanning() }
    }

    val listState = rememberLazyListState()

    // When a device connects, the dashboard restructures — the paired-device
    // list is replaced by the connected-device card and the control-mode grid
    // at the top. Scroll back to the top so the user isn't left at the old
    // scroll position after tapping a device deep in the list.
    LaunchedEffect(connectionState) {
        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AirMouse Console", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.vibrate(30)
                            navController.navigate(Routes.SETTINGS)
                        },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveListBody(modifier = Modifier.padding(innerPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Connection Status Card
            item {
                val isBluetoothPowerOn by viewModel.isBluetoothPowerOn.collectAsState()
                val targetDevice by viewModel.targetDevice.collectAsState()
                val isConnectingState = connectionState == BluetoothProfile.STATE_CONNECTING

                val cardColor = when {
                    !isBluetoothPowerOn -> MaterialTheme.colorScheme.errorContainer
                    isConnected -> Color(0xFF064E3B) // Dark green for connected
                    isConnectingState -> Color(0xFF451A03) // Dark amber for connecting
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val statusText = when {
                    !isBluetoothPowerOn -> "Bluetooth is Off - Tap to turn on"
                    isConnected -> "Connected to ${connectedDevice?.getSafeName() ?: "Unknown Device"}"
                    isConnectingState -> "Connecting to ${targetDevice?.getSafeName() ?: "Device"}..."
                    connectionState == BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Offline"
                }
                val statusIcon = when {
                    isConnected -> Icons.Filled.BluetoothConnected
                    isConnectingState -> Icons.AutoMirrored.Filled.BluetoothSearching
                    else -> Icons.Filled.BluetoothDisabled
                }
                val tintColor = when {
                    !isBluetoothPowerOn -> MaterialTheme.colorScheme.error
                    isConnected -> Color(0xFF10B981) // Green for connected
                    isConnectingState -> Color(0xFFF59E0B) // Amber for connecting
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
                                color = when {
                                    isConnected -> Color.White
                                    isConnectingState -> Color(0xFFF59E0B)
                                    !isBluetoothPowerOn -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
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
                        } else if (isConnectingState) {
                            IconButton(
                                onClick = { viewModel.cancelConnection() },
                                modifier = Modifier.testTag("cancel_connect")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Connection",
                                    tint = Color(0xFFF59E0B),
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
                        ControlScreenTile(Routes.SHORTCUTS, "Shortcuts", Icons.Outlined.SettingsApplications, Color(0xFFEC4899)),
                        ControlScreenTile(Routes.GAMEPAD, "Gamepad", Icons.Outlined.Gamepad, Color(0xFF06B6D4)),
                        ControlScreenTile(Routes.GESTURE, "Gestures", Icons.Outlined.Gesture, Color(0xFFFF6B35))
                    )

                    // Two tiles per row on phones, three on wide screens
                    val tileColumns = if (rememberContentMaxWidth() >= 600.dp) 3 else 2
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in screens.indices step tileColumns) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (j in 0 until tileColumns) {
                                    val index = i + j
                                    if (index < screens.size) {
                                        ControlModeTile(
                                            tile = screens[index],
                                            useDynamicColors = useDynamicColors,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                viewModel.vibrate(30)
                                                navController.navigate(screens[index].route)
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
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
                        text = if (isConnected) "Connected Device" else "Paired Host Devices",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (!isConnected) {
                        TextButton(onClick = { viewModel.refreshPairedDevices() }) {
                            Text("Scan Bonded", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // When connected, show only the connected device in compact form
            if (isConnected && connectedDevice != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.disconnectDevice() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothConnected,
                                    contentDescription = "Connected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = connectedDevice?.getSafeName() ?: "Unknown Device",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to disconnect",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("ACTIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                }
            } else if (pairedDevices.isEmpty()) {
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
            } else if (!isConnected) {
                items(pairedDevices) { device ->
                    val targetDevice by viewModel.targetDevice.collectAsState()
                    val isConnectingThisDevice = targetDevice?.address == device.address && connectionState == BluetoothProfile.STATE_CONNECTING
                    val isThisConnected = connectedDevice?.address == device.address && connectionState == BluetoothProfile.STATE_CONNECTED

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
                            containerColor = when {
                                isThisConnected -> MaterialTheme.colorScheme.surface
                                isConnectingThisDevice -> Color(0xFF451A03).copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = when {
                            isThisConnected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            isConnectingThisDevice -> BorderStroke(1.dp, Color(0xFFF59E0B))
                            else -> null
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.isComputer()) Icons.Default.Computer else Icons.Default.Tv,
                                contentDescription = "Device Type",
                                tint = when {
                                    isThisConnected -> MaterialTheme.colorScheme.primary
                                    isConnectingThisDevice -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
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
                                    text = if (isConnectingThisDevice) "Connecting..." else device.address,
                                    fontSize = 12.sp,
                                    color = if (isConnectingThisDevice) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (isConnectingThisDevice) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFF59E0B)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.cancelConnection() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel Connection",
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
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



            // 4. Discover Nearby Devices (only while disconnected)
            if (!isConnected && isBluetoothPowerOn) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Discover Nearby Devices",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        TextButton(
                            onClick = {
                                viewModel.vibrate(30)
                                if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                            }
                        ) {
                            Text(
                                text = if (isScanning) "Stop Scan" else "Scan",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isScanning) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Scanning for nearby PCs, Macs and TVs...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (scannedDevices.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scannedDevices.forEach { scanned ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Radar,
                                            contentDescription = "Nearby Device",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = scanned.device.getSafeName(),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = scanned.device.address,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { viewModel.bondScannedDevice(scanned.device) },
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Text("Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recent Connections
            if (connectionHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Connections",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        TextButton(onClick = { viewModel.clearConnectionHistory() }) {
                            Text(
                                text = "Clear",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            connectionHistory.forEachIndexed { index, conn ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.connectToDeviceByAddress(conn.deviceAddress) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conn.deviceName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${conn.deviceAddress} • ${formatTimestamp(conn.connectedAt)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Reconnect",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (index < connectionHistory.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
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
}

@Composable
private fun ControlModeTile(
    tile: ControlScreenTile,
    useDynamicColors: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick)
            .testTag("tile_${tile.route}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (useDynamicColors) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconColor = if (useDynamicColors) MaterialTheme.colorScheme.primary else tile.color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tile.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

data class ControlScreenTile(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
