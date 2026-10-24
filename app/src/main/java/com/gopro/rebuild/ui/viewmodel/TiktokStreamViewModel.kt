package com.gopro.rebuild.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.media.GoProUdpRelay
import com.gopro.rebuild.media.PreviewPlayer
import com.gopro.rebuild.media.RELAY_PORT_PREVIEW
import com.gopro.rebuild.media.RelayStats
import com.gopro.rebuild.media.RtmpStreamer
import com.gopro.rebuild.media.StreamerStats
import com.gopro.rebuild.tiktok.RelayStatus
import com.gopro.rebuild.tiktok.TiktokRepository
import com.gopro.rebuild.tiktok.TiktokService
import com.gopro.rebuild.tiktok.TiktokStreamInfo
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.WifiConnectionManager
import com.gopro.rebuild.wifi.WifiConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TiktokStreamVM"

data class TiktokStreamUiState(
    val title: String = "GoPro Hero 6 Live",
    val selectedTopic: String? = null,
    val selectedGameId: String? = null,
    val gameList: Map<String, String> = emptyMap(),
    val regionPriority: String = "id",
    val enableReplay: Boolean = false,
    val closeRoomWhenStreamEnds: Boolean = true,
    val isMatureContent: Boolean = false,
    val isGenerating: Boolean = false,
    val isStartingLive: Boolean = false,
    val streamInfo: TiktokStreamInfo? = null,
    val isLoggedIn: Boolean = false,
    val hasSessionId: Boolean = false,
    val hasSidTt: Boolean = false,
    val cookiesLength: Int = 0,
    val isCameraConnected: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isConnectingWifi: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TiktokStreamViewModel @Inject constructor(
    private val tiktokService: TiktokService,
    private val tiktokRepository: TiktokRepository,
    private val rtmpStreamer: RtmpStreamer,
    private val udpRelay: GoProUdpRelay,
    private val cameraRepository: CameraRepository,
    private val wifiApi: GoProWifiApi,
    private val wifiConnectionManager: WifiConnectionManager,
    private val previewPlayer: PreviewPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(TiktokStreamUiState())
    val uiState: StateFlow<TiktokStreamUiState> = _uiState.asStateFlow()

    val streamerStats: StateFlow<StreamerStats> = rtmpStreamer.streamStats
    val relayStats: StateFlow<RelayStats> = udpRelay.stats
    val volumeLevel: StateFlow<Float> = previewPlayer.audioVolumeLevel
    val isSpeakerMuted: StateFlow<Boolean> = previewPlayer.isSpeakerMuted
    val exoPlayerFlow = previewPlayer.exoPlayerFlow

    init {
        observeCookies()
        loadGameList()
        observeCameraAndWifi()
    }

    fun toggleSpeakerMute() {
        previewPlayer.toggleSpeakerMute()
    }

    fun startCameraViewfinder() {
        viewModelScope.launch {
            try {
                val camera = cameraRepository.getCurrentCamera()
                camera?.startPreview() ?: wifiApi.startPreview()
                delay(300)
                udpRelay.start()
                previewPlayer.startRelayedPreview(RELAY_PORT_PREVIEW)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting camera viewfinder: ${e.message}")
            }
        }
    }

    fun stopCameraViewfinder() {
        previewPlayer.stopPreview()
        if (streamerStats.value.status != RelayStatus.STREAMING) {
            udpRelay.stop()
        }
    }

    private fun observeCookies() {
        viewModelScope.launch {
            tiktokRepository.cookiesFlow.collect { cookies ->
                val hasSession = tiktokRepository.hasSessionId(cookies)
                val hasSid = tiktokRepository.hasSidTt(cookies)
                val len = cookies?.length ?: 0

                _uiState.update {
                    it.copy(
                        hasSessionId = hasSession,
                        hasSidTt = hasSid,
                        cookiesLength = len
                    )
                }
                if (hasSession) {
                    verifyLogin()
                } else {
                    _uiState.update { it.copy(isLoggedIn = false) }
                }
            }
        }
    }

    private fun observeCameraAndWifi() {
        viewModelScope.launch {
            cameraRepository.connectedCamera.collect { cam ->
                _uiState.update { it.copy(isCameraConnected = cam != null) }
            }
        }
        viewModelScope.launch {
            wifiConnectionManager.connectionState.collect { wifiState ->
                _uiState.update {
                    it.copy(isWifiConnected = wifiState is WifiConnectionState.Connected)
                }
            }
        }
    }

    fun verifyLogin() {
        viewModelScope.launch {
            val valid = tiktokService.checkLoginStatus()
            _uiState.update { it.copy(isLoggedIn = valid) }
        }
    }

    private fun loadGameList() {
        viewModelScope.launch {
            val games = tiktokService.fetchGameTags()
            if (games.isNotEmpty()) {
                _uiState.update { it.copy(gameList = games) }
            }
        }
    }

    fun connectCameraWifi() {
        val camera = cameraRepository.getCurrentCamera()
        if (camera == null) {
            _uiState.update { it.copy(error = "Kamera GoPro belum terhubung via BLE. Buka menu Scan/Connect dulu.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingWifi = true, error = null, successMessage = "Menghubungkan ke Wi-Fi GoPro...") }
            val res = camera.connectWifi()
            _uiState.update { it.copy(isConnectingWifi = false) }
            if (res.isSuccess) {
                _uiState.update { it.copy(successMessage = "Wi-Fi GoPro berhasil terhubung!") }
            } else {
                _uiState.update { it.copy(error = "Gagal menghubungkan Wi-Fi: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun setTopic(topic: String?) {
        _uiState.update {
            it.copy(
                selectedTopic = topic,
                selectedGameId = if (topic != "gaming") null else it.selectedGameId
            )
        }
    }

    fun setGameId(gameId: String?) {
        _uiState.update { it.copy(selectedGameId = gameId) }
    }

    fun setRegion(region: String) {
        _uiState.update { it.copy(regionPriority = region) }
    }

    fun setEnableReplay(enable: Boolean) {
        _uiState.update { it.copy(enableReplay = enable) }
    }

    fun setCloseRoom(close: Boolean) {
        _uiState.update { it.copy(closeRoomWhenStreamEnds = close) }
    }

    fun setMatureContent(mature: Boolean) {
        _uiState.update { it.copy(isMatureContent = mature) }
    }

    fun importCookies(raw: String) {
        val result = tiktokRepository.parseCookieInput(raw)
        result.onSuccess { parsed ->
            viewModelScope.launch {
                tiktokRepository.saveCookies(parsed)
                val hasSession = tiktokRepository.hasSessionId(parsed)
                _uiState.update {
                    it.copy(
                        successMessage = if (hasSession) "Cookies berhasil disimpan (ada sessionid)!"
                        else "Cookies tersimpan tapi sessionid TIDAK ADA.",
                        error = null
                    )
                }
            }
        }.onFailure { err ->
            _uiState.update { it.copy(error = err.message, successMessage = null) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    /**
     * Start live streaming directly to TikTok:
     * 1. Creates room on TikTok Live Studio if not created yet.
     * 2. Starts camera preview stream (UDP MPEG-TS packets to 8554).
     * 3. Pipes local MPEG-TS through FFmpegKit to TikTok RTMP URL.
     */
    fun startLiveStreaming() {
        val state = _uiState.value
        if (!state.hasSessionId) {
            _uiState.update { it.copy(error = "Belum login TikTok / tidak ada sessionid di cookies.") }
            return
        }
        if (state.title.trim().isEmpty()) {
            _uiState.update { it.copy(error = "Judul live tidak boleh kosong.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingLive = true, error = null, successMessage = null) }

            // 1. Ensure GoPro camera is outputting UDP stream to port 8554
            Log.i(TAG, "Ensuring camera stream and UDP relay are running...")
            val camera = cameraRepository.getCurrentCamera()
            val camResult = camera?.startPreview() ?: wifiApi.startPreview()
            if (camResult.isFailure) {
                Log.w(TAG, "Camera startPreview warning: ${camResult.exceptionOrNull()?.message}")
            }
            udpRelay.start()
            previewPlayer.startRelayedPreview(RELAY_PORT_PREVIEW)

            // 2. Ensure TikTok room & RTMP URL exist
            var info = _uiState.value.streamInfo
            if (info == null) {
                val topicMap = mapOf(
                    "chat_interview" to "42",
                    "gaming" to "5",
                    "music" to "6",
                    "dance" to "3",
                    "beauty" to "9",
                    "fashion" to "9",
                    "food" to "4",
                    "sports" to "13",
                    "education" to "45"
                )
                val hashtagId = topicMap[state.selectedTopic] ?: "42"
                val gameTagId = if (state.selectedTopic == "gaming") state.selectedGameId ?: "0" else "0"

                Log.i(TAG, "Creating TikTok live room...")
                val createResult = tiktokService.createStream(
                    title = state.title.trim(),
                    hashtagId = hashtagId,
                    gameTagId = gameTagId,
                    enableReplay = state.enableReplay,
                    closeRoomWhenStreamEnds = state.closeRoomWhenStreamEnds,
                    regionPriority = state.regionPriority,
                    isMatureContent = state.isMatureContent
                )

                if (createResult.isFailure) {
                    val err = createResult.exceptionOrNull()?.message ?: "Gagal membuat room TikTok"
                    Log.e(TAG, "Create stream error: $err")
                    _uiState.update { it.copy(isStartingLive = false, error = err) }
                    return@launch
                }

                info = createResult.getOrNull()
                _uiState.update { it.copy(streamInfo = info) }
            }

            if (info == null) {
                _uiState.update { it.copy(isStartingLive = false, error = "RTMP URL TikTok tidak valid.") }
                return@launch
            }

            // 3. Start RTMP streamer (UDP Relay 8554 -> 8555 -> FFmpeg -> TikTok RTMP)
            Log.i(TAG, "Starting RTMP stream to: ${info.fullRtmpUrl}")
            rtmpStreamer.startStream(info.fullRtmpUrl) { ok, err ->
                if (!ok && err != null) {
                    _uiState.update { it.copy(error = "Streaming relay error: $err") }
                }
            }

            _uiState.update {
                it.copy(
                    isStartingLive = false,
                    successMessage = "Live streaming ke TikTok sedang berjalan!"
                )
            }
        }
    }

    /**
     * Stop only the RTMP/relay stream, keeping the room open on TikTok.
     */
    fun stopLiveStreaming() {
        viewModelScope.launch {
            rtmpStreamer.stopStream()
            val camera = cameraRepository.getCurrentCamera()
            camera?.stopPreview() ?: wifiApi.stopPreview()
            _uiState.update { it.copy(successMessage = "Streaming ke TikTok dihentikan.") }
        }
    }

    /**
     * Generate stream key only without starting stream immediately.
     */
    fun generateStreamKey() {
        val state = _uiState.value
        if (state.title.trim().isEmpty()) {
            _uiState.update { it.copy(error = "Judul live tidak boleh kosong") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, successMessage = null) }

            val topicMap = mapOf(
                "chat_interview" to "42",
                "gaming" to "5",
                "music" to "6",
                "dance" to "3",
                "beauty" to "9",
                "fashion" to "9",
                "food" to "4",
                "sports" to "13",
                "education" to "45"
            )
            val hashtagId = topicMap[state.selectedTopic] ?: "42"
            val gameTagId = if (state.selectedTopic == "gaming") state.selectedGameId ?: "0" else "0"

            val result = tiktokService.createStream(
                title = state.title.trim(),
                hashtagId = hashtagId,
                gameTagId = gameTagId,
                enableReplay = state.enableReplay,
                closeRoomWhenStreamEnds = state.closeRoomWhenStreamEnds,
                regionPriority = state.regionPriority,
                isMatureContent = state.isMatureContent
            )

            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamInfo = info,
                        successMessage = "Stream key TikTok Live Studio berhasil dibuat!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = err.message ?: "Gagal membuat stream key"
                    )
                }
            }
        }
    }

    fun startRelayStream() {
        startLiveStreaming()
    }

    fun stopRelayStream() {
        stopLiveStreaming()
    }

    fun endRoom() {
        val info = _uiState.value.streamInfo ?: return
        viewModelScope.launch {
            stopLiveStreaming()
            val ok = tiktokService.endStream(info.roomId)
            _uiState.update {
                it.copy(
                    streamInfo = if (ok) null else it.streamInfo,
                    successMessage = if (ok) "Live room berhasil diakhiri di TikTok" else "Gagal mengakhiri room di TikTok"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopLiveStreaming()
            tiktokRepository.clearCookies()
            _uiState.update {
                it.copy(
                    streamInfo = null,
                    isLoggedIn = false,
                    hasSessionId = false,
                    hasSidTt = false,
                    cookiesLength = 0,
                    successMessage = "Berhasil logout TikTok"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (streamerStats.value.status != RelayStatus.STREAMING) {
            stopCameraViewfinder()
        }
    }
}
