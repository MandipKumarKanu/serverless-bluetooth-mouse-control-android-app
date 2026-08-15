@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.ui.AdaptiveListBody

// ==========================================
// CUSTOM SHORTCUTS SCREEN
// ==========================================
@Composable
fun ShortcutsScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val shortcuts by viewModel.shortcutsState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var shortcutName by remember { mutableStateOf("") }
    var ctrlSelected by remember { mutableStateOf(false) }
    var shiftSelected by remember { mutableStateOf(false) }
    var altSelected by remember { mutableStateOf(false) }
    var guiSelected by remember { mutableStateOf(false) }
    var selectedKeyCodeStr by remember { mutableStateOf("6") } // Defaults to C

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Custom Shortcuts", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                    viewModel.vibrate(30)
                                    showAddDialog = true
                                }
                            },
                            modifier = Modifier.testTag("add_shortcut_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Shortcut", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        AdaptiveListBody(modifier = Modifier.padding(innerPadding)) {
            if (shortcuts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Keyboard, contentDescription = "No Shortcuts", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Custom Shortcuts Added", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Press the + icon on top to create customized hotkey macros.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shortcuts) { shortcut ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                        viewModel.triggerCustomShortcut(shortcut)
                                    }
                                }
                                .testTag("shortcut_card_${shortcut.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.SettingsSystemDaydream, contentDescription = "Macro", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(shortcut.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        val modLabel = buildString {
                                            if (shortcut.modifiers and 0x01 != 0) append("Ctrl ")
                                            if (shortcut.modifiers and 0x02 != 0) append("Shift ")
                                            if (shortcut.modifiers and 0x04 != 0) append("Alt ")
                                            if (shortcut.modifiers and 0x08 != 0) append("Win ")
                                        }
                                        Text(
                                            text = "Keys: $modLabel+ HID_CODE ${shortcut.keyCodes}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                            viewModel.vibrate(30)
                                            viewModel.deleteShortcut(shortcut.id)
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_shortcut_${shortcut.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // ADD DIALOG
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("Add Shortcut Macro", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = shortcutName,
                                onValueChange = { shortcutName = it },
                                label = { Text("Shortcut Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_name_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Text("Select Modifiers", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckboxLabel("Ctrl", ctrlSelected) { ctrlSelected = it }
                                CheckboxLabel("Shift", shiftSelected) { shiftSelected = it }
                                CheckboxLabel("Alt", altSelected) { altSelected = it }
                                CheckboxLabel("Win", guiSelected) { guiSelected = it }
                            }

                            OutlinedTextField(
                                value = selectedKeyCodeStr,
                                onValueChange = { selectedKeyCodeStr = it },
                                label = { Text("HID Key ScanCode (Integer, standard C is 6)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_code_field"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (shortcutName.isNotBlank() && selectedKeyCodeStr.isNotBlank()) {
                                    var modifiers = 0
                                    if (ctrlSelected) modifiers = modifiers or 0x01
                                    if (shiftSelected) modifiers = modifiers or 0x02
                                    if (altSelected) modifiers = modifiers or 0x04
                                    if (guiSelected) modifiers = modifiers or 0x08

                                    viewModel.addCustomShortcut(shortcutName, modifiers, selectedKeyCodeStr)

                                    // Reset Dialog variables
                                    shortcutName = ""
                                    ctrlSelected = false
                                    shiftSelected = false
                                    altSelected = false
                                    guiSelected = false
                                    selectedKeyCodeStr = "6"
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier.testTag("dialog_confirm")
                        ) {
                            Text("Add Macro")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CheckboxLabel(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
