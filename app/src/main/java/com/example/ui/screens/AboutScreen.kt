@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.BuildConfig
import com.example.ui.AdaptiveScreenBody

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
        AdaptiveScreenBody(
            modifier = Modifier.padding(innerPadding),
            horizontalPadding = 24.dp,
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AirMouse Guide",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Thank you for using AirMouse! This application turns your Android phone into an ultra-low latency, highly responsive, serverless Bluetooth controller.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 1. Windows 10/11 Pairing Guide Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.size(14.dp),
                                verticalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.onSecondaryContainer))
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.onSecondaryContainer))
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.onSecondaryContainer))
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.onSecondaryContainer))
                                }
                            }
                        }
                        Text(
                            text = "Windows 10 / 11 Setup Guide",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Text(
                        text = "Windows caches Bluetooth service descriptors strictly. If your PC does not connect or recognize AirMouse, follow these steps exactly:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val steps = listOf(
                            "1. Unpair on Windows: Go to Settings > Bluetooth & devices, locate your phone, and choose 'Remove device'.",
                            "2. Unpair on Phone: Go to system Bluetooth settings, find your PC under paired devices, and tap 'Forget/Unpair'.",
                            "3. Restart Bluetooth: Toggle Bluetooth OFF and back ON on both your PC and your phone.",
                            "4. Open AirMouse: Launch this app on your phone to register the virtual keyboard/mouse HID profile in the background.",
                            "5. Pair from Windows (Not Phone): On Windows, click 'Add device' > 'Bluetooth'. Select your phone and pair.",
                            "6. Driver Installation: Wait 5-10 seconds for Windows to finish installing the HID driver until it says 'Device is ready'.",
                            "7. Connect: Return to the AirMouse main dashboard, click your PC name under Paired Devices, and begin controlling!"
                        )
                        for (step in steps) {
                            Text(
                                text = step,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 2. Dedicated Smart TV & Android TV Setup Guide Card (NEW!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Smart TV",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Smart TV & Android TV Setup Guide",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Text(
                        text = "Crucial Notice: Many Smart TVs (including Fire TV, Android TV, Google TV, and Samsung/LG TVs) enforce strict Bluetooth host roles. If you try to initiate the connection from the phone app, the TV will immediately reject/disconnect it, resulting in a rapid connect-disconnect loop. Follow this procedure to establish a stable connection:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tvSteps = listOf(
                            "1. Break the Loop: If the devices are currently looping, turn Bluetooth OFF and ON again on your phone.",
                            "2. Unpair on Both: Unpair/forget the phone on your TV, and unpair the TV in your phone's Bluetooth settings.",
                            "3. Keep App Open: Open the AirMouse app on your phone. This initializes the HID profile and makes your phone discoverable.",
                            "4. Connect FROM the TV (Important): Go to your TV's Bluetooth Settings (often under Accessories or Remotes & Accessories). Search for new devices, select your phone from the scan results, and pair.",
                            "5. Accept Pairing: Watch for any pairing code dialogs on your phone screen and tap 'Pair' and 'Allow access'.",
                            "6. Completed: The TV acts as the host and will establish a robust, permanent connection with the phone without dropping!"
                        )
                        for (step in tvSteps) {
                            Text(
                                text = step,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 3. General Setup Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "General Setup Instructions",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "1. Turn ON Bluetooth on your phone and target device (PC, Tablet, macOS, or Smart TV).\n" +
                                "2. Go to your phone's system settings and pair your target host device via standard Bluetooth settings.\n" +
                                "3. Return to the AirMouse app console, refresh, and click on your host under 'Paired Host Devices'.\n" +
                                "4. Your phone will register securely as a combined hardware mouse and keyboard. Once connected, open any control screen to begin!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            // 4. Troubleshooting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Troubleshooting Guide",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "• Connection Rejected / Loop: Windows and Smart TVs cache Bluetooth capabilities. If pairing drops or fails, follow the TV or Windows Setup Guide above to refresh their service discovery caches.\n" +
                                "• Drift: If the Air Mouse cursor drifts automatically, place the phone flat on a stable surface and click 'Calibrate Gyroscope' inside the Air Mouse screen.\n" +
                                "• Input Lag: Ensure your phone and host PC do not have aggressive power-saving modes enabled on their Bluetooth controllers.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // 5. Technical Specifications Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Technical Specifications",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "• Platform: Native Android (Kotlin & Jetpack Compose)\n" +
                                "• Bluetooth HID API Level: 28 (Android 9.0+) Required\n" +
                                "• Profile: official Android BluetoothHidDevice (SDP Combo)\n" +
                                "• Provider / Device: Generic HID Device (AirMouse)\n" +
                                "• Subclass: Combo Keyboard/Pointer (0x03)\n" +
                                "• Version: ${BuildConfig.VERSION_NAME}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
