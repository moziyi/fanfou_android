package com.kansen.fanfan.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.TimelineType
import com.kansen.fanfan.ui.components.RepostDialog
import com.kansen.fanfan.ui.components.StatusCard
import kotlin.math.roundToInt

/** How far the list slides down during a pull, and where it rests while refreshing. */
private val PullOffset = 56.dp
private val RefreshChipSize = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenStatus: (String) -> Unit,
    onReply: (StatusUi) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    var repostTarget by remember { mutableStateOf<StatusUi?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    // Reset scroll when switching timelines.
    LaunchedEffect(type) { listState.scrollToItem(0) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            statuses.isNotEmpty() && last >= statuses.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (type == TimelineType.HOME) 0 else 1) {
            TimelineType.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = type == entry,
                    onClick = { viewModel.selectType(entry) },
                    text = { Text(entry.label) },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                // Only a user pull drives the indicator; the first load of a
                // timeline uses the centered spinner instead, so nothing drops
                // in by itself on tab switches.
                isRefreshing = uiState.refreshing,
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = { RefreshChip(refreshing = uiState.refreshing, state = pullState) },
            ) {
                // The list itself slides down with the pull and the chip lands
                // in the gap it leaves — the stock M3 indicator instead floats
                // on top of the cards.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(0, pullOffsetPx(uiState.refreshing, pullState))
                        },
                ) {
                    when {
                        statuses.isEmpty() && !uiState.initialLoaded -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        statuses.isEmpty() -> {
                            // A scrollable child is required for the pull gesture
                            // to reach PullToRefreshBox — "下拉重试" must work.
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (uiState.error != null) "加载失败，下拉重试" else "还没有消息",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                // Keep the last card clear of the compose FAB.
                                contentPadding = PaddingValues(bottom = 88.dp),
                            ) {
                                items(items = statuses, key = { it.id }) { status ->
                                    StatusCard(
                                        status = status,
                                        onClick = { onOpenStatus(status.id) },
                                        onReply = { onReply(status) },
                                        onRepost = { repostTarget = status },
                                        onFavorite = { viewModel.toggleFavorite(status) },
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                                item {
                                    TimelineFooter(
                                        loadingMore = uiState.loadingMore,
                                        endReached = uiState.endReached,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

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

/** Pull distance in px: eased up during the gesture, held open while refreshing. */
private fun Density.pullOffsetPx(
    refreshing: Boolean,
    state: PullToRefreshState,
): Int {
    val fraction = if (refreshing) 1f else state.distanceFraction
    return (PullOffset.toPx() * fraction).roundToInt()
}

/** Raised chip that sits inside the gap the list opens while pulling. */
@Composable
private fun BoxScope.RefreshChip(refreshing: Boolean, state: PullToRefreshState) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset {
                val gap = pullOffsetPx(refreshing, state)
                val y = gap - RefreshChipSize.toPx() - 8.dp.toPx()
                IntOffset(0, y.coerceAtLeast(2.dp.toPx()).roundToInt())
            },
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(RefreshChipSize)
                .graphicsLayer {
                    val fraction = if (refreshing) 1f else state.distanceFraction
                    alpha = fraction.coerceIn(0f, 1f)
                    rotationZ = fraction * 180f
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineFooter(loadingMore: Boolean, endReached: Boolean) {
    when {
        loadingMore -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        endReached -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "没有更多了",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
