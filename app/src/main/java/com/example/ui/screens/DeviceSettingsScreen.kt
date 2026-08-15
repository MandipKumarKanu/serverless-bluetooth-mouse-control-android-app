@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.bluetooth.getSafeName
import com.example.data.DeviceSettingsEntity
import com.example.ui.AdaptiveScreenBody
import com.example.viewmodel.AirMouseViewModel

// ==========================================
// DEVICE SPECIFIC SETTINGS SCREEN
// ==========================================
@Composable
fun DeviceSettingsScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val profiles by viewModel.deviceProfiles.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    var expandedAddress by remember { mutableStateOf<String?>(null) }

    fun deviceName(address: String): String =
        pairedDevices.firstOrNull { it.address == address }?.getSafeName()
            ?: "Device ··${address.takeLast(4)}"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Device Specific Settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveScreenBody(
            modifier = Modifier.padding(innerPadding),
            horizontalPadding = 16.dp,
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Each host keeps its own pointer profile. Tap a host to edit it — changes save automatically to that host. \"Reset to Global\" deletes the profile so the host falls back to your global settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            if (profiles.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No device profiles saved yet",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect a host and adjust its pointer settings — it will appear here with its own profile.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            profiles.forEach { profile ->
                val name = deviceName(profile.deviceAddress)
                val isConnected = profile.deviceAddress == connectedDevice?.address
                val expanded = expandedAddress == profile.deviceAddress

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header: name, connected badge, expand chevron
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.vibrate(15)
                                    expandedAddress = if (expanded) null else profile.deviceAddress
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (isConnected) {
                                    Text(
                                        text = "CONNECTED",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(rotation)
                            )
                        }

                        if (expanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                            // Sliders bind to the profile from the flow, so every
                            // change is written straight to this host's profile.
                            DeviceSliderRow(
                                label = "Cursor Sensitivity",
                                valueText = String.format("%.1fx", profile.sensitivity),
                                value = profile.sensitivity,
                                range = 0.2f..3.0f,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(sensitivity = it)) }
                            )
                            DeviceSliderRow(
                                label = "Scroll Speed",
                                valueText = String.format("%.1fx", profile.scrollSpeed),
                                value = profile.scrollSpeed,
                                range = 0.2f..3.0f,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(scrollSpeed = it)) }
                            )
                            DeviceSliderRow(
                                label = "Motion Smoothing (Low Pass)",
                                valueText = String.format("%.1fx", profile.smoothing),
                                value = profile.smoothing,
                                range = 0.05f..0.9f,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(smoothing = it)) }
                            )
                            DeviceSliderRow(
                                label = "Gyro Dead Zone",
                                valueText = String.format("%.2f", profile.deadZone),
                                value = profile.deadZone,
                                range = 0.01f..0.2f,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(deadZone = it)) }
                            )
                            DeviceSliderRow(
                                label = "Pointer Acceleration",
                                valueText = String.format("%.1f", profile.acceleration),
                                value = profile.acceleration,
                                range = 0.0f..3.0f,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(acceleration = it)) }
                            )
                            DeviceSwitchRow(
                                label = "Invert Horizontal (X) Motion",
                                checked = profile.invertX,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(invertX = it)) }
                            )
                            DeviceSwitchRow(
                                label = "Invert Vertical (Y) Motion",
                                checked = profile.invertY,
                                onChange = { viewModel.updateDeviceSettingsForDevice(profile.copy(invertY = it)) }
                            )

                            OutlinedButton(
                                onClick = {
                                    viewModel.vibrate(30)
                                    viewModel.resetDeviceSettingsForDevice(profile.deviceAddress)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("device_reset_${profile.deviceAddress}"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reset to Global", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(valueText, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun DeviceSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
