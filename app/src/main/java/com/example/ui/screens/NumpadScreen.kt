package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumpadScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var displayText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Numpad", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = displayText.ifEmpty { "0" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Numpad Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: 7 8 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumpadButton("7", Modifier.weight(1f)) { 
                        displayText += "7"
                        viewModel.sendKeyboardKey(0, 0x24.toByte()) // 7 key
                    }
                    NumpadButton("8", Modifier.weight(1f)) { 
                        displayText += "8"
                        viewModel.sendKeyboardKey(0, 0x25.toByte()) // 8 key
                    }
                    NumpadButton("9", Modifier.weight(1f)) { 
                        displayText += "9"
                        viewModel.sendKeyboardKey(0, 0x26.toByte()) // 9 key
                    }
                }

                // Row 2: 4 5 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumpadButton("4", Modifier.weight(1f)) { 
                        displayText += "4"
                        viewModel.sendKeyboardKey(0, 0x21.toByte()) // 4 key
                    }
                    NumpadButton("5", Modifier.weight(1f)) { 
                        displayText += "5"
                        viewModel.sendKeyboardKey(0, 0x22.toByte()) // 5 key
                    }
                    NumpadButton("6", Modifier.weight(1f)) { 
                        displayText += "6"
                        viewModel.sendKeyboardKey(0, 0x23.toByte()) // 6 key
                    }
                }

                // Row 3: 1 2 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumpadButton("1", Modifier.weight(1f)) { 
                        displayText += "1"
                        viewModel.sendKeyboardKey(0, 0x1E.toByte()) // 1 key
                    }
                    NumpadButton("2", Modifier.weight(1f)) { 
                        displayText += "2"
                        viewModel.sendKeyboardKey(0, 0x1F.toByte()) // 2 key
                    }
                    NumpadButton("3", Modifier.weight(1f)) { 
                        displayText += "3"
                        viewModel.sendKeyboardKey(0, 0x20.toByte()) // 3 key
                    }
                }

                // Row 4: 0 . Enter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumpadButton("0", Modifier.weight(1f)) { 
                        displayText += "0"
                        viewModel.sendKeyboardKey(0, 0x27.toByte()) // 0 key
                    }
                    NumpadButton(".", Modifier.weight(1f)) { 
                        displayText += "."
                        viewModel.sendKeyboardKey(0, 0x63.toByte()) // Period key
                    }
                    NumpadButton("Enter", Modifier.weight(1f), isSpecial = true) { 
                        displayText = ""
                        viewModel.sendKeyboardKey(0, 0x28.toByte()) // Enter key
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Operator buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumpadButton("+", Modifier.weight(1f), isOperator = true) { 
                    displayText += "+"
                    viewModel.sendKeyboardKey(0, 0x57.toByte()) // + key
                }
                NumpadButton("-", Modifier.weight(1f), isOperator = true) { 
                    displayText += "-"
                    viewModel.sendKeyboardKey(0, 0x56.toByte()) // - key
                }
                NumpadButton("*", Modifier.weight(1f), isOperator = true) { 
                    displayText += "*"
                    viewModel.sendKeyboardKey(0, 0x55.toByte()) // * key
                }
                NumpadButton("/", Modifier.weight(1f), isOperator = true) { 
                    displayText += "/"
                    viewModel.sendKeyboardKey(0, 0x54.toByte()) // / key
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Clear and Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { displayText = "" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear")
                }
                OutlinedButton(
                    onClick = { 
                        if (displayText.isNotEmpty()) {
                            displayText = displayText.dropLast(1)
                            viewModel.sendKeyboardKey(0, 0x2A.toByte()) // Backspace
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun NumpadButton(
    text: String,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    isOperator: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSpecial -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isSpecial -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
