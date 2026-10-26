package com.kansen.fanfan.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val context: List<StatusUi> = emptyList(),
    val error: String? = null,
    val deleting: Boolean = false,
    /** One-shot: the target was deleted, the screen should close. */
    val deleted: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val statusId: String = savedStateHandle.get<String>("id").orEmpty()

    /** The logged-in user's handle; the delete action is only offered for own statuses. */
    val selfUserId: StateFlow<String?> = tokenStore.session
        .map { it?.user?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun deleteStatus() {
        val state = _uiState.value
        if (state.deleting || statusId.isBlank()) return
        // Defense in depth: the menu is only rendered for own statuses, but the
        // check is repeated here so no future caller can delete someone else's.
        val self = selfUserId.value
        if (self == null || status.value?.user?.id != self) {
            _uiState.update { it.copy(error = "只能删除自己的推文") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, error = null) }
            statusRepository.delete(statusId)
                .onSuccess { _uiState.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { t ->
                    _uiState.update { it.copy(deleting = false, error = t.userMessage()) }
                }
        }
    }


    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
