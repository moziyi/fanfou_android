package com.kansen.fanfan.ui.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.model.StatusText
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.DraftRepository
import com.kansen.fanfan.data.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.kansen.fanfan.data.media.PhotoPreparer
import com.kansen.fanfan.data.media.PreparedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ComposeUiState(
    val text: String = "",
    val posting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
    val replyTo: StatusUi? = null,
    val repostOf: StatusUi? = null,
    val isRepost: Boolean = false,
    val draftReady: Boolean = false,
    /** The picture that will be posted, if one was picked. */
    val photo: PreparedPhoto? = null,
    /** A small preview of [photo] for the composer. */
    val photoPreview: ImageBitmap? = null,
    /** The picked picture is being decoded and scaled. */
    val photoBusy: Boolean = false,
    /** The picture is being uploaded. */
    val uploading: Boolean = false,
    /** Upload progress in 0..1. */
    val uploadProgress: Float = 0f,
) {
    val canPublish: Boolean
        get() = !posting && !photoBusy &&
            StatusText.fits(text) && (isRepost || text.isNotBlank())

    val remaining: Int get() = StatusText.remaining(text)

    /** A picture can only be attached to a new status: the upload endpoint
     *  takes neither a reply nor a repost target. */
    val canAttachPhoto: Boolean get() = replyTo == null && !isRepost
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
    private val draftRepository: DraftRepository,
    private val photoPreparer: PhotoPreparer,
) : ViewModel() {

    private val inReplyToId: String? =
        savedStateHandle.get<String>("inReplyTo")?.takeIf { it.isNotBlank() && it != NO_ID }

    private val repostOfId: String? =
        savedStateHandle.get<String>("repostOf")?.takeIf { it.isNotBlank() && it != NO_ID }

    private val draftId: String = when {
        inReplyToId != null -> "reply:$inReplyToId"
        repostOfId != null -> "repost:$repostOfId"
        else -> "composer"
    }

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private var draftJob: Job? = null

    init {
        viewModelScope.launch {
            val draft = draftRepository.load(draftId)
            val context = (inReplyToId ?: repostOfId)?.let { loadStatus(it) }
            _uiState.update {
                it.copy(
                    text = draft?.text ?: it.text,
                    replyTo = if (inReplyToId != null) context else null,
                    repostOf = if (repostOfId != null) context else null,
                    isRepost = repostOfId != null,
                    draftReady = true,
                )
            }
        }
    }

    private suspend fun loadStatus(id: String): StatusUi? {
        statusRepository.status(id).firstOrNull()?.let { return it }
        statusRepository.refreshStatus(id)
        return statusRepository.status(id).firstOrNull()
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(text = text) }
        scheduleDraftSave(text)
    }

    /**
     * Picks up the picture the user chose. It is decoded and scaled in the
     * background, so the composer stays responsive.
     */
    fun attachPhoto(uri: Uri) {
        if (!_uiState.value.canAttachPhoto) return
        viewModelScope.launch {
            _uiState.update { it.copy(photoBusy = true, error = null) }
            val prepared = withContext(Dispatchers.Default) {
                runCatching { photoPreparer.prepare(uri) }.map { it to it.previewBitmap() }
            }
            prepared
                .onSuccess { (photo, preview) ->
                    _uiState.update {
                        it.copy(
                            photo = photo,
                            photoPreview = preview,
                            photoBusy = false,
                            // The endpoint refuses an empty text; put the
                            // default in so the user can edit it.
                            text = if (it.text.isBlank()) DEFAULT_PHOTO_TEXT else it.text,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(photoBusy = false, error = t.userMessage()) }
                }
        }
    }

    fun removePhoto() {
        _uiState.update { it.copy(photo = null, photoPreview = null) }
    }

    private fun scheduleDraftSave(text: String) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            draftRepository.save(draftId, text, inReplyToId, repostOfId)
        }
    }

    fun post() {
        val state = _uiState.value
        if (!state.canPublish) return
        val photo = state.photo
        _uiState.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            val result = if (photo != null) {
                _uiState.update { it.copy(uploading = true, uploadProgress = 0f) }
                statusRepository.postPhoto(text = state.text, photo = photo) { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            } else {
                statusRepository.post(
                    text = state.text,
                    inReplyToStatusId = inReplyToId,
                    repostStatusId = repostOfId,
                    repostAuthor = state.repostOf?.user?.screenName,
                )
            }
            result
                .onSuccess {
                    draftRepository.clear(draftId)
                    _uiState.update {
                        it.copy(posting = false, uploading = false, posted = true)
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(posting = false, uploading = false, error = t.userMessage())
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val NO_ID = "-1"
        const val DRAFT_DEBOUNCE_MS = 400L

        /** The text the server uses itself when a photo is posted without one. */
        const val DEFAULT_PHOTO_TEXT = "上传了新照片"
    }
}
