package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.GestureEntity
import com.example.gesture.GestureRecognizer
import com.example.gesture.GesturePoint
import com.example.viewmodel.AirMouseViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var isRecording by remember { mutableStateOf(false) }
    var recordedPoints by remember { mutableStateOf<List<GesturePoint>>(emptyList()) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf("") }
    var gestureName by remember { mutableStateOf("") }

    // Gesture recognition state
    var recognitionStatus by remember { mutableStateOf<String?>(null) }
    var lastMatchedAction by remember { mutableStateOf<String?>(null) }
    val gestureRecognizer = remember { GestureRecognizer() }

    // Load saved gestures from database
    val savedGestures by viewModel.gesturesState.collectAsState()

    // Auto-clear recognition status after 2 seconds
    LaunchedEffect(recognitionStatus) {
        if (recognitionStatus != null) {
            delay(2000)
            recognitionStatus = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Gesture Mode", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            // Instructions
            Text(
                text = "Draw a gesture → It triggers if matched, or save as new",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Recognition status feedback
            recognitionStatus?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastMatchedAction != null)
                            Color(0xFF064E3B)
                        else
                            Color(0xFF451A03)
                    )
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Gesture Canvas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isRecording = true
                                    recordedPoints = emptyList()
                                    recognitionStatus = null
                                },
                                onDragEnd = {
                                    isRecording = false
                                    if (recordedPoints.size > 10) {
                                        // Try to recognize the gesture
                                        val templates = savedGestures.associate { gesture ->
                                            gesture.name to parsePoints(gesture.points)
                                        }

                                        val result = gestureRecognizer.recognize(recordedPoints, templates)

                                        if (result != null) {
                                            // Found a match - trigger the action
                                            val matchedGesture = savedGestures.find { it.name == result.first }
                                            if (matchedGesture != null) {
                                                lastMatchedAction = matchedGesture.actionData
                                                recognitionStatus = "✓ Matched: ${matchedGesture.name}"
                                                viewModel.executeGestureAction(matchedGesture.actionData)
                                            }
                                        } else {
                                            // No match - show assign dialog
                                            lastMatchedAction = null
                                            showAssignDialog = true
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isRecording = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    recordedPoints = recordedPoints + GesturePoint(
                                        x = change.position.x,
                                        y = change.position.y
                                    )
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Draw the gesture path
                    val pathColor = if (isRecording) MaterialTheme.colorScheme.primary else Color(0xFF3B82F6)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (recordedPoints.isNotEmpty()) {
                            val path = Path()
                            path.moveTo(recordedPoints.first().x, recordedPoints.first().y)

                            for (i in 1 until recordedPoints.size) {
                                path.lineTo(recordedPoints[i].x, recordedPoints[i].y)
                            }

                            drawPath(
                                path = path,
                                color = pathColor,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                    }

                    // Placeholder text
                    if (recordedPoints.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = "Draw",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Draw gesture here",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recording indicator
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording... (${recordedPoints.size} points)",
                        fontSize = 12.sp,
                        color = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear button
                if (recordedPoints.isNotEmpty() && !isRecording) {
                    OutlinedButton(
                        onClick = { recordedPoints = emptyList() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear")
                    }

                    // Save as new gesture button
                    Button(
                        onClick = { showAssignDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Gesture")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Saved Gestures Section
            if (savedGestures.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Gestures (${savedGestures.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // List of saved gestures
                savedGestures.forEach { gesture ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                // Execute the gesture action when tapped
                                viewModel.executeGestureAction(gesture.actionData)
                                recognitionStatus = "✓ Executed: ${gesture.name}"
                                lastMatchedAction = gesture.actionData
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = "Gesture",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gesture.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = GestureActions.getActionLabel(gesture.actionData),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteGesture(gesture.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // No saved gestures
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = "No gestures",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No saved gestures yet",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Draw a gesture and tap 'Save Gesture' to create one",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick action buttons
            Text(
                text = "Quick Actions (Tap to Execute)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(getQuickActions()) { action ->
                    Card(
                        modifier = Modifier
                            .height(56.dp)
                            .clickable {
                                viewModel.executeGestureAction(action.second)
                                recognitionStatus = "✓ ${action.first}"
                                lastMatchedAction = action.second
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = action.third,
                                contentDescription = action.first,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = action.first,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Assign Action Dialog
    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Save Gesture") },
            text = {
                Column {
                    Text(
                        text = "Name your gesture and select an action:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = gestureName,
                        onValueChange = { gestureName = it },
                        label = { Text("Gesture Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Select Action:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(180.dp)
                    ) {
                        items(getAssignableActions()) { action ->
                            Card(
                                modifier = Modifier
                                    .clickable {
                                        selectedAction = action.second
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedAction == action.second)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = action.first,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 11.sp,
                                    color = if (selectedAction == action.second)
                                        MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (gestureName.isNotBlank() && selectedAction.isNotBlank()) {
                            viewModel.saveGesture(
                                GestureEntity(
                                    name = gestureName,
                                    points = recordedPoints.toString(),
                                    actionType = "keyboard",
                                    actionData = selectedAction
                                )
                            )
                            recordedPoints = emptyList()
                            gestureName = ""
                            selectedAction = ""
                            showAssignDialog = false
                            recognitionStatus = "✓ Gesture saved!"
                        }
                    },
                    enabled = gestureName.isNotBlank() && selectedAction.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAssignDialog = false
                    recordedPoints = emptyList()
                    gestureName = ""
                    selectedAction = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Parse points string back to GesturePoint list
 */
fun parsePoints(pointsStr: String): List<GesturePoint> {
    return try {
        val cleaned = pointsStr.removePrefix("[").removeSuffix("]")
        if (cleaned.isBlank()) return emptyList()

        cleaned.split("), ").map { pointStr ->
            val coords = pointStr.removePrefix("GesturePoint(x=").removeSuffix(")")
                .split(", y=")
            GesturePoint(
                x = coords[0].toFloatOrNull() ?: 0f,
                y = coords[1].split(", timestamp=")[0].toFloatOrNull() ?: 0f
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun getQuickActions(): List<Triple<String, String, ImageVector>> {
    return listOf(
        Triple("Copy", "copy", Icons.Default.ContentCopy),
        Triple("Paste", "paste", Icons.Default.ContentPaste),
        Triple("Undo", "undo", Icons.Default.Undo),
        Triple("Redo", "redo", Icons.Default.Redo),
        Triple("Vol +", "vol_up", Icons.Default.VolumeUp),
        Triple("Vol -", "vol_down", Icons.Default.VolumeDown),
        Triple("Play", "play_pause", Icons.Default.PlayArrow),
        Triple("Next", "next_track", Icons.Default.SkipNext),
        Triple("Prev", "prev_track", Icons.Default.SkipPrevious)
    )
}

fun getAssignableActions(): List<Pair<String, String>> {
    return listOf(
        "Copy (Ctrl+C)" to "copy",
        "Paste (Ctrl+V)" to "paste",
        "Undo (Ctrl+Z)" to "undo",
        "Redo (Ctrl+Y)" to "redo",
        "Select All" to "select_all",
        "Volume Up" to "vol_up",
        "Volume Down" to "vol_down",
        "Play/Pause" to "play_pause",
        "Next Track" to "next_track",
        "Previous Track" to "prev_track",
        "Left Click" to "left_click",
        "Right Click" to "right_click"
    )
}
