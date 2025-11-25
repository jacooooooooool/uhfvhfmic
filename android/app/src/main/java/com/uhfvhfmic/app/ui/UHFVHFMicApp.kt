@file:OptIn(ExperimentalMaterial3Api::class)

package com.uhfvhfmic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uhfvhfmic.app.audio.FMModulator
import com.uhfvhfmic.app.sdr.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * UHF/VHF Wireless Mic App UI
 * © 2024 Iwinosa Aigbokhaevbo. All Rights Reserved.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UHFVHFMicApp(
    sdrManager: SDRManager,
    fmModulator: FMModulator,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit
) {
    val connectionStatus by sdrManager.connectionStatus.collectAsState()
    val connectedDevice by sdrManager.connectedDevice.collectAsState()
    val availableDevices by sdrManager.availableDevices.collectAsState()
    val currentFrequencyMHz by sdrManager.currentFrequencyMHz.collectAsState()
    val currentBand by sdrManager.currentBand.collectAsState()
    val txPowerDbm by sdrManager.txPowerDbm.collectAsState()
    val isTransmitting by sdrManager.isTransmitting.collectAsState()
    val sdrError by sdrManager.errorMessage.collectAsState()

    val isCapturing by fmModulator.isCapturing.collectAsState()
    val inputLevel by fmModulator.inputLevel.collectAsState()
    val isRecording by fmModulator.isRecording.collectAsState()
    val recordingDuration by fmModulator.recordingDuration.collectAsState()
    val preEmphasis by fmModulator.preEmphasis.collectAsState()
    val narrowFM by fmModulator.narrowFM.collectAsState()
    val audioGain by fmModulator.audioGain.collectAsState()
    val squelchLevel by fmModulator.squelchLevel.collectAsState()
    val modulatorError by fmModulator.errorMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val errorMessage = sdrError ?: modulatorError

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("UHF/VHF Mic", fontWeight = FontWeight.Bold)
                        Text(
                            text = when (connectionStatus) {
                                SDRConnectionStatus.CONNECTED -> connectedDevice?.name ?: "Connected"
                                SDRConnectionStatus.TRANSMITTING -> "TX: ${connectedDevice?.name}"
                                SDRConnectionStatus.CONNECTING -> "Connecting..."
                                SDRConnectionStatus.ERROR -> "Error"
                                else -> "No Device"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (connectionStatus) {
                                SDRConnectionStatus.TRANSMITTING -> Color(0xFF4CAF50)
                                SDRConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                SDRConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeviceSheet = true }) {
                        Icon(
                            Icons.Default.Usb,
                            contentDescription = "Devices",
                            tint = if (connectionStatus == SDRConnectionStatus.CONNECTED ||
                                connectionStatus == SDRConnectionStatus.TRANSMITTING)
                                Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Radio, contentDescription = "Transmit") },
                    label = { Text("Transmit") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Audio") },
                    label = { Text("Audio") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Recordings") },
                    label = { Text("Recordings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> TransmitScreen(
                    currentFrequencyMHz = currentFrequencyMHz,
                    currentBand = currentBand,
                    txPowerDbm = txPowerDbm,
                    isTransmitting = isTransmitting,
                    connectionStatus = connectionStatus,
                    inputLevel = inputLevel,
                    onFrequencyChange = { sdrManager.setFrequency(it) },
                    onBandChange = { sdrManager.setBand(it) },
                    onPowerChange = { sdrManager.setTxPower(it) },
                    onStartTransmit = onStartTransmit,
                    onStopTransmit = onStopTransmit,
                    canTransmit = sdrManager.canTransmit()
                )
                1 -> AudioScreen(
                    inputLevel = inputLevel,
                    isCapturing = isCapturing,
                    isRecording = isRecording,
                    recordingDuration = recordingDuration,
                    preEmphasis = preEmphasis,
                    narrowFM = narrowFM,
                    audioGain = audioGain,
                    squelchLevel = squelchLevel,
                    onPreEmphasisChange = { fmModulator.setPreEmphasis(it) },
                    onNarrowFMChange = { fmModulator.setNarrowFM(it) },
                    onAudioGainChange = { fmModulator.setAudioGain(it) },
                    onSquelchChange = { fmModulator.setSquelchLevel(it) },
                    onStartRecording = { fmModulator.startRecording() },
                    onStopRecording = { fmModulator.stopRecording() }
                )
                2 -> RecordingsScreen(fmModulator = fmModulator)
            }
        }

        // Error snackbar
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = {
                        sdrManager.clearError()
                        fmModulator.clearError()
                    }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }

    // Device selection bottom sheet
    if (showDeviceSheet) {
        ModalBottomSheet(onDismissRequest = { showDeviceSheet = false }) {
            DeviceSelectionSheet(
                availableDevices = availableDevices,
                connectedDevice = connectedDevice,
                connectionStatus = connectionStatus,
                onRefresh = { sdrManager.refreshDevices() },
                onConnect = { sdrManager.requestConnection(it) },
                onDisconnect = { sdrManager.disconnect() }
            )
        }
    }

    // Settings bottom sheet
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            SettingsSheet(
                preEmphasis = preEmphasis,
                narrowFM = narrowFM,
                onPreEmphasisChange = { fmModulator.setPreEmphasis(it) },
                onNarrowFMChange = { fmModulator.setNarrowFM(it) }
            )
        }
    }
}

