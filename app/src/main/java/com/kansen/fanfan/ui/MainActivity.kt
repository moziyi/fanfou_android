package com.kansen.fanfan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.datastore.SelfUser
import com.kansen.fanfan.ui.MainShell
import com.kansen.fanfan.ui.login.LoginScreen
import com.kansen.fanfan.ui.theme.FanfanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FanfanTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val state = viewModel.state.collectAsStateWithLifecycle().value
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (state) {
                        MainUiState.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { LinearProgressIndicator() }

                        MainUiState.LoggedOut -> LoginScreen()

                        is MainUiState.LoggedIn -> MainShell(
                            user = SelfUser(
                                id = state.session.user?.id ?: state.session.token,
                                screenName = state.session.user?.screenName ?: "",
                                avatarUrl = state.session.user?.avatarUrl,
                            ),
                            onLogout = viewModel::logout,
                        )
                    }
                }
            }
        }
    }
}
