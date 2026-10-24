package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.wifi.MediaFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val camera get() = cameraRepository.getCurrentCamera()

    private val _mediaItems = MutableStateFlow<List<Pair<String, MediaFile>>>(emptyList())
    val mediaItems: StateFlow<List<Pair<String, MediaFile>>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedItem = MutableStateFlow<Pair<String, MediaFile>?>(null)
    val selectedItem: StateFlow<Pair<String, MediaFile>?> = _selectedItem.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            camera?.getMediaList()
                ?.onSuccess { mediaList ->
                    val items = mutableListOf<Pair<String, MediaFile>>()
                    mediaList.media.forEach { directory ->
                        directory.fs.forEach { file ->
                            // Filter out LRV (low res) and THM (thumbnail) files
                            if (!file.n.endsWith(".LRV", ignoreCase = true) &&
                                !file.n.endsWith(".THM", ignoreCase = true)) {
                                items.add(directory.d to file)
                            }
                        }
                    }
                    // Sort by creation time, newest first
                    _mediaItems.value = items.sortedByDescending { it.second.cre }
                }
                ?.onFailure { e ->
                    _error.value = "Failed to load media: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    fun getThumbnailUrl(directory: String, filename: String): String {
        return camera?.getThumbnailUrl(directory, filename) ?: ""
    }

    fun getMediaUrl(directory: String, filename: String): String {
        return camera?.getMediaUrl(directory, filename) ?: ""
    }

    fun selectItem(directory: String, file: MediaFile) {
        _selectedItem.value = directory to file
    }

    fun clearSelection() {
        _selectedItem.value = null
    }

    fun downloadMedia(directory: String, file: MediaFile) {
        viewModelScope.launch {
            // TODO: Implement download with progress
            // This would use DownloadManager or custom download logic
            _error.value = "Download feature coming soon"
        }
    }

    fun deleteMedia(directory: String, file: MediaFile) {
        viewModelScope.launch {
            camera?.deleteMedia(directory, file.n)
                ?.onSuccess {
                    clearSelection()
                    loadMedia() // Refresh list
                }
                ?.onFailure { e ->
                    _error.value = "Failed to delete: ${e.message}"
                }
        }
    }
}
