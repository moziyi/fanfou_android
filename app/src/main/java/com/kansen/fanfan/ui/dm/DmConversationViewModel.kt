package com.kansen.fanfan.ui.dm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.datastore.DmStateStore
import com.kansen.fanfan.core.model.DmUi
import com.kansen.fanfan.data.repository.DmRepository
import com.kansen.fanfan.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.network.api.DmApi
import com.kansen.fanfan.data.repository.DmSendUnconfirmedException

data class DmConversationUiState(
    val otherId: String = "",
    val otherName: String = "",
    val otherAvatarUrl: String? = null,
    val messages: List<DmUi> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    /** The next page of older messages to request; 1 before the first load. */
    val nextPage: Int = 1,
    /** No older messages are left to load. */
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * One conversation. Messages are kept in memory only and refetched on entry;
 * the API returns them newest first, the screen shows them oldest first.
 */
@HiltViewModel
class DmConversationViewModel @Inject constructor(
    private val dmRepository: DmRepository,
    private val userRepository: UserRepository,
    private val dmState: DmStateStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val otherId: String = savedStateHandle.get<String>("userId").orEmpty()

    private val _uiState = MutableStateFlow(DmConversationUiState(otherId = otherId))
    val uiState: StateFlow<DmConversationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (otherId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatchingCancellable { dmRepository.conversation(otherId = otherId, page = 1) }
                .onSuccess { thread ->
                    _uiState.update {
                        it.copy(
                            messages = thread.messages.sortedBy { m -> m.createdAt },
                            loading = false,
                            nextPage = 2,
                            endReached = thread.messages.size < DmApi.PAGE_SIZE,
                        )
                    }
                    // Opening the thread marks it as read locally.
                    thread.messages.maxByOrNull { it.createdAt }?.let { newest ->
                        dmState.markRead(otherId, newest.id)
                    }
                    // The title is the other side: the conversation payload
                    // carries both parties, and an empty thread has neither —
                    // then the profile is the only source.
                    val peer = thread.peer
                    if (peer != null) {
                        _uiState.update {
                            it.copy(otherName = peer.name, otherAvatarUrl = peer.avatarUrl)
                        }
                    } else {
                        userRepository.profile(otherId).getOrNull()
                            ?.let { profile ->
                                _uiState.update {
                                    it.copy(
                                        otherName = profile.name,
                                        otherAvatarUrl = profile.avatarUrl,
                                    )
                                }
                            }
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(loading = false, error = t.userMessage()) }
                }
        }
    }

    /**
     * Loads the messages before the oldest one on screen. The list grows at
     * the top, so the screen keeps the reading position.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.sending) return
        if (state.endReached || state.messages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true, error = null) }
            // The page number is counted, never derived from the message count:
            // a message that arrives while the user reads shifts the server's
            // offsets, and a derived page would fetch the same page again.
            val nextPage = state.nextPage
            runCatchingCancellable { dmRepository.conversation(otherId = otherId, page = nextPage) }
                .onSuccess { thread ->
                    val merged = (thread.messages + state.messages)
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt }
                    _uiState.update {
                        it.copy(
                            messages = merged,
                            loadingMore = false,
                            nextPage = nextPage + 1,
                            endReached = thread.messages.size < DmApi.PAGE_SIZE,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(loadingMore = false, error = t.userMessage()) }
                }
        }
    }

    /**
     * Sends a message. The text stays in the input box unless the server
     * accepted it — a failed send must not look like a sent message.
     */
    fun send(text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null) }
            // Only a message from the other side can be replied to.
            val replyTo = _uiState.value.messages
                .maxByOrNull { it.createdAt }
                ?.takeIf { !it.isMine }
                ?.id
            runCatchingCancellable {
                dmRepository.send(otherId = otherId, text = trimmed, inReplyToId = replyTo)
            }.onSuccess { sent ->
                _uiState.update {
                    it.copy(
                        messages = (it.messages + sent).distinctBy { m -> m.id }
                            .sortedBy { m -> m.createdAt },
                        sending = false,
                    )
                }
                dmState.markRead(otherId, sent.id)
                onSent()
            }.onFailure { t ->
                _uiState.update { it.copy(sending = false, error = t.userMessage()) }
                // The message reached the server but the answer was unreadable:
                // keeping the text would invite a duplicate.
                if (t is DmSendUnconfirmedException) onSent()
            }
        }
    }

}
