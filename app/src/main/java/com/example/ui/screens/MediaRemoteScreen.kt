@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import kotlinx.coroutines.launch

// ==========================================
// MEDIA REMOTE SCREEN
// ==========================================
@Composable
fun MediaRemoteScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                Toast.makeText(context, "Beaming to TV: \"$recognizedText\"", Toast.LENGTH_SHORT).show()
                viewModel.sendText(recognizedText)
                viewModel.sendKeyboardKey(0, 0x28.toByte()) // Send ENTER
            }
        }
    }

    fun triggerTvVoiceSearch() {
        viewModel.vibrate(40)
        // Send Assistant / Search command to TV
        viewModel.sendMediaAction(0x80.toByte())
        // Launch Speech Recognizer on phone
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak for Google TV Search...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerScreenMirroring() {
        viewModel.vibrate(40)
        // 1. Send Win + K (Wireless Display / Connect shortcut) over Bluetooth HID to PC/TV
        viewModel.sendKeyboardKey(0x08, 0x0E.toByte()) // Left GUI + K

        // 2. Open Android native Cast / Screen Mirroring connection settings
        val castIntent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
        try {
            context.startActivity(castIntent)
        } catch (_: Exception) {
            try {
                val wifiDisplayIntent = android.content.Intent("android.settings.WIFI_DISPLAY_SETTINGS")
                context.startActivity(wifiDisplayIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Opening Wireless Display settings...", Toast.LENGTH_SHORT).show()
                val displayIntent = android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                try {
                    context.startActivity(displayIntent)
                } catch (_: Exception) {}
            }
        }
    }

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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Quick Action Grid (Power, Home, Voice TV, Screen Mirror)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Power & Home
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // System Power Key
                    Button(
                        onClick = { viewModel.sendMediaAction(0x40) }, // Bit 6 (Power)
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("media_power"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Power", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Home Key
                    Button(
                        onClick = { viewModel.sendMediaAction(0x80.toByte()) }, // Bit 7 (Menu / Home)
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("media_home"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Home", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Row 2: Voice TV & Screen Mirror (Cast)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google TV Voice Search Button
                    Button(
                        onClick = { triggerTvVoiceSearch() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("media_voice_assistant"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Assistant", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voice TV", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Screen Mirroring / Cast Button
                    Button(
                        onClick = { triggerScreenMirroring() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("media_screen_mirror"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.Cast, contentDescription = "Screen Mirror", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mirror TV", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
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

            // 3. Navigation Shortcuts Row: Back, App Switch & Enter/Select
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Escape key
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x29.toByte()) }, // Escape / Back
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("media_back_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // App Switcher / Task View key (Win + Tab)
                Button(
                    onClick = {
                        viewModel.vibrate(30)
                        viewModel.sendKeyboardKey(8, 0x2B.toByte()) // Win + Tab
                        Toast.makeText(context, "Task View (Win + Tab)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(50.dp)
                        .testTag("media_app_switch_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.ViewArray, contentDescription = "App Switch", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apps", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Direct Select / Enter key
                Button(
                    onClick = { viewModel.sendKeyboardKey(0, 0x28.toByte()) }, // Enter / Select
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("media_enter_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Enter", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enter", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