@Composable
fun TransmitScreen(
    currentFrequencyMHz: Double,
    currentBand: FrequencyBand,
    txPowerDbm: Int,
    isTransmitting: Boolean,
    connectionStatus: SDRConnectionStatus,
    inputLevel: Float,
    onFrequencyChange: (Double) -> Unit,
    onBandChange: (FrequencyBand) -> Unit,
    onPowerChange: (Int) -> Unit,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit,
    canTransmit: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Frequency display
        FrequencyDisplay(
            frequencyMHz = currentFrequencyMHz,
            band = currentBand,
            isTransmitting = isTransmitting
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Frequency dial
        FrequencyDial(
            frequencyMHz = currentFrequencyMHz,
            band = currentBand,
            onFrequencyChange = onFrequencyChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Band selector
        BandSelector(
            currentBand = currentBand,
            onBandChange = onBandChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TX Power slider
        PowerSlider(
            powerDbm = txPowerDbm,
            onPowerChange = onPowerChange
        )

        Spacer(modifier = Modifier.height(24.dp))

        // VU Meter
        if (isTransmitting) {
            VUMeter(level = inputLevel, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transmit button
        TransmitButton(
            isTransmitting = isTransmitting,
            enabled = canTransmit && connectionStatus == SDRConnectionStatus.CONNECTED || isTransmitting,
            onStartTransmit = onStartTransmit,
            onStopTransmit = onStopTransmit
        )

        if (!canTransmit && connectionStatus == SDRConnectionStatus.CONNECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This device cannot transmit",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun FrequencyDisplay(
    frequencyMHz: Double,
    band: FrequencyBand,
    isTransmitting: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tx_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tx_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isTransmitting) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TX",
                        color = Color.Red.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Text(
                    text = "${"%.3f".format(frequencyMHz)}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isTransmitting) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
                Text(
                    text = " MHz",
                    fontSize = 20.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Text(
                text = band.name,
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FrequencyDial(
    frequencyMHz: Double,
    band: FrequencyBand,
    onFrequencyChange: (Double) -> Unit
) {
    var rotation by remember { mutableStateOf(0f) }

    val range = band.maxFreqMHz - band.minFreqMHz
    val normalizedFreq = (frequencyMHz - band.minFreqMHz) / range

    LaunchedEffect(frequencyMHz, band) {
        rotation = (normalizedFreq * 270f - 135f).toFloat()
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .pointerInput(band) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val touchPoint = change.position
                    val angle = atan2(
                        touchPoint.y - center.y,
                        touchPoint.x - center.x
                    ) * (180f / PI.toFloat())

                    val clampedAngle = angle.coerceIn(-135f, 135f)
                    rotation = clampedAngle

                    val newNormalized = (clampedAngle + 135f) / 270f
                    val newFreq = band.minFreqMHz + (newNormalized * range)
                    onFrequencyChange(newFreq)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Dial background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()

            // Background arc
            drawArc(
                color = Color(0xFF333333),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            val progress = ((rotation + 135f) / 270f).coerceIn(0f, 1f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFE53935))
                ),
                startAngle = 135f,
                sweepAngle = progress * 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Dial knob
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF4A4A4A), Color(0xFF2A2A2A))
                    )
                )
                .border(2.dp, Color(0xFF555555), CircleShape)
                .rotate(rotation),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(4.dp, 20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF9800))
            )
        }
    }
}

@Composable
fun BandSelector(
    currentBand: FrequencyBand,
    onBandChange: (FrequencyBand) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SDRManager.ALL_BANDS) { band ->
            FilterChip(
                selected = band == currentBand,
                onClick = { onBandChange(band) },
                label = { Text(band.name, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun PowerSlider(
    powerDbm: Int,
    onPowerChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TX Power", style = MaterialTheme.typography.bodyMedium)
            Text(
                "$powerDbm dBm",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = powerDbm.toFloat(),
            onValueChange = { onPowerChange(it.toInt()) },
            valueRange = -40f..20f,
            steps = 59,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-40 dBm", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("+20 dBm", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun VUMeter(
    level: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Audio Level",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .background(
                        when {
                            level > 0.9f -> Color.Red
                            level > 0.7f -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
            )

            // Peak markers
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(20) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF1A1A1A))
                    )
                }
            }
        }
    }
}

