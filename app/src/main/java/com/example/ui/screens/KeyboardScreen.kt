@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController
import com.example.viewmodel.AirMouseViewModel
import com.example.bluetooth.HidKeyMapper
import com.example.ui.AdaptiveScreenBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==========================================
// KEYBOARD SCREEN
// ==========================================
@Composable
fun KeyboardScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }

    // Toggle modifier button states
    var ctrlPressed by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var altPressed by remember { mutableStateOf(false) }
    var winPressed by remember { mutableStateOf(false) }

    // Lock states mirrored from the connected PC (HID LED output reports)
    val capsLock by viewModel.capsLockState.collectAsState()
    val numLock by viewModel.numLockState.collectAsState()
    val scrollLock by viewModel.scrollLockState.collectAsState()

    // Optimistic lock display: tapping a lock chip flips it immediately and
    // sends the lock key; the override is cleared as soon as the host reports
    // its real LED state back (so the chip always ends up matching the PC).
    var capsOverride by remember { mutableStateOf<Boolean?>(null) }
    var numOverride by remember { mutableStateOf<Boolean?>(null) }
    var scrollOverride by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        viewModel.hidManager.hostLeds.collect {
            capsOverride = null
            numOverride = null
            scrollOverride = null
        }
    }

    fun getModifierByte(): Byte {
        var mask = 0
        if (ctrlPressed) mask = mask or 0x01
        if (shiftPressed) mask = mask or 0x02
        if (altPressed) mask = mask or 0x04
        if (winPressed) mask = mask or 0x08
        return mask.toByte()
    }

    // Maps a standard ASCII char and transmits over HID
    fun transmitCharacter(char: Char) {
        val mapped = HidKeyMapper.map(char) ?: return
        // Merge any held modifier toggles (Ctrl/Shift/Alt/Win) with the
        // character's own required modifier (e.g. Shift for uppercase)
        val modifier = (getModifierByte().toInt() or mapped.first.toInt()).toByte()
        viewModel.hidManager.sendKeyPress(modifier, mapped.second)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Keyboard Input", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
        AdaptiveScreenBody(
            modifier = Modifier.padding(innerPadding),
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp,
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Input Box for full string transmissions
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type Text to Transmit", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keyboard_text_field"),
                        placeholder = { Text("Enter sentence here...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val context = LocalContext.current
                    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

                    fun beamClipboardText() {
                        val clipData = clipboardManager.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val copiedText = clipData.getItemAt(0).text?.toString()
                            if (!copiedText.isNullOrEmpty()) {
                                viewModel.vibrate(40)
                                Toast.makeText(context, "Beaming Clipboard: \"${copiedText.take(20)}...\"", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    copiedText.forEach { char ->
                                        transmitCharacter(char)
                                        delay(15)
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.vibrate(30)
                                coroutineScope.launch {
                                    val text = textInput
                                    textInput = ""
                                    text.forEach { char ->
                                        transmitCharacter(char)
                                        delay(15) // small latency gap between characters
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("send_text_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Text", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Icon-only beam-clipboard button (sends the phone's
                        // copied text to the host, char by char, over HID)
                        Button(
                            onClick = { beamClipboardText() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("beam_clipboard_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Beam Clipboard",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Keyboard Modifier Switches Row (Mechanical style toggles)
            Text("Modifiers (Toggles)", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modifiers = listOf(
                    ModifierTile("CTRL", ctrlPressed) { ctrlPressed = !ctrlPressed },
                    ModifierTile("SHIFT", shiftPressed) { shiftPressed = !shiftPressed },
                    ModifierTile("ALT", altPressed) { altPressed = !altPressed },
                    ModifierTile("WIN", winPressed) { winPressed = !winPressed }
                )
                modifiers.forEach { mod ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable {
                                viewModel.vibrate(20)
                                mod.onClick()
                            }
                            .testTag("modifier_${mod.label}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (mod.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, if (mod.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = mod.label,
                                    color = if (mod.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (mod.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // Host lock indicators — tap to toggle the lock on the PC (the
            // chip then mirrors the host's reported state back via HID LEDs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LockIndicator("CAPS", capsOverride ?: capsLock, Modifier.weight(1f)) {
                    capsOverride = !(capsOverride ?: capsLock)
                    viewModel.sendKeyboardKey(0, 0x39.toByte()) // Caps Lock
                }
                LockIndicator("NUM", numOverride ?: numLock, Modifier.weight(1f)) {
                    numOverride = !(numOverride ?: numLock)
                    viewModel.sendKeyboardKey(0, 0x53.toByte()) // Num Lock
                }
                LockIndicator("SCROLL", scrollOverride ?: scrollLock, Modifier.weight(1f)) {
                    scrollOverride = !(scrollOverride ?: scrollLock)
                    viewModel.sendKeyboardKey(0, 0x47.toByte()) // Scroll Lock
                }
            }

            // Modern Virtual QWERTY Keyboard
            Text(
                text = "Interactive Virtual Keyboard",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Number Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val numRow = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
                        numRow.forEach { num ->
                            KeycapButton(
                                char = num,
                                isUppercase = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(num)
                                }
                            )
                        }
                    }

                    // Row 1 (QWERTY)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
                        row1.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed || capsLock,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }
                    }

                    // Row 2 (ASDF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
                        row2.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed || capsLock,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.weight(0.2f))
                    }

                    // Row 3 (ZXCV + Shift + Backspace)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Shift Key
                        Card(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    shiftPressed = !shiftPressed
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (shiftPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Shift",
                                    tint = if (shiftPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
                        row3.forEach { char ->
                            KeycapButton(
                                char = char,
                                isUppercase = shiftPressed || capsLock,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.vibrate(15)
                                    transmitCharacter(char)
                                }
                            )
                        }

                        // Backspace Key
                        Card(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2A.toByte()) // Backspace scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Row 4 (Tab + Space + Enter)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab key
                        Card(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2B.toByte()) // Tab scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("TAB", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Space key
                        Card(
                            modifier = Modifier
                                .weight(5f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x2C.toByte()) // Space scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("SPACE", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Enter key
                        Card(
                            modifier = Modifier
                                .weight(2f)
                                .height(38.dp)
                                .clickable {
                                    viewModel.vibrate(20)
                                    viewModel.sendKeyboardKey(getModifierByte(), 0x28.toByte()) // Enter scan code
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("ENTER", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Arrow Keys and D-Pad Controls
            Text("Navigation & Utilities", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad Cross Keypad (Left side)
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("D-PAD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        // Row 1: Up Arrow
                        Row {
                            Spacer(modifier = Modifier.size(44.dp))
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x52.toByte())
                                    }
                                    .testTag("key_arrow_up"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.size(44.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row 2: Left, Center (OK), Right
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x50.toByte())
                                    }
                                    .testTag("key_arrow_left"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            // Center space dot
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                            }

                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x4F.toByte())
                                    }
                                    .testTag("key_arrow_right"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row 3: Down Arrow
                        Row {
                            Spacer(modifier = Modifier.size(44.dp))
                            Card(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.vibrate(15)
                                        viewModel.sendKeyboardKey(getModifierByte(), 0x51.toByte())
                                    }
                                    .testTag("key_arrow_down"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.size(44.dp))
                        }
                    }

                    // Vertical separator line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(130.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )

                    // Utility Actions Column (Right side)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SHORTCUTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))

                        val actions = listOf(
                            Pair("Escape (ESC)", 0x29.toByte()),
                            Pair("Backspace", 0x2A.toByte()),
                            Pair("Tab Key", 0x2B.toByte()),
                            Pair("Enter Key", 0x28.toByte())
                        )
                        actions.forEach { (label, scanCode) ->
                            Button(
                                onClick = {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(getModifierByte(), scanCode)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("key_shortcut_${label.lowercase().replace(" ", "_").replace("(", "").replace(")", "")}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Desktop Navigation Utilities
            Text("Desktop Utilities", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                val keysList = listOf(
                    Triple("Home", 0x4A.toByte(), "home"),
                    Triple("End", 0x4D.toByte(), "end"),
                    Triple("Pg Up", 0x4B.toByte(), "page_up"),
                    Triple("Pg Dn", 0x4E.toByte(), "page_down")
                )
                items(keysList) { key ->
                    Button(
                        onClick = {
                            viewModel.vibrate(15)
                            viewModel.sendKeyboardKey(getModifierByte(), key.second)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("key_${key.third}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key.first, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Function Keys (F1-F12)
            Text(
                text = "Function Keys",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // F1-F6 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fRow1 = listOf("F1" to 0x3A, "F2" to 0x3B, "F3" to 0x3C, "F4" to 0x3D, "F5" to 0x3E, "F6" to 0x3F)
                        fRow1.forEach { (label, keyCode) ->
                            Button(
                                onClick = {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(0, keyCode.toByte())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    // F7-F12 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fRow2 = listOf("F7" to 0x40, "F8" to 0x41, "F9" to 0x42, "F10" to 0x43, "F11" to 0x44, "F12" to 0x45)
                        fRow2.forEach { (label, keyCode) ->
                            Button(
                                onClick = {
                                    viewModel.vibrate(15)
                                    viewModel.sendKeyboardKey(0, keyCode.toByte())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun KeycapButton(
    char: Char,
    isUppercase: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val text = if (isUppercase) char.uppercaseChar().toString() else char.toString()
    Card(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class ModifierTile(
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit
)

/**
 * Host lock-state chip (Caps/Num/Scroll): shows the state mirrored from the
 * PC via HID and toggles the lock on the host when tapped.
 */
@Composable
private fun LockIndicator(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
