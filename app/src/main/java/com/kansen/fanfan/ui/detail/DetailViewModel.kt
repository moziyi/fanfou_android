package com.kansen.fanfan.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val context: List<StatusUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    private val statusId: String = savedStateHandle.get<String>("id").orEmpty()

    val status: StateFlow<StatusUi?> = statusRepository.status(statusId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (statusId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val refreshFailure = statusRepository.refreshStatus(statusId).exceptionOrNull()
            statusRepository.context(statusId)
                .onSuccess { thread ->
                    _uiState.update { it.copy(loading = false, context = thread) }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(loading = false, error = (refreshFailure ?: t).userMessage())
                    }
                }
        }
    }

    fun toggleFavorite(status: StatusUi) {
        viewModelScope.launch {
            statusRepository.toggleFavorite(status)
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
        }
    }

    fun repost(status: StatusUi, comment: String) {
        viewModelScope.launch {
            statusRepository.post(text = comment.trim(), repostStatusId = status.id)
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