@Composable
fun TransmitButton(
    isTransmitting: Boolean,
    enabled: Boolean,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Button(
        onClick = { if (isTransmitting) onStopTransmit() else onStartTransmit() },
        enabled = enabled,
        modifier = Modifier
            .size(if (isTransmitting) (120 * scale).dp else 120.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTransmitting) Color.Red else MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color.Gray
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isTransmitting) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isTransmitting) "Stop" else "Transmit",
                modifier = Modifier.size(32.dp)
            )
            Text(
                if (isTransmitting) "STOP" else "TX",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AudioScreen(
    inputLevel: Float,
    isCapturing: Boolean,
    isRecording: Boolean,
    recordingDuration: Long,
    preEmphasis: Boolean,
    narrowFM: Boolean,
    audioGain: Float,
    squelchLevel: Float,
    onPreEmphasisChange: (Boolean) -> Unit,
    onNarrowFMChange: (Boolean) -> Unit,
    onAudioGainChange: (Float) -> Unit,
    onSquelchChange: (Float) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio Input", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    VUMeter(level = inputLevel, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio Gain", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gain")
                        Text("${"%.1f".format(audioGain)}x")
                    }

                    Slider(
                        value = audioGain,
                        onValueChange = onAudioGainChange,
                        valueRange = 0.1f..5f
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Squelch", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Threshold")
                        Text("${(squelchLevel * 100).toInt()}%")
                    }

                    Slider(
                        value = squelchLevel,
                        onValueChange = onSquelchChange,
                        valueRange = 0f..0.5f
                    )

                    Text(
                        "Audio below this level will be muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FM Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pre-emphasis")
                            Text(
                                "Boost high frequencies for FM",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = preEmphasis, onCheckedChange = onPreEmphasisChange)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Narrow FM")
                            Text(
                                "5kHz deviation (wireless mics)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = narrowFM, onCheckedChange = onNarrowFMChange)
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recording", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(formatDuration(recordingDuration))
                            }

                            Button(
                                onClick = onStopRecording,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartRecording,
                            enabled = isCapturing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "Record")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Recording")
                        }

                        if (!isCapturing) {
                            Text(
                                "Start transmitting to enable recording",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(fmModulator: FMModulator) {
    val recordings = remember { mutableStateOf(fmModulator.getRecordings()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recordings", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { recordings.value = fmModulator.getRecordings() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (recordings.value.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No recordings yet", color = Color.Gray)
                    }
                }
            }
        } else {
            items(recordings.value) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${file.length() / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        IconButton(onClick = {
                            fmModulator.deleteRecording(file)
                            recordings.value = fmModulator.getRecordings()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceSelectionSheet(
    availableDevices: List<SDRDeviceInfo>,
    connectedDevice: SDRDeviceInfo?,
    connectionStatus: SDRConnectionStatus,
    onRefresh: () -> Unit,
    onConnect: (SDRDeviceInfo) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SDR Devices", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (connectedDevice != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(connectedDevice.name, fontWeight = FontWeight.Bold)
                        Text(
                            "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Button(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (availableDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.UsbOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No SDR devices found", color = Color.Gray)
                    Text(
                        "Connect HackRF, LimeSDR, or PlutoSDR",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            availableDevices.forEach { device ->
                if (device != connectedDevice) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnect(device) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(device.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "VID: 0x${device.vendorId.toString(16)} PID: 0x${device.productId.toString(16)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Connect")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSheet(
    preEmphasis: Boolean,
    narrowFM: Boolean,
    onPreEmphasisChange: (Boolean) -> Unit,
    onNarrowFMChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Modulation", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Pre-emphasis Filter")
                Text(
                    "Standard for FM broadcast",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(checked = preEmphasis, onCheckedChange = onPreEmphasisChange)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Narrow FM Mode")
                Text(
                    "5kHz deviation for wireless mics",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(checked = narrowFM, onCheckedChange = onNarrowFMChange)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("About", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("UHF/VHF Wireless Mic v1.0.0", color = Color.Gray)
        Text("© 2024 Iwinosa Aigbokhaevbo", color = Color.Gray)
        Text("All Rights Reserved", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 60000) % 60
    val hours = millis / 3600000
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
