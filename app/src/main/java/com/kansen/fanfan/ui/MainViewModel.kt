package com.kansen.fanfan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.datastore.AuthSession
import com.kansen.fanfan.data.repository.AuthRepository
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
) : ViewModel() {

    val state: StateFlow<MainUiState> = authRepository.sessionFlow
        .map { session -> if (session == null) MainUiState.LoggedOut else MainUiState.LoggedIn(session) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
