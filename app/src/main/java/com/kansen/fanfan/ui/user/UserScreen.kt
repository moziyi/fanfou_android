package com.kansen.fanfan.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kansen.fanfan.core.model.UserProfileUi
import com.kansen.fanfan.ui.components.StatusCard

private val Tabs = listOf("消息", "关注", "粉丝")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    initialTab: Int,
    onBack: () -> Unit,
    onOpenStatus: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    viewModel: UserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // The selected tab is view state, not data: keeping it here avoids a
    // second source of truth that can disagree with the loaded content.
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    LaunchedEffect(tab) {
        if (tab != 0) viewModel.ensurePeopleLoaded(tab)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.name ?: "用户",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading && state.profile == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.profile == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "加载失败",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::loadProfile) { Text("重试") }
                }
            }

            else -> {
                val profile = state.profile!!
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") {
                        ProfileHeader(
                            onSendMessage = { onSendMessage(profile.id) },
                            profile = profile,
                            isSelf = state.isSelf,
                            busy = state.followingBusy,
                            onFollow = viewModel::toggleFollow,
                        )
                    }
                    item(key = "tabs") {
                        TabRow(selectedTabIndex = tab) {
                            Tabs.forEachIndexed { index, label ->
                                Tab(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    text = { Text(label) },
                                )
                            }
                        }
                    }

                    when (tab) {
                        0 -> {
                            items(items = state.statuses, key = { it.id }) { status ->
                                StatusCard(
                                    status = status,
                                    onClick = { onOpenStatus(status.id) },
                                    onUserClick = { onOpenUser(status.user?.id.orEmpty()) },
                                    onReply = {},
                                    onRepost = {},
                                    onFavorite = {},
                                    onPhotoClick = null,
                                    showActions = false,
                                    showFavorite = false,
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                            if (state.statuses.isEmpty() && state.statusesError != null) {
                                item(key = "statuses-error") {
                                    ErrorState(
                                        message = state.statusesError!!,
                                        onRetry = viewModel::retryStatuses,
                                    )
                                }
                            } else if (state.statuses.isEmpty()) {
                                item(key = "statuses-empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "还没有消息",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                item(key = "statuses-footer") {
                                    PagingFooter(
                                        loading = state.statusesLoading,
                                        endReached = state.statusesEndReached,
                                        loadedOnce = state.statusesLoadedOnce,
                                        error = state.statusesError,
                                        onLoadMore = viewModel::loadMoreStatuses,
                                        onRetry = viewModel::retryStatuses,
                                    )
                                }
                            }
                        }

                        1 -> peopleList(
                            page = state.friends,
                            keyPrefix = "friends",
                            emptyText = "还没有关注任何人",
                            onOpenUser = onOpenUser,
                            onLoadMore = { viewModel.loadMorePeople(1) },
                            onRetry = { viewModel.retryPeople(1) },
                        )

                        else -> peopleList(
                            page = state.followers,
                            keyPrefix = "followers",
                            emptyText = "还没有粉丝",
                            onOpenUser = onOpenUser,
                            onLoadMore = { viewModel.loadMorePeople(2) },
                            onRetry = { viewModel.retryPeople(2) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfileUi,
    isSelf: Boolean,
    busy: Boolean,
    onFollow: () -> Unit,
    onSendMessage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${profile.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.protected) {
                    Text(
                        text = "受保护账号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (!isSelf) {
                Spacer(Modifier.width(8.dp))
                when {
                    profile.following ->
                        FilledTonalButton(onClick = onFollow, enabled = !busy) { Text("已关注") }

                    // Waiting for the other side to accept (protected account).
                    profile.followRequested ->
                        FilledTonalButton(onClick = {}, enabled = false) { Text("已申请") }

                    else -> Button(onClick = onFollow, enabled = !busy) { Text("关注") }
                }
                Spacer(Modifier.width(8.dp))
                // Entry point for a new conversation: Fanfou only allows
                // direct messages between people who follow each other, so
                // the server decides whether the send works.
                OutlinedButton(onClick = onSendMessage) { Text("私信") }
            }
        }

        profile.description?.let { bio ->
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        profile.location?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Stat("消息", profile.statusesCount)
            Stat("关注", profile.friendsCount)
            Stat("粉丝", profile.followersCount)
            Stat("收藏", profile.favouritesCount)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun Stat(label: String, value: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.peopleList(
    page: PeoplePage,
    keyPrefix: String,
    emptyText: String,
    onOpenUser: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (page.items.isEmpty() && page.loadedOnce) {
        item(key = "$keyPrefix-empty") {
            if (page.error != null) {
                ErrorState(message = page.error, onRetry = onRetry)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    items(items = page.items, key = { "$keyPrefix-${it.id}" }) { user ->
        PersonRow(user = user, onClick = { onOpenUser(user.id) })
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
    item(key = "$keyPrefix-footer") {
        PagingFooter(
            loading = page.loading,
            endReached = page.endReached,
            loadedOnce = page.loadedOnce,
            error = page.error,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun PersonRow(user: UserProfileUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            user.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (user.following) {
            Text(
                text = "已关注",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Footer that also triggers the next page when it becomes visible. The
 * ViewModel guards against duplicate requests.
 */
@Composable
private fun PagingFooter(
    loading: Boolean,
    endReached: Boolean,
    loadedOnce: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        // First load: the spinner belongs to the list, not to a retry loop.
        !loadedOnce || loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        // A failed load must not page again on its own — that is what kept the
        // spinner alive forever on protected accounts.
        error != null -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onRetry) { Text("重试") }
        }

        !endReached -> LaunchedEffect(Unit) { onLoadMore() }

        else -> Box(
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

/** Failure state for a list that could not be loaded at all. */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
