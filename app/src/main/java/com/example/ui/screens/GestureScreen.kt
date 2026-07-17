package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gesture.GestureActions
import com.example.gesture.GestureEntity
import com.example.gesture.GesturePoint
import com.example.viewmodel.AirMouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var isRecording by remember { mutableStateOf(false) }
    var recordedPoints by remember { mutableStateOf<List<GesturePoint>>(emptyList()) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf("") }
    var gestureName by remember { mutableStateOf("") }

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
                text = "Draw a gesture to assign an action",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Gesture Canvas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isRecording = true
                                    recordedPoints = emptyList()
                                },
                                onDragEnd = {
                                    isRecording = false
                                    if (recordedPoints.size > 10) {
                                        showAssignDialog = true
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (recordedPoints.isNotEmpty()) {
                            val path = Path()
                            path.moveTo(recordedPoints.first().x, recordedPoints.first().y)

                            for (i in 1 until recordedPoints.size) {
                                path.lineTo(recordedPoints[i].x, recordedPoints[i].y)
                            }

                            drawPath(
                                path = path,
                                color = if (isRecording) MaterialTheme.colorScheme.primary else Color(0xFF3B82F6),
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                    }

                    // Placeholder text
                    if (recordedPoints.isEmpty()) {
                        Text(
                            text = "Draw here",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recording indicator
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Clear button
            if (recordedPoints.isNotEmpty() && !isRecording) {
                Button(
                    onClick = {
                        recordedPoints = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Gesture")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick action buttons
            Text(
                text = "Quick Actions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Pre-defined gesture actions
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(getQuickActions()) { action ->
                    Card(
                        modifier = Modifier
                            .height(60.dp)
                            .clickable {
                                // Execute action directly
                                viewModel.executeGestureAction(action.first)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = action.second,
                                contentDescription = action.first,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = action.first,
                                fontSize = 10.sp,
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
            title = { Text("Assign Action") },
            text = {
                Column {
                    OutlinedTextField(
                        value = gestureName,
                        onValueChange = { gestureName = it },
                        label = { Text("Gesture Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select Action:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Action list
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(getAssignableActions()) { action ->
                            Card(
                                modifier = Modifier
                                    .clickable {
                                        selectedAction = action.first
                                        // Save gesture and action
                                        if (gestureName.isNotBlank()) {
                                            viewModel.saveGesture(
                                                GestureEntity(
                                                    name = gestureName,
                                                    points = recordedPoints.toString(),
                                                    actionType = "keyboard",
                                                    actionData = action.first
                                                )
                                            )
                                            recordedPoints = emptyList()
                                            gestureName = ""
                                            showAssignDialog = false
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedAction == action.first)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = action.first,
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 12.sp,
                                    color = if (selectedAction == action.first)
                                        MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAssignDialog = false
                    recordedPoints = emptyList()
                    gestureName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyGridScope.() -> Unit
) {
    // Simplified grid implementation
    LazyColumn(
        modifier = modifier,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

sealed class GridCells {
    data class Fixed(val count: Int) : GridCells()
}

interface LazyGridScope {
    fun item(content: @Composable () -> Unit)
    fun items(count: Int, itemContent: @Composable (Int) -> Unit)
}

fun getQuickActions(): List<Pair<String, ImageVector>> {
    return listOf(
        "Copy" to Icons.Default.ContentCopy,
        "Paste" to Icons.Default.ContentPaste,
        "Undo" to Icons.Default.Undo,
        "Redo" to Icons.Default.Redo,
        "Vol +" to Icons.Default.VolumeUp,
        "Vol -" to Icons.Default.VolumeDown,
        "Play" to Icons.Default.PlayArrow,
        "Next" to Icons.Default.SkipNext,
        "Prev" to Icons.Default.SkipPrevious
    )
}

fun getAssignableActions(): List<Pair<String, ImageVector>> {
    return listOf(
        "Copy (Ctrl+C)" to Icons.Default.ContentCopy,
        "Paste (Ctrl+V)" to Icons.Default.ContentPaste,
        "Undo (Ctrl+Z)" to Icons.Default.Undo,
        "Redo (Ctrl+Y)" to Icons.Default.Redo,
        "Select All" to Icons.Default.SelectAll,
        "Volume Up" to Icons.Default.VolumeUp,
        "Volume Down" to Icons.Default.VolumeDown,
        "Play/Pause" to Icons.Default.PlayArrow,
        "Next Track" to Icons.Default.SkipNext,
        "Previous Track" to Icons.Default.SkipPrevious,
        "Left Click" to Icons.Default.Mouse,
        "Right Click" to Icons.Default.RightClick
    )
}
