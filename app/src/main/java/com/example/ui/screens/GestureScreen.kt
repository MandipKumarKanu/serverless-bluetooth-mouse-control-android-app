package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.GestureEntity
import com.example.gesture.GestureActions
import com.example.gesture.GestureRecognizer
import com.example.gesture.GesturePoint
import com.example.viewmodel.AirMouseViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun GestureScreen(navController: NavController, viewModel: AirMouseViewModel) {
    var isRecording by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) } // Registration mode
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

    // Auto-clear recognition status after 2.5 seconds
    LaunchedEffect(recognitionStatus) {
        if (recognitionStatus != null) {
            delay(2500)
            recognitionStatus = null
        }
    }

    // Animation for pulse outline in Register Mode
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Gesture Workspace",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                StickyConnectionIndicator(viewModel, navController)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // MODE SELECTOR (Directly above the canvas)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SELECT WORKSPACE MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (!isRegistering) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        isRegistering = false
                                        recordedPoints = emptyList()
                                        recognitionStatus = null
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = "Trigger Mode",
                                        tint = if (!isRegistering) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Trigger Mode",
                                        color = if (!isRegistering) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isRegistering) MaterialTheme.colorScheme.secondary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        isRegistering = true
                                        recordedPoints = emptyList()
                                        recognitionStatus = null
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = "Register Mode",
                                        tint = if (isRegistering) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Register Mode",
                                        color = if (isRegistering) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // GESTURE CANVAS AREA
            item {
                val canvasBorderColor = if (isRegistering) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .border(2.dp, canvasBorderColor, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Canvas Header Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRegistering) "📝 DRAW TO SAVE" else "⚡ DRAW TO TRIGGER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isRegistering) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            // Pulsing REC Indicator
                            AnimatedVisibility(
                                visible = isRecording,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Red.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "RECORDING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Canvas Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                                                if (isRegistering) {
                                                    showAssignDialog = true
                                                } else {
                                                    // Recognize gesture
                                                    val templates = savedGestures.associate { gesture ->
                                                        gesture.name to parsePoints(gesture.points)
                                                    }
                                                    val result = gestureRecognizer.recognize(recordedPoints, templates)
                                                    if (result != null) {
                                                        val matchedGesture = savedGestures.find { it.name == result.first }
                                                        if (matchedGesture != null) {
                                                            lastMatchedAction = matchedGesture.actionData
                                                            recognitionStatus = "✓ Matched: ${matchedGesture.name}"
                                                            viewModel.executeGestureAction(matchedGesture.actionData)
                                                        }
                                                    } else {
                                                        lastMatchedAction = null
                                                        recognitionStatus = "No match found. Switched to 'Register Mode' to save it?"
                                                    }
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
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val secondaryColor = MaterialTheme.colorScheme.secondary
                            val onSurfaceColor = MaterialTheme.colorScheme.onSurface

                            // Draw subtle grid guides & the neon gesture path
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // 1. Subtle dotted grid background
                                val dotSpacing = 20.dp.toPx()
                                val dotRadius = 1.2f.dp.toPx()
                                val dotColor = onSurfaceColor.copy(alpha = 0.08f)

                                var x = dotSpacing
                                while (x < size.width) {
                                    var y = dotSpacing
                                    while (y < size.height) {
                                        drawCircle(
                                            color = dotColor,
                                            radius = dotRadius,
                                            center = androidx.compose.ui.geometry.Offset(x, y)
                                        )
                                        y += dotSpacing
                                    }
                                    x += dotSpacing
                                }

                                // 2. Crosshair guide in the center
                                val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                drawCircle(
                                    color = onSurfaceColor.copy(alpha = 0.03f),
                                    radius = 60.dp.toPx(),
                                    center = centerOffset,
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                )

                                // 3. Glowing user-drawn path
                                if (recordedPoints.isNotEmpty()) {
                                    val path = Path()
                                    path.moveTo(recordedPoints.first().x, recordedPoints.first().y)

                                    for (i in 1 until recordedPoints.size) {
                                        path.lineTo(recordedPoints[i].x, recordedPoints[i].y)
                                    }

                                    val finalPathColor = if (isRegistering) secondaryColor else primaryColor

                                    // Outer neon glow
                                    drawPath(
                                        path = path,
                                        color = finalPathColor.copy(alpha = 0.3f),
                                        style = Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Crisp inner trail
                                    drawPath(
                                        path = path,
                                        color = finalPathColor,
                                        style = Stroke(
                                            width = 4.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Interactive prompt/instruction overlay (when empty)
                            if (recordedPoints.isEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRegistering) Icons.Default.Edit else Icons.Default.Gesture,
                                        contentDescription = "Instruction",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isRegistering) "Draw a custom gesture pattern\nto register it" else "Draw gesture path here\nto trigger a command",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Bottom action strip for the canvas
                        AnimatedVisibility(
                            visible = recordedPoints.isNotEmpty() && !isRecording,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { recordedPoints = emptyList() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reset Area", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { showAssignDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRegistering) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRegistering) Icons.Default.SaveAs else Icons.Default.Save,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRegistering) "Register It" else "Save Gesture",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RECOGNITION STATUS & MATCH FEEDBACK
            item {
                AnimatedVisibility(
                    visible = recognitionStatus != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    recognitionStatus?.let { status ->
                        val isMatched = lastMatchedAction != null
                        val containerColor = if (isMatched) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                        val contentColor = if (isMatched) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                        val statusIcon = if (isMatched) Icons.Default.CheckCircle else Icons.Default.HelpOutline

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = containerColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = "Status",
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = status,
                                        color = contentColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isMatched) {
                                        Text(
                                            text = "Command executed successfully",
                                            color = contentColor.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (!isMatched && recordedPoints.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            isRegistering = true
                                            showAssignDialog = true
                                        }
                                    ) {
                                        Text("Register Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // QUICK ACTIONS DASHBOARD
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "QUICK CONTROL TOUCHPADS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
            }

            item {
                val quickActions = getQuickActions()
                val chunkedActions = remember(quickActions) { quickActions.chunked(3) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkedActions.forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowActions.forEach { action ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(68.dp)
                                        .clickable {
                                            viewModel.executeGestureAction(action.second)
                                            recognitionStatus = "✓ Quick Trigger: ${action.first}"
                                            lastMatchedAction = action.second
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = action.third,
                                                contentDescription = action.first,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = action.first,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (rowActions.size < 3) {
                                repeat(3 - rowActions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // SAVED GESTURES (With Live Thumbnails)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED GESTURE SHORTCUTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            "${savedGestures.size} active",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (savedGestures.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = "No gestures",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No saved gestures yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Toggle 'Register Mode' above, draw a gesture shape, and map it to any command.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(savedGestures, key = { it.id }) { gesture ->
                    val miniPoints = remember(gesture.points) { parsePoints(gesture.points) }
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.executeGestureAction(gesture.actionData)
                                recognitionStatus = "✓ Triggered: ${gesture.name}"
                                lastMatchedAction = gesture.actionData
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini Live Gesture Path Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(38.dp)) {
                                    if (miniPoints.isNotEmpty()) {
                                        val minX = miniPoints.minOf { it.x }
                                        val maxX = miniPoints.maxOf { it.x }
                                        val minY = miniPoints.minOf { it.y }
                                        val maxY = miniPoints.maxOf { it.y }

                                        val dx = maxX - minX
                                        val dy = maxY - minY
                                        val pad = 4.dp.toPx()

                                        val usableWidth = size.width - pad * 2
                                        val usableHeight = size.height - pad * 2

                                        val scale = minOf(
                                            if (dx > 0) usableWidth / dx else 1f,
                                            if (dy > 0) usableHeight / dy else 1f
                                        )

                                        val path = Path()
                                        val first = miniPoints.first()
                                        val startX = pad + (first.x - minX) * scale + (usableWidth - dx * scale) / 2
                                        val startY = pad + (first.y - minY) * scale + (usableHeight - dy * scale) / 2
                                        path.moveTo(startX, startY)

                                        for (i in 1 until miniPoints.size) {
                                            val p = miniPoints[i]
                                            val px = pad + (p.x - minX) * scale + (usableWidth - dx * scale) / 2
                                            val py = pad + (p.y - minY) * scale + (usableHeight - dy * scale) / 2
                                            path.lineTo(px, py)
                                        }

                                        drawPath(
                                            path = path,
                                            color = primaryColor,
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    } else {
                                        drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 3.dp.toPx())
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gesture.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = GestureActions.getActionLabel(gesture.actionData),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            // Delete Action
                            IconButton(
                                onClick = { viewModel.deleteGesture(gesture.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Gesture",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ASSIGN ACTION DIALOG
    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SaveAs,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Gesture Shortcut")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Name your recorded gesture pattern and map it to an automated keyboard, mouse, or media command.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = gestureName,
                        onValueChange = { gestureName = it },
                        label = { Text("Gesture Label (e.g., Circle, Tick)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        "CHOOSE AUTOMATION ACTION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    val assignableActions = getAssignableActions()
                    val chunkedAssignable = remember(assignableActions) { assignableActions.chunked(2) }

                    // Scrollable category/action list
                    Box(modifier = Modifier.height(180.dp)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(chunkedAssignable) { rowActions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowActions.forEach { action ->
                                        val isSelected = selectedAction == action.second
                                        val containerCol = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                        val outlineColor = if (isSelected) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            Color.Transparent
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(containerCol)
                                                .border(1.5.dp, outlineColor, RoundedCornerShape(10.dp))
                                                .clickable { selectedAction = action.second }
                                                .padding(horizontal = 10.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.first,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    if (rowActions.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
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
                            isRegistering = false
                            recognitionStatus = "✓ Gesture Registered!"
                        }
                    },
                    enabled = gestureName.isNotBlank() && selectedAction.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Register", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Register")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAssignDialog = false
                        recordedPoints = emptyList()
                        gestureName = ""
                        selectedAction = ""
                    }
                ) {
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

@Suppress("DEPRECATION")
fun getQuickActions(): List<Triple<String, String, ImageVector>> {
    return listOf(
        Triple("Copy", "copy", Icons.Default.ContentCopy),
        Triple("Paste", "paste", Icons.Default.ContentPaste),
        Triple("Undo", "undo", Icons.Default.Undo),
        Triple("Redo", "redo", Icons.Default.Redo),
        Triple("Vol +", "vol_up", Icons.Default.VolumeUp),
        Triple("Vol -", "vol_down", Icons.Default.VolumeDown),
        Triple("Play/Pause", "play_pause", Icons.Default.PlayArrow),
        Triple("Next Track", "next_track", Icons.Default.SkipNext),
        Triple("Prev Track", "prev_track", Icons.Default.SkipPrevious)
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
