package com.gopro.rebuild.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gopro.rebuild.ui.viewmodel.CameraInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraInfoScreen(
    onBack: () -> Unit,
    viewModel: CameraInfoViewModel = hiltViewModel()
) {
    val cameraInfo by viewModel.cameraInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    InfoCard(
                        title = "Model",
                        value = cameraInfo?.model ?: "Unknown",
                        icon = Icons.Default.CameraAlt
                    )
                }

                item {
                    InfoCard(
                        title = "Serial Number",
                        value = cameraInfo?.serialNumber ?: "Unknown",
                        icon = Icons.Default.Numbers
                    )
                }

                item {
                    InfoCard(
                        title = "Firmware",
                        value = cameraInfo?.firmware ?: "Unknown",
                        icon = Icons.Default.Memory
                    )
                }

                item {
                    InfoCard(
                        title = "AP SSID",
                        value = cameraInfo?.apSsid ?: "Unknown",
                        icon = Icons.Default.Wifi
                    )
                }

                item {
                    InfoCard(
                        title = "AP MAC",
                        value = cameraInfo?.apMac ?: "Unknown",
                        icon = Icons.Default.Router
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Text(
                        "Storage",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    InfoCard(
                        title = "SD Card",
                        value = if (cameraInfo?.sdCardPresent == true) "Inserted" else "Not Present",
                        icon = Icons.Default.SdCard
                    )
                }

                item {
                    InfoCard(
                        title = "Remaining Space",
                        value = formatBytes(cameraInfo?.remainingSpace ?: 0),
                        icon = Icons.Default.Storage
                    )
                }

                item {
                    InfoCard(
                        title = "Total Photos",
                        value = "${cameraInfo?.totalPhotos ?: 0}",
                        icon = Icons.Default.Photo
                    )
                }

                item {
                    InfoCard(
                        title = "Total Videos",
                        value = "${cameraInfo?.totalVideos ?: 0}",
                        icon = Icons.Default.VideoLibrary
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Text(
                        "Battery",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    InfoCard(
                        title = "Battery Level",
                        value = "${cameraInfo?.batteryLevel ?: 0}%",
                        icon = Icons.Default.BatteryStd
                    )
                }

                item {
                    InfoCard(
                        title = "External Battery",
                        value = if (cameraInfo?.externalBattery == true) "Connected" else "Not Connected",
                        icon = Icons.Default.BatteryChargingFull
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        else -> "$bytes B"
    }
}
