package com.kansen.fanfan.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.datastore.SelfUser
import com.kansen.fanfan.ui.login.LoginScreen
import com.kansen.fanfan.ui.theme.FanfanTheme
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.notifications.MentionNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var statusDao: StatusDao

    /** Notification payload, kept until MainShell can act on it (login may come first). */
    private val pendingStatusId = MutableStateFlow<String?>(null)
    private val pendingMentions = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            FanfanTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val unread = viewModel.unreadMentions.collectAsStateWithLifecycle().value
                val openStatusId = pendingStatusId.asStateFlow().collectAsStateWithLifecycle().value
                val openMentions = pendingMentions.asStateFlow().collectAsStateWithLifecycle().value

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
                            unreadMentions = unread,
                            openStatusId = openStatusId,
                            openMentions = openMentions,
                            onDeepLinkHandled = {
                                pendingStatusId.value = null
                                pendingMentions.value = false
                            },
                            testNotification = {
                                lifecycleScope.launch {
                                    val id = statusDao.newestStatusId() ?: return@launch
                                    MentionNotifications.showDebug(
                                        context = this@MainActivity,
                                        statusId = id,
                                        author = "调试",
                                        text = "这是一条调试通知，用于验证通知栏与点击跳转",
                                    )
                                }
                            },
                            onLogout = viewModel::logout,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_STATUS_ID)?.takeIf { it.isNotBlank() }?.let {
            pendingStatusId.value = it
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_MENTIONS, false)) {
            pendingMentions.value = true
        }
    }

    companion object {
        const val EXTRA_STATUS_ID = "status_id"
        const val EXTRA_OPEN_MENTIONS = "open_mentions"
    }
}
