@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController
import com.example.gesture.PointerSample
import com.example.gesture.TouchpadAction
import com.example.gesture.TouchpadGestureRecognizer
import com.example.viewmodel.AirMouseViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnected
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ==========================================
// TOUCHPAD SCREEN
// ==========================================
@Composable
fun TouchpadScreen(navController: NavController, viewModel: AirMouseViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    var isRightScrollActive by remember { mutableStateOf(false) }
    var showTouchpadSettings by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Scroll inertia state
    var scrollVelocity by remember { mutableFloatStateOf(0f) }
    var isInertiaScrolling by remember { mutableStateOf(false) }
    var inertiaJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Laptop Touchpad", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.vibrate(30)
                            showTouchpadSettings = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Touchpad Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        if (showTouchpadSettings) {
            var currentSensitivity by remember(settings.sensitivity) { mutableFloatStateOf(settings.sensitivity) }
            var currentScrollSpeed by remember(settings.scrollSpeed) { mutableFloatStateOf(settings.scrollSpeed) }

            AlertDialog(
                onDismissRequest = { showTouchpadSettings = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Touchpad & Scroll Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Touch Pointer Sensitivity Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Touch Pointer Sensitivity",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1fx", currentSensitivity),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = currentSensitivity,
                                onValueChange = { currentSensitivity = it },
                                onValueChangeFinished = {
                                    viewModel.updateSettings(settings.copy(sensitivity = currentSensitivity))
                                },
                                valueRange = 0.2f..3.0f,
                                steps = 27
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Slow (0.2x)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Fast (3.0x)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Scroll Speed Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Scroll Bar Sensitivity",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1fx", currentScrollSpeed),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = currentScrollSpeed,
                                onValueChange = { currentScrollSpeed = it },
                                onValueChangeFinished = {
                                    viewModel.updateSettings(settings.copy(scrollSpeed = currentScrollSpeed))
                                },
                                valueRange = 0.2f..3.0f,
                                steps = 27
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Slow (0.2x)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Fast (3.0x)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateSettings(settings.copy(sensitivity = currentSensitivity, scrollSpeed = currentScrollSpeed))
                            showTouchpadSettings = false
                        }
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            // Touchpad Instruction Alert
            Text(
                text = "Tap: Click • Hold: Drag • 2-Finger: Scroll / Right-Click • Pinch: Zoom • 3-Finger: Swipe / Drag",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            val context = LocalContext.current

            // Touch Area Box Row
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Main Touchpad Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .testTag("touchpad_area")
                        .pointerInput(Unit) {
                            // Gesture recognition is delegated to a dedicated,
                            // unit-tested state machine (TouchpadGestureRecognizer)
                            val recognizer = TouchpadGestureRecognizer()
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val samples = event.changes.map {
                                        PointerSample(
                                            id = it.id.value,
                                            x = it.position.x,
                                            y = it.position.y,
                                            down = it.pressed
                                        )
                                    }
                                    val actions = recognizer.processFrame(samples, System.currentTimeMillis())

                                    if (actions.isNotEmpty()) {
                                        event.changes.forEach { it.consume() }
                                    }

                                    actions.forEach { action ->
                                        when (action) {
                                            is TouchpadAction.Move -> {
                                                // Pointer movement. While a drag is active the held
                                                // button (from DragStart) is applied automatically.
                                                viewModel.sendTouchMove(action.dx, action.dy)
                                            }
                                            is TouchpadAction.Scroll -> {
                                                if (action.yTicks != 0) {
                                                    viewModel.sendScrollTicks(action.yTicks)
                                                    viewModel.vibrate(10)
                                                }
                                                if (action.xTicks != 0) {
                                                    viewModel.sendHScrollTicks(action.xTicks)
                                                    viewModel.vibrate(10)
                                                }
                                            }
                                            is TouchpadAction.Zoom -> {
                                                // Ctrl + wheel = pinch-to-zoom
                                                viewModel.sendCtrlScroll(action.ticks.toByte())
                                                viewModel.vibrate(15)
                                            }
                                            is TouchpadAction.Tap -> {
                                                if (action.button == 2) {
                                                    // Two-finger tap -> right click
                                                    viewModel.vibrate(40)
                                                    Toast.makeText(context, "Right Click", Toast.LENGTH_SHORT).show()
                                                }
                                                viewModel.sendMouseClick(action.button.toByte())
                                            }
                                            is TouchpadAction.DoubleTap -> {
                                                // First tap already sent one click; send the second one.
                                                viewModel.sendMouseClick(1)
                                            }
                                            is TouchpadAction.DragStart -> {
                                                // Long-press or three-finger drag: hold the button down.
                                                viewModel.sendMouseDown(action.button.toByte())
                                                Toast.makeText(context, "Dragging - lift to release", Toast.LENGTH_SHORT).show()
                                            }
                                            is TouchpadAction.DragEnd -> {
                                                viewModel.sendMouseUp()
                                            }
                                            is TouchpadAction.Swipe -> {
                                                // Three-finger swipe -> task view (Win + Tab)
                                                viewModel.vibrate(50)
                                                viewModel.sendKeyboardKey(8, 0x2B.toByte())
                                                Toast.makeText(context, "Task View (Win + Tab)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Central subtle grid decorative visualizer
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeColor = Color(0x0AFFFFFF)
                        val step = 40.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(strokeColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(strokeColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }
                    Text("Touchpad Canvas", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Scroll Bar Area on the right (with inertia)
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .testTag("touchpad_scroll_bar")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isRightScrollActive = true
                                    inertiaJob?.cancel()
                                    isInertiaScrolling = false
                                },
                                onDragEnd = {
                                    isRightScrollActive = false
                                    // Apply inertia based on final velocity
                                    if (kotlin.math.abs(scrollVelocity) > 0.5f) {
                                        isInertiaScrolling = true
                                        inertiaJob = coroutineScope.launch {
                                            var velocity = scrollVelocity
                                            while (kotlin.math.abs(velocity) > 0.1f) {
                                                val rawTick = if (velocity > 0) 1f else -1f
                                                val scaledTick = rawTick * settings.scrollSpeed
                                                val finalTick = if (scaledTick > 0) {
                                                    maxOf(1, scaledTick.toInt())
                                                } else {
                                                    minOf(-1, scaledTick.toInt())
                                                }.toByte()
                                                viewModel.hidManager.sendMouseInput(0, 0, 0, finalTick)
                                                delay(30)
                                                velocity *= 0.9f // Deceleration factor
                                            }
                                            isInertiaScrolling = false
                                        }
                                    }
                                    scrollVelocity = 0f
                                },
                                onDragCancel = {
                                    isRightScrollActive = false
                                    scrollVelocity = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Track velocity for inertia
                                    scrollVelocity = dragAmount.y
                                    // Send scroll relative ticks
                                    val rawTick = if (dragAmount.y > 0) -1f else 1f
                                    val scaledTick = rawTick * settings.scrollSpeed
                                    val finalTick = if (scaledTick > 0) {
                                        maxOf(1, scaledTick.toInt())
                                    } else {
                                        minOf(-1, scaledTick.toInt())
                                    }.toByte()
                                    viewModel.hidManager.sendMouseInput(0, 0, 0, finalTick)
                                    viewModel.vibrate(10)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Scroll Up", tint = MaterialTheme.colorScheme.onSurface)
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Scroll Indicators",
                            tint = if (isRightScrollActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Physical Mouse Buttons (Left Click, Middle/Scroll toggle, Right Click)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Click Card
                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(1) }
                        .testTag("mouse_left_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Left Click", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Middle Scroll Click Card
                Card(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(4) }
                        .testTag("mouse_middle_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = "Middle Click", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Right Click Card
                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clickable { viewModel.sendMouseClick(2) }
                        .testTag("mouse_right_click"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Right Click", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Drag & Drop Mode
            var isDragMode by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Drag Mode Toggle
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clickable {
                            isDragMode = !isDragMode
                            viewModel.vibrate(20)
                            if (isDragMode) {
                                viewModel.sendMouseDown(1) // Press left button
                            } else {
                                viewModel.sendMouseUp() // Release
                            }
                        }
                        .testTag("drag_mode_toggle"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragMode) StatusConnected else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, if (isDragMode) StatusConnected else MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag Mode",
                            tint = if (isDragMode) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDragMode) "DRAGGING" else "Drag & Drop",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDragMode) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
