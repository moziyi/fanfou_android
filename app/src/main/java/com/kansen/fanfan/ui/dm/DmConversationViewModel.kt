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

data class DmConversationUiState(
    val otherId: String = "",
    val otherName: String = "",
    val otherAvatarUrl: String? = null,
    val messages: List<DmUi> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
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
            runCatching { dmRepository.conversation(otherId = otherId, page = 1) }
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            messages = messages.sortedBy { m -> m.createdAt },
                            loading = false,
                        )
                    }
                    // Opening the thread marks it as read locally.
                    messages.maxByOrNull { it.createdAt }?.let { newest ->
                        dmState.markRead(otherId, newest.id)
                    }
                    // The title is the other side: the conversation payload
                    // carries both parties, and an empty thread has neither —
                    // then the profile is the only source.
                    val peer = runCatching { dmRepository.peerOf(otherId) }.getOrNull()
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

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.sending || state.messages.isEmpty()) return
        viewModelScope.launch {
            val oldest = state.messages.minByOrNull { it.createdAt }?.id ?: return@launch
            runCatching { dmRepository.conversation(otherId = otherId, page = 1, maxId = oldest) }
                .onSuccess { older ->
                    val merged = (older + state.messages)
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt }
                    _uiState.update { it.copy(messages = merged) }
                }
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
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
            val lastId = _uiState.value.messages.maxByOrNull { it.createdAt }?.id
            runCatching { dmRepository.send(otherId = otherId, text = trimmed, inReplyToId = lastId) }
                .onSuccess { sent ->
                    _uiState.update {
                        it.copy(
                            messages = (it.messages + sent).distinctBy { m -> m.id }
                                .sortedBy { m -> m.createdAt },
                            sending = false,
                        )
                    }
                    onSent()
                }
                .onFailure { t ->
                    _uiState.update { it.copy(sending = false, error = t.userMessage()) }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
