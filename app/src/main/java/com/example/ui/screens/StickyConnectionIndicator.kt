@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.bluetooth.BluetoothProfile
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.bluetooth.getSafeName

@Composable
fun StickyConnectionIndicator(viewModel: AirMouseViewModel, navController: NavController? = null) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val connectedRssi by viewModel.connectedRssi.collectAsState()
    val targetDevice by viewModel.targetDevice.collectAsState()
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
        !isBluetoothPowerOn -> MaterialTheme.colorScheme.onErrorContainer
        isConnected -> Color(0xFF10B981) // Green for connected
        isConnecting -> Color(0xFFF59E0B) // Amber for connecting
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.vibrate(30)
                if (!isBluetoothPowerOn) {
                    viewModel.enableBluetooth()
                } else if (!isConnected && !isConnecting) {
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
            if (isConnected) {
                Text(
                    text = "CONNECTED: ${connectedDevice?.getSafeName() ?: "Host Device"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (connectedRssi != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.NetworkCell,
                        contentDescription = "Signal Strength",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$connectedRssi dBm",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (isConnecting) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = "Connecting",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CONNECTING TO ${targetDevice?.getSafeName() ?: "Host Device"}...",
                    color = Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.cancelConnection() },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Connection",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (!isBluetoothPowerOn) Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (!isBluetoothPowerOn) "BLUETOOTH IS TURNED OFF - CLICK TO TURN ON" else "DISCONNECTED - TAP TO RECONNECT",
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
