package com.kansen.fanfan.ui.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.TimelineType
import com.kansen.fanfan.ui.components.StatusFeed

@Composable
fun TimelineScreen(
    onOpenStatus: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (StatusUi) -> Unit,
    onRepost: (StatusUi) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // The error stays in the state: the snackbar is gone after a few seconds,
    // and a timeline without a cache (the public one) would otherwise show
    // "还没有消息" as if the feed were simply empty. The next refresh clears
    // it again.
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = TimelineType.feedTabs.indexOf(type).coerceAtLeast(0)) {
            TimelineType.feedTabs.forEach { entry ->
                Tab(
                    selected = type == entry,
                    onClick = { viewModel.selectType(entry) },
                    text = { Text(entry.label) },
                )
            }
        }

        // A new timeline starts at the top with a fresh list state.
        key(type) {
            StatusFeed(
                statuses = statuses,
                refreshing = uiState.refreshing,
                initialLoaded = uiState.initialLoaded,
                loadingMore = uiState.loadingMore,
                endReached = uiState.endReached,
                emptyText = if (uiState.error != null) "加载失败，下拉重试" else "还没有消息",
                snackbar = snackbar,
                onRefresh = { viewModel.refresh() },
                onLoadMore = viewModel::loadMore,
                onOpenStatus = onOpenStatus,
                onOpenUser = onOpenUser,
                onReply = onReply,
                onRepost = onRepost,
                onFavorite = viewModel::toggleFavorite,
            )
        }
    }
}
