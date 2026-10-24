package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.media.PreviewPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LivePreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val previewPlayer: PreviewPlayer
) : ViewModel() {

    private val camera get() = cameraRepository.getCurrentCamera()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val exoPlayer: ExoPlayer?
        get() = previewPlayer.exoPlayer

    val exoPlayerFlow: kotlinx.coroutines.flow.StateFlow<ExoPlayer?>
        get() = previewPlayer.exoPlayerFlow

    fun startPreview() {
        viewModelScope.launch {
            _error.value = null

            // Debug harness: play a local file without touching the camera so the
            // ExoPlayer/TS pipeline can be isolated from camera HTTP.
            val debugFile = java.io.File(
                previewPlayer.debugFileDir(), "debug_local.ts"
            )
            if (debugFile.exists()) {
                previewPlayer.startPreview(streamUrl = debugFile.absolutePath)
                _isPlaying.value = true
                return@launch
            }

            val currentCamera = camera
            if (currentCamera == null) {
                _error.value = "No camera connected"
                return@launch
            }

            // Prepare UDP listener before asking the camera to stream. Starting
            // the camera first can lose initial packets before ExoPlayer binds.
            previewPlayer.startPreview(
                streamUrl = currentCamera.getPreviewUrl()
            )
            delay(300)
            currentCamera.startPreview().onSuccess {
                _isPlaying.value = true
            }.onFailure { e ->
                previewPlayer.stopPreview()
                _isPlaying.value = false
                _error.value = e.message ?: "Failed to start camera preview"
            }
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            previewPlayer.stopPreview()
            camera?.stopPreview()
            _isPlaying.value = false
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            camera?.startRecording()?.onSuccess {
                _isRecording.value = true
            }?.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            camera?.stopRecording()?.onSuccess {
                _isRecording.value = false
            }?.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.release()
    }
}
