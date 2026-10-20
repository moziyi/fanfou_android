package com.kansen.fanfan.ui.mentions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.ui.components.RepostDialog
import com.kansen.fanfan.ui.components.StatusFeed

@Composable
fun MentionsScreen(
    onOpenStatus: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (StatusUi) -> Unit,
    viewModel: MentionsViewModel = hiltViewModel(),
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var repostTarget by remember { mutableStateOf<StatusUi?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    StatusFeed(
        statuses = statuses,
        refreshing = uiState.refreshing,
        initialLoaded = uiState.initialLoaded,
        loadingMore = uiState.loadingMore,
        endReached = uiState.endReached,
        emptyText = if (uiState.error != null) "加载失败，下拉重试" else "还没有 @ 你的消息",
        snackbar = snackbar,
        onRefresh = { viewModel.refresh() },
        onLoadMore = viewModel::loadMore,
        onOpenStatus = onOpenStatus,
        onOpenUser = onOpenUser,
        onReply = onReply,
        onRepost = { repostTarget = it },
        onFavorite = viewModel::toggleFavorite,
        modifier = Modifier.fillMaxSize(),
    )

    repostTarget?.let { target ->
        RepostDialog(
            target = target,
            onDismiss = { repostTarget = null },
            onConfirm = { comment ->
                viewModel.repost(target, comment)
                repostTarget = null
            },
        )
    }
}
