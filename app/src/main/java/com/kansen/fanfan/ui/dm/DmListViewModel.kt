package com.kansen.fanfan.ui.dm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.datastore.DmStateStore
import com.kansen.fanfan.core.model.DmConversationUi
import com.kansen.fanfan.core.model.DmUnread
import com.kansen.fanfan.data.repository.DmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DmListUiState(
    val items: List<DmConversationUi> = emptyList(),
    /** Conversation ids with a message the user has not seen yet. */
    val unreadIds: Set<String> = emptySet(),
    /** The next page to request; 1 before the first load. */
    val nextPage: Int = 1,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * The 私信 tab: one row per conversation. The list is refetched on entry
 * (direct messages are not cached, see the M3 design §3.1) and the unread
 * count is written back so the tab badge stays correct.
 */
@HiltViewModel
class DmListViewModel @Inject constructor(
    private val dmRepository: DmRepository,
    private val dmState: DmStateStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DmListUiState())
    val uiState: StateFlow<DmListUiState> = _uiState.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        val state = _uiState.value
        if (state.refreshing || state.initialLoading || state.loadingMore) return
        viewModelScope.launch {
            _uiState.update {
                if (initial) it.copy(initialLoading = true, error = null)
                else it.copy(refreshing = true, error = null)
            }
            runCatching { dmRepository.conversations(page = 1) }
                .onSuccess { conversations ->
                    val markers = dmState.readMarkers.first()
                    val unread = DmUnread.evaluate(conversations, markers)
                    _uiState.update {
                        it.copy(
                            items = conversations,
                            unreadIds = unread,
                            nextPage = 2,
                            initialLoading = false,
                            refreshing = false,
                            endReached = conversations.isEmpty(),
                        )
                    }
                    dmState.setUnreadConversations(unread.size)
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            error = t.userMessage(),
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.endReached || state.initialLoading || state.refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            val nextPage = state.nextPage
            runCatching { dmRepository.conversations(page = nextPage) }
                .onSuccess { older ->
                    _uiState.update {
                        it.copy(
                            items = (it.items + older).distinctBy { c -> c.otherId },
                            nextPage = nextPage + 1,
                            loadingMore = false,
                            endReached = older.isEmpty(),
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(loadingMore = false, error = t.userMessage()) }
                }
        }
    }

}
