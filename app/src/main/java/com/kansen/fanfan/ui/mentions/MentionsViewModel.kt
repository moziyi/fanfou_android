package com.kansen.fanfan.ui.mentions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.MentionRepository
import com.kansen.fanfan.data.repository.StatusRepository
import com.kansen.fanfan.data.repository.TimelineRepository
import com.kansen.fanfan.data.repository.TimelineType
import com.kansen.fanfan.ui.components.FeedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MentionsViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val mentionRepository: MentionRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    val statuses: StateFlow<List<StatusUi>> = timelineRepository.timeline(TimelineType.MENTIONS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        val state = _uiState.value
        if (state.refreshing || state.initialLoading) return
        viewModelScope.launch {
            _uiState.update {
                if (initial) it.copy(initialLoading = true, error = null)
                else it.copy(refreshing = true, error = null)
            }
            timelineRepository.refresh(TimelineType.MENTIONS)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            initialLoaded = true,
                            endReached = false,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            initialLoaded = true,
                            error = t.userMessage(),
                        )
                    }
                }
            // Opening (or pulling) @我 counts as reading everything shown,
            // even when the refresh itself failed and only cache is visible.
            mentionRepository.markRead()
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.endReached || state.refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            timelineRepository.loadMore(TimelineType.MENTIONS)
                .onSuccess { hasMore ->
                    _uiState.update { it.copy(loadingMore = false, endReached = !hasMore) }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(loadingMore = false, error = t.userMessage()) }
                }
        }
    }

    fun toggleFavorite(status: StatusUi) {
        viewModelScope.launch {
            statusRepository.toggleFavorite(status)
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
        }
    }


    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
