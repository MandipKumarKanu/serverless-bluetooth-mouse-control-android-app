@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.bluetooth.BluetoothProfile
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.ui.AdaptiveScreenBody
import com.example.ui.responsiveControlDiameter

// ==========================================
// AIR MOUSE SCREEN
// ==========================================
@Composable
fun AirMouseScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val connectionState by viewModel.bluetoothState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()

    var showSensitivityDialog by remember { mutableStateOf(false) }
    var isFreeMode by remember { mutableStateOf(com.example.service.AirMouseService.isAirMouseActive) }
    var isFreeStreaming by remember { mutableStateOf(com.example.service.AirMouseService.isAirMouseActive) }

    // Scales the activation pad with the window (bounded on tablets)
    val padDiameter = responsiveControlDiameter()

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
            if (!com.example.service.AirMouseService.isAirMouseActive) {
                viewModel.stopAirMouse()
            }
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
        AdaptiveScreenBody(
            modifier = Modifier.padding(innerPadding),
            horizontalPadding = 24.dp,
            verticalPadding = 24.dp,
            scrollable = true,
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
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
                        .size(padDiameter)
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
                            .size(padDiameter - 12.dp)
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
