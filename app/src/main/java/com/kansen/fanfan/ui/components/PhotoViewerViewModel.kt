package com.kansen.fanfan.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.data.media.ImageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Saving the photo currently open in the viewer. */
@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val downloader: ImageDownloader,
) : ViewModel() {

    sealed interface SaveState {
        data object Idle : SaveState
        data class Saving(val progress: Float) : SaveState
        data object Saved : SaveState
        data class Failed(val message: String) : SaveState
    }

    private val _save = MutableStateFlow<SaveState>(SaveState.Idle)
    val save: StateFlow<SaveState> = _save.asStateFlow()

    fun saveOriginal(url: String) {
        if (_save.value is SaveState.Saving) return
        _save.value = SaveState.Saving(0f)
        viewModelScope.launch {
            downloader.saveToDownloads(url) { progress ->
                _save.value = SaveState.Saving(progress)
            }
                .onSuccess { _save.value = SaveState.Saved }
                .onFailure { _save.value = SaveState.Failed(it.message ?: "保存失败") }
        }
    }

    /** Called once the result has been shown, so re-opening the viewer starts clean. */
    fun consumeResult() {
        if (_save.value !is SaveState.Saving) _save.value = SaveState.Idle
    }
}
