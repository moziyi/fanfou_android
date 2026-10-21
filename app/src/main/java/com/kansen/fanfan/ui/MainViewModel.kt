package com.kansen.fanfan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.datastore.AuthSession
import com.kansen.fanfan.core.datastore.DmStateStore
import com.kansen.fanfan.data.repository.AuthRepository
import com.kansen.fanfan.data.repository.MentionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data object LoggedOut : MainUiState
    data class LoggedIn(val session: AuthSession) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    mentionRepository: MentionRepository,
    private val dmStateStore: DmStateStore,
) : ViewModel() {

    val state: StateFlow<MainUiState> = authRepository.sessionFlow
        .map { session -> if (session == null) MainUiState.LoggedOut else MainUiState.LoggedIn(session) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /** Badge on the @我 tab: mentions newer than the local read watermark. */
    val unreadMentions: StateFlow<Int> = mentionRepository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Unread direct-message conversations, for the 私信 tab badge. */
    val unreadDms: StateFlow<Int> = dmStateStore.unreadConversations.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0,
    )

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
