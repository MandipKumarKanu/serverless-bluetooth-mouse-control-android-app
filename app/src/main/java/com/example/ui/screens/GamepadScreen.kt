package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.ui.AdaptiveScreenBody
import com.example.ui.theme.GamepadA
import com.example.ui.theme.GamepadB
import com.example.ui.theme.GamepadX
import com.example.ui.theme.GamepadY

/**
 * Press-and-hold modifier: invokes [onPress](true) on finger-down and
 * [onPress](false) on release, giving gamepad buttons real press-hold
 * semantics instead of tap-only. [rememberUpdatedState] keeps the callback
 * fresh across recompositions.
 */
@Composable
private fun Modifier.pressHold(onPress: (Boolean) -> Unit): Modifier = composed {
    val currentOnPress by rememberUpdatedState(onPress)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            currentOnPress(true)
            waitForUpOrCancellation()
            currentOnPress(false)
        }
    }
}

// HID gamepad button bits (report ID 4, button 1 = bit 0)
private const val BTN_A = 0x01
private const val BTN_B = 0x02
private const val BTN_X = 0x04
private const val BTN_Y = 0x08
private const val BTN_L1 = 0x10
private const val BTN_R1 = 0x20
private const val BTN_SELECT = 0x40
private const val BTN_START = 0x80
private const val BTN_DPAD_CENTER = 0x100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var gamepadMode by remember { mutableStateOf(false) }
    var showModeHelp by remember { mutableStateOf(false) }

    // Host force-feedback (rumble) intensity, 0 when motors are off
    val rumble by viewModel.gamepadRumbleState.collectAsState()

    // Held D-pad directions; used to compute the HID hat switch (supports diagonals)
    var dpadUp by remember { mutableStateOf(false) }
    var dpadDown by remember { mutableStateOf(false) }
    var dpadLeft by remember { mutableStateOf(false) }
    var dpadRight by remember { mutableStateOf(false) }

    fun releaseKeyboard() {
        viewModel.hidManager.sendKeyboardInput(0, byteArrayOf(0))
    }

    fun hatFor(): Byte {
        return when {
            dpadUp && dpadLeft -> 7
            dpadUp && dpadRight -> 1
            dpadDown && dpadLeft -> 5
            dpadDown && dpadRight -> 3
            dpadUp -> 0
            dpadRight -> 2
            dpadDown -> 4
            dpadLeft -> 6
            else -> 8
        }
    }

    fun dpadPress(direction: String, down: Boolean) {
        when (direction) {
            "up" -> dpadUp = down
            "down" -> dpadDown = down
            "left" -> dpadLeft = down
            "right" -> dpadRight = down
        }
        if (gamepadMode) {
            viewModel.gamepadHat(hatFor())
        } else {
            val key = when (direction) {
                "up" -> 0x52
                "down" -> 0x51
                "left" -> 0x50
                else -> 0x4F
            }
            if (down) viewModel.sendKeyboardKey(0, key.toByte()) else releaseKeyboard()
        }
    }

    fun buttonPress(buttonBit: Int, modifiers: Int, keyCode: Int, down: Boolean) {
        if (gamepadMode) {
            viewModel.gamepadButton(buttonBit, down)
        } else {
            if (down) viewModel.sendKeyboardKey(modifiers.toByte(), keyCode.toByte()) else releaseKeyboard()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Gamepad Controller", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        // "?" help explaining the two input modes
                        IconButton(onClick = { showModeHelp = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "Mode help",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        AdaptiveScreenBody(
            modifier = Modifier.padding(innerPadding),
            horizontalPadding = 16.dp,
            scrollable = true,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Breathing room between the connection bar and the mode selector
            Spacer(modifier = Modifier.height(8.dp))

            // Mode selector: keyboard emulation (universal) vs real HID gamepad
            // (recognized by DirectInput games and emulators).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GamepadModeChip("Keyboard Mode", !gamepadMode) {
                    viewModel.vibrate(20)
                    gamepadMode = false
                }
                GamepadModeChip("Gamepad Mode", gamepadMode) {
                    viewModel.vibrate(20)
                    gamepadMode = true
                }
            }

            // Rumble indicator: lights up while the host game sends
            // force-feedback output reports (mirrored as phone vibration).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (rumble > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Rumble",
                            tint = if (rumble > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (rumble > 0) "RUMBLE ACTIVE" else "RUMBLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rumble > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Top section: Shoulder buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GamepadButton(
                    label = "L1",
                    modifier = Modifier.weight(1f).height(50.dp),
                    onPress = { down -> buttonPress(BTN_L1, 0x02, 0x1D, down) } // Shift + Z in keyboard mode
                )
                Spacer(modifier = Modifier.weight(1f))
                GamepadButton(
                    label = "R1",
                    modifier = Modifier.weight(1f).height(50.dp),
                    onPress = { down -> buttonPress(BTN_R1, 0x02, 0x1B, down) } // Shift + X in keyboard mode
                )
            }

            // Middle section: D-Pad and Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "D-PAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DPad(
                        onUp = { down -> dpadPress("up", down) },
                        onDown = { down -> dpadPress("down", down) },
                        onLeft = { down -> dpadPress("left", down) },
                        onRight = { down -> dpadPress("right", down) },
                        onCenter = { down -> buttonPress(BTN_DPAD_CENTER, 0, 0x28, down) } // Enter in keyboard mode
                    )
                }

                // Action Buttons (Right side) - Diamond layout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACTIONS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButtons(
                        onTop = { down -> buttonPress(BTN_Y, 0, 0x1A, down) },    // Y -> W
                        onBottom = { down -> buttonPress(BTN_A, 0, 0x07, down) }, // A -> G
                        onLeft = { down -> buttonPress(BTN_X, 0, 0x04, down) },   // X -> D
                        onRight = { down -> buttonPress(BTN_B, 0, 0x0D, down) }   // B -> J
                    )
                }
            }

            // Bottom section: Start/Select and Home
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamepadButton(
                    label = "SELECT",
                    modifier = Modifier.weight(0.8f).height(45.dp),
                    onPress = { down -> buttonPress(BTN_SELECT, 0x04, 0x16, down) } // Alt + S in keyboard mode
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Home button (media control in both modes)
                IconButton(
                    onClick = { viewModel.sendMediaAction(0x80.toByte()) }, // Home/Menu
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                GamepadButton(
                    label = "START",
                    modifier = Modifier.weight(0.8f).height(45.dp),
                    onPress = { down -> buttonPress(BTN_START, 0, 0x28, down) } // Enter in keyboard mode
                )
            }

            // Hint text
            Text(
                text = if (gamepadMode) {
                    "HID Gamepad: D-Pad = Hat • A/B/X/Y/L1/R1 = Buttons (DirectInput & emulators)"
                } else {
                    "D-Pad: Arrow Keys | A: G | B: J | X: D | Y: W"
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }

    // "?" mode explanation dialog
    if (showModeHelp) {
        AlertDialog(
            onDismissRequest = { showModeHelp = false },
            title = { Text("Gamepad Modes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Keyboard Mode — every button sends a real keyboard key (D-pad = arrow keys, A = G, B = J, X = D, Y = W). Works on any PC or app, but games see a keyboard, not a controller.",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Gamepad Mode — sends a true HID gamepad report (joystick, hat switch, buttons). Recognized by DirectInput games and emulators; XInput-only titles won't respond.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeHelp = false }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun RowScope.GamepadModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DPad(
    onUp: (Boolean) -> Unit,
    onDown: (Boolean) -> Unit,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit,
    onCenter: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Up
        Box(
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .pressHold(onUp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Down
        Box(
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .pressHold(onDown),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Left
        Box(
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .pressHold(onLeft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Left",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Right
        Box(
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .pressHold(onRight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Right",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Center
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pressHold(onCenter),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@Composable
fun ActionButtons(
    onTop: (Boolean) -> Unit,
    onBottom: (Boolean) -> Unit,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Y (Top)
        GamepadActionButton(
            label = "Y",
            color = GamepadY,
            modifier = Modifier.align(Alignment.TopCenter),
            onPress = onTop
        )

        // A (Bottom)
        GamepadActionButton(
            label = "A",
            color = GamepadA,
            modifier = Modifier.align(Alignment.BottomCenter),
            onPress = onBottom
        )

        // X (Left)
        GamepadActionButton(
            label = "X",
            color = GamepadX,
            modifier = Modifier.align(Alignment.CenterStart),
            onPress = onLeft
        )

        // B (Right)
        GamepadActionButton(
            label = "B",
            color = GamepadB,
            modifier = Modifier.align(Alignment.CenterEnd),
            onPress = onRight
        )
    }
}

@Composable
fun GamepadActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onPress: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color)
            .pressHold(onPress),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun GamepadButton(
    label: String,
    modifier: Modifier = Modifier,
    onPress: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .pressHold(onPress),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
