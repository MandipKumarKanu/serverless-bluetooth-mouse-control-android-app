package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(navController: NavController, viewModel: AirMouseViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Gamepad Controller", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Shoulder buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // L1 Button
                GamepadButton(
                    label = "L1",
                    modifier = Modifier.weight(1f).height(50.dp),
                    onClick = { viewModel.sendKeyboardKey(0x02, 0x1D.toByte()) } // Shift + Z
                )
                Spacer(modifier = Modifier.weight(1f))
                // R1 Button
                GamepadButton(
                    label = "R1",
                    modifier = Modifier.weight(1f).height(50.dp),
                    onClick = { viewModel.sendKeyboardKey(0x02, 0x1B.toByte()) } // Shift + X
                )
            }

            // Middle section: D-Pad and Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-Pad (Left side)
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
                        onUp = { viewModel.sendKeyboardKey(0, 0x52.toByte()) },    // Up Arrow
                        onDown = { viewModel.sendKeyboardKey(0, 0x51.toByte()) },  // Down Arrow
                        onLeft = { viewModel.sendKeyboardKey(0, 0x50.toByte()) },  // Left Arrow
                        onRight = { viewModel.sendKeyboardKey(0, 0x4F.toByte()) }, // Right Arrow
                        onCenter = { viewModel.sendKeyboardKey(0, 0x28.toByte()) } // Enter
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
                        onTop = { viewModel.sendKeyboardKey(0, 0x1A.toByte()) },    // Y -> W
                        onBottom = { viewModel.sendKeyboardKey(0, 0x07.toByte()) }, // A -> G
                        onLeft = { viewModel.sendKeyboardKey(0, 0x04.toByte()) },   // X -> D
                        onRight = { viewModel.sendKeyboardKey(0, 0x0D.toByte()) }   // B -> J
                    )
                }
            }

            // Bottom section: Start/Select and Home
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select Button
                GamepadButton(
                    label = "SELECT",
                    modifier = Modifier.weight(0.8f).height(45.dp),
                    onClick = { viewModel.sendKeyboardKey(0x04, 0x16.toByte()) } // Alt + S
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Home Button
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
                // Start Button
                GamepadButton(
                    label = "START",
                    modifier = Modifier.weight(0.8f).height(45.dp),
                    onClick = { viewModel.sendKeyboardKey(0, 0x28.toByte()) } // Enter
                )
            }

            // Hint text
            Text(
                text = "D-Pad: Arrow Keys | A: G | B: J | X: D | Y: W",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit
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
                .clickable { onUp() },
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
                .clickable { onDown() },
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
                .clickable { onLeft() },
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
                .clickable { onRight() },
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
                .clickable { onCenter() },
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
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Y (Top)
        GamepadActionButton(
            label = "Y",
            color = Color(0xFFF59E0B),
            modifier = Modifier.align(Alignment.TopCenter),
            onClick = onTop
        )

        // A (Bottom)
        GamepadActionButton(
            label = "A",
            color = Color(0xFF10B981),
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = onBottom
        )

        // X (Left)
        GamepadActionButton(
            label = "X",
            color = Color(0xFF3B82F6),
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onLeft
        )

        // B (Right)
        GamepadActionButton(
            label = "B",
            color = Color(0xFFEF4444),
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = onRight
        )
    }
}

@Composable
fun GamepadActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() },
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
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { onClick() },
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
