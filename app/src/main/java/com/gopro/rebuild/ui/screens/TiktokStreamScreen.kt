package com.gopro.rebuild.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.gopro.rebuild.tiktok.RelayStatus
import com.gopro.rebuild.ui.viewmodel.TiktokStreamViewModel

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
fun AudioVuMeter(
    level: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 14
    val activeBars = (level * barCount).toInt().coerceIn(0, barCount)

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "GoPro Mic",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(
            "GoPro Mic",
            color = Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(2.dp))
        for (i in 0 until barCount) {
            val isActive = i < activeBars
            val color = when {
                i >= 11 -> Color(0xFFFF453A) // Merah (peak)
                i >= 8 -> Color(0xFFFFD60A)  // Kuning (tinggi)
                else -> Color(0xFF30D158)    // Hijau (nominal)
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((7 + (i * 1.1f)).dp)
                    .background(
                        color = if (isActive) color else color.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
fun CameraViewfinder(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    isWifiConnected: Boolean,
    isLive: Boolean,
    durationSeconds: Long,
    bitrateKbps: Double,
    fps: Double,
    volumeLevel: Float,
    isSpeakerMuted: Boolean,
    onToggleSpeakerMute: () -> Unit,
    onConnectWifi: () -> Unit,
    onRefreshPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101012))
            .border(
                width = if (isLive) 2.dp else 1.dp,
                color = if (isLive) Color(0xFFFE2C55) else Color(0xFF2C2C2E),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        if (isWifiConnected) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                        Text("Mempersiapkan preview kamera...", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // GoPro Wi-Fi belum terhubung
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color.Gray
                    )
                    Text(
                        "Wi-Fi GoPro Belum Terhubung (10.5.5.9)",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = onConnectWifi,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Hubungkan Wi-Fi GoPro", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Overlay Atas Kiri: Status Live / Standby
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLive) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFE2C55), CircleShape)
                    )
                    Text(
                        "LIVE",
                        color = Color(0xFFFE2C55),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        formatDuration(durationSeconds),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                    if (bitrateKbps > 0) {
                        Text(
                            "${bitrateKbps.toInt()}k",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }
                    if (fps > 0) {
                        Text(
                            "${fps.toInt()}fps",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isWifiConnected) Color(0xFF30D158) else Color.Gray, CircleShape)
                    )
                    Text(
                        if (isWifiConnected) "STANDBY" else "OFFLINE",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Overlay Atas Kanan: Mute Toggle + Reload Preview
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSpeakerMute,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Speaker Mute Toggle",
                    tint = if (isSpeakerMuted) Color(0xFFFF453A) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (isWifiConnected) {
                IconButton(
                    onClick = onRefreshPreview,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Preview",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Overlay Bawah Kiri: Real-Time Audio Meter
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            AudioVuMeter(
                level = volumeLevel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiktokStreamScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: TiktokStreamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val streamerStats by viewModel.streamerStats.collectAsState()
    val relayStats by viewModel.relayStats.collectAsState()
    val volumeLevel by viewModel.volumeLevel.collectAsState()
    val isSpeakerMuted by viewModel.isSpeakerMuted.collectAsState()
    val exoPlayer by viewModel.exoPlayerFlow.collectAsState()
    val context = LocalContext.current

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isWifiConnected) {
        if (uiState.isWifiConnected) {
            viewModel.startCameraViewfinder()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopCameraViewfinder()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    viewModel.importCookies(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membaca file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val topics = listOf(
        null to "None",
        "chat_interview" to "Chat & Interview",
        "gaming" to "Gaming",
        "music" to "Music",
        "dance" to "Dance",
        "beauty" to "Beauty",
        "fashion" to "Fashion",
        "food" to "Food",
        "sports" to "Sports",
        "education" to "Education"
    )

    val regions = listOf(
        "id" to "Indonesia (id)",
        "" to "Default",
        "us" to "United States (us)",
        "gb" to "United Kingdom (gb)",
        "jp" to "Japan (jp)",
        "kr" to "South Korea (kr)"
    )

    fun copyToClipboard(text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label disalin ke clipboard", Toast.LENGTH_SHORT).show()
    }

    val isLiveActive = streamerStats.status == RelayStatus.STREAMING || streamerStats.status == RelayStatus.STARTING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TikTok Live Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (isLiveActive) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFE2C55))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (uiState.hasSessionId) {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. HERO: Camera Viewfinder with Live Overlays
            CameraViewfinder(
                exoPlayer = exoPlayer,
                isWifiConnected = uiState.isWifiConnected,
                isLive = isLiveActive,
                durationSeconds = streamerStats.durationSeconds,
                bitrateKbps = streamerStats.bitrateKbps,
                fps = streamerStats.fps,
                volumeLevel = volumeLevel,
                isSpeakerMuted = isSpeakerMuted,
                onToggleSpeakerMute = { viewModel.toggleSpeakerMute() },
                onConnectWifi = { viewModel.connectCameraWifi() },
                onRefreshPreview = { viewModel.startCameraViewfinder() }
            )

            // Pesan Error / Sukses
            uiState.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
            }

            uiState.successMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }
                }
            }

            // 2. Kontrol Utama Siaran Live
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.setTitle(it) },
                        label = { Text("Judul Live Stream") },
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLiveActive
                    )

                    // Tombol Aksi Utama: Mulai / Akhiri Live Streaming
                    if (isLiveActive) {
                        Button(
                            onClick = { viewModel.stopLiveStreaming() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Akhiri Live Streaming", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.startLiveStreaming()
                            },
                            enabled = !uiState.isStartingLive && !uiState.isGenerating && uiState.hasSessionId,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (uiState.isStartingLive) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Mempersiapkan Live Room & Audio...", fontSize = 15.sp)
                            } else {
                                Icon(Icons.Default.Videocam, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mulai Live Streaming ke TikTok", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }

                    // Status relay jaringan
                    if (isLiveActive && relayStats.packetsReceived > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Paket GoPro: ${relayStats.packetsReceived}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Bitrate: ${streamerStats.bitrateKbps.toInt()} kbps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Status Sesi TikTok & Cookies
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        uiState.isLoggedIn -> Color(0xFFE8F5E9)
                        uiState.hasSessionId -> Color(0xFFFFF3E0)
                        else -> Color(0xFFFFEBEE)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                uiState.isLoggedIn -> Icons.Default.CheckCircle
                                uiState.hasSessionId -> Icons.Default.Warning
                                else -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when {
                                uiState.isLoggedIn -> Color(0xFF2E7D32)
                                uiState.hasSessionId -> Color(0xFFEF6C00)
                                else -> Color(0xFFC62828)
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    uiState.isLoggedIn -> "TikTok Sesi Aktif"
                                    uiState.hasSessionId -> "Cookie Tersimpan (Belum diverifikasi)"
                                    else -> "Belum Login TikTok"
                                },
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    uiState.isLoggedIn -> Color(0xFF2E7D32)
                                    uiState.hasSessionId -> Color(0xFFEF6C00)
                                    else -> Color(0xFFC62828)
                                }
                            )
                            Text(
                                text = "sessionid: ${if (uiState.hasSessionId) "Tersedia" else "Kosong"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!uiState.isLoggedIn || !uiState.hasSessionId) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onNavigateToLogin,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Login Web")
                            }
                            FilledTonalButton(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import File")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { showPasteDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste Cookie Manual")
                        }
                    }
                }
            }

            // 4. Pengaturan Lanjutan (Kategori, Region & RTMP Key)
            Card(
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedSettings = !showAdvancedSettings },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Pengaturan Lanjutan & RTMP Key", fontWeight = FontWeight.Medium)
                        }
                        Icon(
                            imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Topic selector
                            var topicExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = topicExpanded,
                                onExpandedChange = { topicExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = topics.firstOrNull { it.first == uiState.selectedTopic }?.second ?: "None",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Topic / Kategori") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = topicExpanded,
                                    onDismissRequest = { topicExpanded = false }
                                ) {
                                    topics.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setTopic(key)
                                                topicExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Region selector
                            var regionExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = regionExpanded,
                                onExpandedChange = { regionExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = regions.firstOrNull { it.first == uiState.regionPriority }?.second ?: "Indonesia (id)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Region Priority") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = regionExpanded,
                                    onDismissRequest = { regionExpanded = false }
                                ) {
                                    regions.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setRegion(key)
                                                regionExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.enableReplay,
                                    onCheckedChange = { viewModel.setEnableReplay(it) }
                                )
                                Text("Simpan Replay Live", style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.closeRoomWhenStreamEnds,
                                    onCheckedChange = { viewModel.setCloseRoom(it) }
                                )
                                Text("Tutup Room saat Stream Selesai", style = MaterialTheme.typography.bodyMedium)
                            }

                            uiState.streamInfo?.let { info ->
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("RTMP Stream Info", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                OutlinedTextField(
                                    value = info.fullRtmpUrl,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Full RTMP URL") },
                                    trailingIcon = {
                                        IconButton(onClick = { copyToClipboard(info.fullRtmpUrl, "RTMP URL") }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = info.shareUrl,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Share URL") },
                                    trailingIcon = {
                                        IconButton(onClick = { copyToClipboard(info.shareUrl, "Share URL") }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste Cookie TikTok") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste format JSON array dari EditThisCookie atau format raw string.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("Paste cookies di sini...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pasteText.isNotBlank()) {
                            viewModel.importCookies(pasteText.trim())
                            showPasteDialog = false
                            pasteText = ""
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
