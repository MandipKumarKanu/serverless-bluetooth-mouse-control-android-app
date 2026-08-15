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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import android.widget.Toast
import androidx.compose.ui.input.pointer.positionChange
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.example.BuildConfig
import com.example.R
import com.example.data.SettingsEntity
import com.example.data.ShortcutEntity
import com.example.gesture.PointerSample
import com.example.gesture.TouchpadAction
import com.example.gesture.TouchpadGestureRecognizer
import com.example.viewmodel.AirMouseViewModel
import com.example.bluetooth.HidKeyMapper
import com.example.bluetooth.getSafeName
import com.example.bluetooth.isComputer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun StickyConnectionIndicator(viewModel: AirMouseViewModel, navController: NavController? = null) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
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
            } else if (isConnecting) {
                Icon(
                    imageVector = Icons.Default.BluetoothSearching,
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
