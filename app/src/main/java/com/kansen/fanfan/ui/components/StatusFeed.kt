package com.kansen.fanfan.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kansen.fanfan.core.model.StatusUi
import kotlin.math.roundToInt

/** How far the list slides down during a pull, and where it rests while refreshing. */
private val PullOffset = 56.dp
private val RefreshChipSize = 36.dp

/**
 * The status list shared by the home timelines and @我: pull-to-refresh,
 * infinite scroll, empty/loading/footer states and the card actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFeed(
    statuses: List<StatusUi>,
    refreshing: Boolean,
    initialLoaded: Boolean,
    loadingMore: Boolean,
    endReached: Boolean,
    emptyText: String,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenStatus: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (StatusUi) -> Unit,
    onRepost: (StatusUi) -> Unit,
    onFavorite: (StatusUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(statuses) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            statuses.isNotEmpty() && last >= statuses.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            // Only a user pull drives the indicator; the first load of a
            // timeline uses the centered spinner instead, so nothing drops
            // in by itself on tab switches.
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = { RefreshChip(refreshing = refreshing, state = pullState) },
        ) {
            // The list itself slides down with the pull and the chip lands
            // in the gap it leaves — the stock M3 indicator instead floats
            // on top of the cards.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, pullOffsetPx(refreshing, pullState)) },
            ) {
                when {
                    statuses.isEmpty() && !initialLoaded -> {
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
                                        text = emptyText,
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
                                    onUserClick = { onOpenUser(status.user?.id.orEmpty()) },
                                    onReply = { onReply(status) },
                                    onRepost = { onRepost(status) },
                                    onFavorite = { onFavorite(status) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                            item {
                                FeedFooter(loadingMore = loadingMore, endReached = endReached)
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
private fun FeedFooter(loadingMore: Boolean, endReached: Boolean) {
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
