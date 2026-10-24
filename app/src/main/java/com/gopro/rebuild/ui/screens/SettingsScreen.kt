package com.gopro.rebuild.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gopro.rebuild.camera.*
import com.gopro.rebuild.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentSettings by viewModel.currentSettings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                // Video Settings
                item {
                    SettingsHeader("Video")
                }

                item {
                    SettingSelector(
                        title = "Resolution",
                        currentValue = currentSettings.resolution.label,
                        options = VideoResolution.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setResolution(VideoResolution.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "Frame Rate",
                        currentValue = currentSettings.frameRate.label,
                        options = FrameRate.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setFrameRate(FrameRate.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "Lens",
                        currentValue = currentSettings.fov.label,
                        options = FieldOfView.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setFov(FieldOfView.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "HyperSmooth",
                        currentValue = currentSettings.hyperSmooth.label,
                        options = HyperSmooth.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setHyperSmooth(HyperSmooth.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "Hindsight",
                        currentValue = currentSettings.hindsight.label,
                        options = Hindsight.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setHindsight(Hindsight.entries[idx])
                        }
                    )
                }

                // Protune
                item {
                    SettingsHeader("Protune")
                }

                item {
                    SettingSelector(
                        title = "Color",
                        currentValue = currentSettings.color.label,
                        options = ProtuneColor.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setColor(ProtuneColor.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "White Balance",
                        currentValue = currentSettings.whiteBalance.label,
                        options = WhiteBalance.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setWhiteBalance(WhiteBalance.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "ISO Max",
                        currentValue = currentSettings.isoMax.label,
                        options = ISO.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setIsoMax(ISO.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "ISO Min",
                        currentValue = currentSettings.isoMin.label,
                        options = ISO.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setIsoMin(ISO.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "Sharpness",
                        currentValue = currentSettings.sharpness.label,
                        options = Sharpness.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setSharpness(Sharpness.entries[idx])
                        }
                    )
                }

                item {
                    SettingSelector(
                        title = "EV Comp",
                        currentValue = currentSettings.evComp.label,
                        options = EVComp.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setEvComp(EVComp.entries[idx])
                        }
                    )
                }

                // General
                item {
                    SettingsHeader("General")
                }

                item {
                    SettingSelector(
                        title = "Timer",
                        currentValue = currentSettings.timer.label,
                        options = Timer.entries.map { it.label },
                        onSelect = { idx ->
                            viewModel.setTimer(Timer.entries[idx])
                        }
                    )
                }

                item {
                    SettingAction(
                        title = "Sync Date/Time",
                        subtitle = "Set camera time to phone time",
                        onClick = { viewModel.syncDateTime() }
                    )
                }

                item {
                    SettingAction(
                        title = "Locate Camera",
                        subtitle = "Beep to find camera",
                        onClick = { viewModel.locateCamera() }
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSelector(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentValue,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == currentValue) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    trailingIcon = if (option == currentValue) {
                        { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SettingAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
