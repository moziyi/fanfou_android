package com.kansen.fanfan.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.StatusRepository
import com.kansen.fanfan.data.repository.TimelineRepository
import com.kansen.fanfan.data.repository.TimelineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    /** First load of a timeline: shows the centered spinner, never the pull indicator. */
    val initialLoading: Boolean = false,
    /** User-initiated pull: drives the pull-to-refresh indicator only. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val initialLoaded: Boolean = false,
    val error: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    private val _type = MutableStateFlow(TimelineType.HOME)
    val type: StateFlow<TimelineType> = _type.asStateFlow()

    val statuses: StateFlow<List<StatusUi>> = _type
        .flatMapLatest { timelineRepository.timeline(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun selectType(type: TimelineType) {
        if (_type.value == type) return
        _type.value = type
        _uiState.value = TimelineUiState()
        refresh(initial = true)
    }

    /**
     * [initial] is the first load of a timeline (centered spinner).
     * A pull is always explicit user intent, so it is never deduped — the
     * hourly rate budget is nowhere near reachable by hand.
     */
    fun refresh(initial: Boolean = false) {
        val state = _uiState.value
        if (state.refreshing || state.initialLoading) return
        viewModelScope.launch {
            _uiState.update {
                if (initial) it.copy(initialLoading = true, error = null)
                else it.copy(refreshing = true, error = null)
            }
            timelineRepository.refresh(_type.value)
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
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.endReached || state.refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            timelineRepository.loadMore(_type.value)
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

    fun repost(status: StatusUi, comment: String) {
        viewModelScope.launch {
            statusRepository.post(text = comment.trim(), repostStatusId = status.id)
                .onSuccess { _uiState.update { it.copy(error = null) } }
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
