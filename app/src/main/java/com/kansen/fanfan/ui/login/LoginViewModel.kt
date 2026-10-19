package com.kansen.fanfan.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object LoggingIn : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank() || _state.value is LoginUiState.LoggingIn) return
        _state.value = LoginUiState.LoggingIn
        viewModelScope.launch {
            authRepository.login(username, password)
                .onSuccess {
                    // MainViewModel's sessionFlow flips; MainActivity swaps screen.
                    _state.value = LoginUiState.Idle
                }
                .onFailure { t ->
                    _state.value = LoginUiState.Error(t.userMessage())
                }
        }
    }

    fun dismissError() {
        if (_state.value is LoginUiState.Error) _state.value = LoginUiState.Idle
    }
}
