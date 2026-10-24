package com.gopro.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gopro.rebuild.camera.PresetGroup
import com.gopro.rebuild.camera.PresetIcon
import com.gopro.rebuild.ui.viewmodel.PresetViewModel
import com.gopro.rebuild.wifi.PresetData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(
    onBack: () -> Unit,
    viewModel: PresetViewModel = hiltViewModel()
) {
    val presetGroups by viewModel.presetGroups.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPresets()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Preset group tabs
            PresetGroupTabs(
                selectedGroup = selectedGroup,
                onGroupSelected = { viewModel.selectGroup(it) }
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Presets grid
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val groupPresets = presetGroups[selectedGroup] ?: emptyList()

                    items(groupPresets) { preset ->
                        PresetCard(
                            preset = preset,
                            isSelected = preset.id == currentPreset,
                            onClick = { viewModel.selectPreset(preset.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetGroupTabs(
    selectedGroup: PresetGroup,
    onGroupSelected: (PresetGroup) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PresetGroup.entries) { group ->
            FilterChip(
                selected = group == selectedGroup,
                onClick = { onGroupSelected(group) },
                label = { Text(group.label) },
                leadingIcon = {
                    Icon(
                        imageVector = when (group) {
                            PresetGroup.VIDEO -> Icons.Default.Videocam
                            PresetGroup.PHOTO -> Icons.Default.CameraAlt
                            PresetGroup.TIMELAPSE -> Icons.Default.Timer
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: PresetData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = getPresetIcon(preset.icon)
    val title = getPresetTitle(preset.titleId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preset icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (preset.isModified) {
                    Text(
                        text = "Modified",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getPresetIcon(iconId: Int): ImageVector {
    return when (iconId) {
        0, 70 -> Icons.Default.Videocam         // Video/Standard
        1, 78, 79 -> Icons.Default.Timer        // Timelapse/TimeWarp
        2, 80 -> Icons.Default.NightsStay       // Night Lapse
        3, 75 -> Icons.Default.CameraAlt        // Photo
        4, 77 -> Icons.Default.BedtimeOff       // Night Photo
        5 -> Icons.Default.BurstMode            // Burst
        71 -> Icons.Default.DirectionsRun       // Activity
        72 -> Icons.Default.Movie               // Cinematic
        73 -> Icons.Default.SlowMotionVideo     // Ultra Slo-Mo
        74 -> Icons.Default.Tune                // Basic
        76 -> Icons.Default.Animation           // LiveBurst
        81 -> Icons.Default.Star                // Star Trails
        82 -> Icons.Default.Brush               // Light Painting
        83 -> Icons.Default.Route               // Light Trails
        84 -> Icons.Default.CropFree            // Full Frame
        else -> Icons.Default.Settings
    }
}

private fun getPresetTitle(titleId: Int): String {
    return when (titleId) {
        0 -> "Standard"
        1 -> "Activity"
        2 -> "Cinematic"
        3 -> "Ultra Slo-Mo"
        4 -> "Basic"
        5 -> "Photo"
        6 -> "Live Burst"
        7 -> "Night Photo"
        8 -> "TimeWarp"
        9 -> "Time Lapse"
        10 -> "Night Lapse"
        11 -> "Star Trails"
        12 -> "Light Painting"
        13 -> "Light Trails"
        14 -> "Max Video"
        15 -> "Max Photo"
        16 -> "Max TimeWarp"
        else -> "Preset $titleId"
    }
}
