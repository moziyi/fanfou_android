package com.kansen.fanfan.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.PhotoUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.ui.components.RepostDialog
import com.kansen.fanfan.ui.components.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onReply: (StatusUi) -> Unit,
    onOpenPhoto: (PhotoUi) -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val selfUserId by viewModel.selfUserId.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var repostTarget by remember { mutableStateOf<StatusUi?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val isOwnStatus = status?.user?.id?.let { it == selfUserId } == true

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isOwnStatus) {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                enabled = !uiState.deleting,
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "删除推文",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    enabled = !uiState.deleting,
                                    onClick = {
                                        menuOpen = false
                                        viewModel.deleteStatus()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val target = status
        when {
            // Deleted: the row is already gone from Room and onBack is about to
            // pop, so do not flash 「消息不可用」.
            uiState.deleted -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }

            target == null && uiState.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            target == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text("消息不可用") }
            }

            else -> {
                val others = uiState.context.filter { it.id != target.id }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item(key = "target") {
                        StatusCard(
                            status = target,
                            onClick = {},
                            onReply = { onReply(target) },
                            onRepost = { repostTarget = target },
                            onFavorite = { viewModel.toggleFavorite(target) },
                            onPhotoClick = onOpenPhoto,
                            onUserClick = { target.user?.id?.let(onOpenUser) },
                            isContextTarget = true,
                        )
                        HorizontalDivider()
                    }
                    if (others.isNotEmpty()) {
                        item(key = "context-header") {
                            Text(
                                text = "对话",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(items = others, key = { it.id }) { threadStatus ->
                            StatusCard(
                                status = threadStatus,
                                onClick = {},
                                onReply = { onReply(threadStatus) },
                                onRepost = { repostTarget = threadStatus },
                                onFavorite = { viewModel.toggleFavorite(threadStatus) },
                                onPhotoClick = onOpenPhoto,
                                onUserClick = { threadStatus.user?.id?.let(onOpenUser) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
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
