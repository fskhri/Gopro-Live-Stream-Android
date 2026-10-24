package com.gopro.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gopro.rebuild.camera.PresetGroup
import com.gopro.rebuild.ui.viewmodel.ControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    onNavigateToMedia: () -> Unit,
    onNavigateToPreview: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    onNavigateToCameraInfo: () -> Unit = {},
    onNavigateToTiktok: () -> Unit = {},
    onDisconnect: () -> Unit,
    viewModel: ControlViewModel = hiltViewModel()
) {
    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val isWifiConnected by viewModel.isWifiConnected.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cameraStatus.cameraInfo?.model ?: "GoPro")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isWifiConnected) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                if (isWifiConnected) "WiFi Connected" else "BLE Only",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWifiConnected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Battery
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            when {
                                cameraStatus.batteryLevel > 75 -> Icons.Default.BatteryFull
                                cameraStatus.batteryLevel > 50 -> Icons.Default.Battery6Bar
                                cameraStatus.batteryLevel > 25 -> Icons.Default.Battery3Bar
                                else -> Icons.Default.BatteryAlert
                            },
                            contentDescription = "Battery",
                            modifier = Modifier.size(18.dp),
                            tint = when {
                                cameraStatus.batteryLevel > 20 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (cameraStatus.batteryLevel > 0) "${cameraStatus.batteryLevel}%" else "--%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // TikTok Live button
                    IconButton(onClick = onNavigateToTiktok) {
                        Icon(Icons.Default.LiveTv, "TikTok Live", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Info button
                    IconButton(onClick = onNavigateToCameraInfo) {
                        Icon(Icons.Default.Info, "Camera Info")
                    }

                    // Power off
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.PowerSettingsNew, "Disconnect")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // WiFi connection card
            if (!isWifiConnected) {
                WifiConnectionCard(onConnect = { viewModel.connectWifi() })
                Spacer(Modifier.height(16.dp))
            }

            // Mode selector
            ModeSelector(
                currentMode = cameraStatus.currentMode,
                onModeSelected = { viewModel.setMode(it) }
            )

            Spacer(Modifier.height(16.dp))

            // Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable(enabled = isWifiConnected) { onNavigateToPreview() },
                contentAlignment = Alignment.Center
            ) {
                if (isWifiConnected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap for Live Preview",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Connect WiFi for Preview",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Recording indicator
                if (cameraStatus.isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Red)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("REC", color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Shutter button
            ShutterButton(
                isRecording = cameraStatus.isRecording,
                mode = cameraStatus.currentMode,
                onClick = {
                    if (cameraStatus.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.startRecording()
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // Bottom action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Media",
                    onClick = onNavigateToMedia,
                    enabled = isWifiConnected
                )
                ActionButton(
                    icon = Icons.Default.Tune,
                    label = "Presets",
                    onClick = onNavigateToPresets,
                    enabled = cameraStatus.isConnected
                )
                ActionButton(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = onNavigateToSettings,
                    enabled = cameraStatus.isConnected
                )
                ActionButton(
                    icon = Icons.Default.PowerOff,
                    label = "Sleep",
                    onClick = { viewModel.powerOff() }
                )
            }
        }

        error?.let { errorMsg ->
            Snackbar(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(errorMsg)
            }
        }
    }
}

@Composable
private fun WifiConnectionCard(onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Connect WiFi")
                Text(
                    "Enable preview, media & settings",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ModeSelector(
    currentMode: PresetGroup,
    onModeSelected: (PresetGroup) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PresetGroup.entries.forEach { mode ->
            ModeChip(
                mode = mode,
                isSelected = mode == currentMode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(
    mode: PresetGroup,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (mode) {
        PresetGroup.VIDEO -> Icons.Default.Videocam
        PresetGroup.PHOTO -> Icons.Default.CameraAlt
        PresetGroup.TIMELAPSE -> Icons.Default.Timer
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(mode.label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) }
    )
}

@Composable
private fun ShutterButton(
    isRecording: Boolean,
    mode: PresetGroup,
    onClick: () -> Unit
) {
    val size = 80.dp
    val innerSize = if (isRecording) 32.dp else 64.dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(
                    if (mode == PresetGroup.VIDEO || isRecording) Color.Red else Color.White
                )
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
