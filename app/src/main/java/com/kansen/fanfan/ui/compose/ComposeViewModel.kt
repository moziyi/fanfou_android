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

data class ComposeUiState(
    val text: String = "",
    val posting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
    val replyTo: StatusUi? = null,
    val repostOf: StatusUi? = null,
    val isRepost: Boolean = false,
    val draftReady: Boolean = false,
) {
    val canPublish: Boolean
        get() = !posting && StatusText.fits(text) && (isRepost || text.isNotBlank())

    val remaining: Int get() = StatusText.remaining(text)
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
    private val draftRepository: DraftRepository,
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
        val text = state.text.trim()
        _uiState.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            statusRepository.post(
                text = text,
                inReplyToStatusId = inReplyToId,
                repostStatusId = repostOfId,
            )
                .onSuccess {
                    draftRepository.clear(draftId)
                    _uiState.update { it.copy(posting = false, posted = true) }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(posting = false, error = t.userMessage()) }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val NO_ID = "-1"
        const val DRAFT_DEBOUNCE_MS = 400L
    }
}
