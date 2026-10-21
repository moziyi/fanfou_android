package com.kansen.fanfan.ui.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.DmUi

/** Direct messages are limited to 140 characters, same as a status. */
private const val MAX_LENGTH = 140

/**
 * One conversation: bubbles (own messages right, the other side left) and an
 * input row. A failed send keeps the text so it can be retried, and the
 * reason is shown in a snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmConversationScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: DmConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }

    // Anchor for prepending older messages: the first visible message keeps
    // its position when the list grows at the top.
    var anchor by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(state.error) {
        // The error stays in the state: an empty thread shows it as well, and
        // the next attempt clears it.
        state.error?.let { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(state.messages.size) {
        val restore = anchor
        if (restore != null) {
            // Older messages were prepended: keep the reading position.
            val index = state.messages.indexOfFirst { it.id == restore.first }
            if (index >= 0) listState.scrollToItem(index, restore.second)
        } else if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    // A load that brought nothing (already at the oldest message) leaves the
    // list untouched — drop the anchor so the next message scrolls normally.
    LaunchedEffect(state.loadingMore) {
        if (!state.loadingMore) anchor = null
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            state.messages.size > PAGE_SIZE && first <= 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val id = info?.index?.let { state.messages.getOrNull(it)?.id }
            anchor = id?.let { it to (info.offset) }
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.otherName.ifBlank { "私信" },
                        modifier = Modifier.clickable {
                            if (state.otherId.isNotBlank()) onOpenUser(state.otherId)
                        },
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
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= MAX_LENGTH) input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么…") },
                        maxLines = 4,
                        supportingText = if (input.length >= MAX_LENGTH - 20) {
                            {
                                Text(
                                    text = "${input.length}/$MAX_LENGTH",
                                    textAlign = TextAlign.End,
                                    color = if (input.length >= MAX_LENGTH) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    )
                    IconButton(
                        onClick = {
                            viewModel.send(input) { input = "" }
                        },
                        enabled = input.isNotBlank() && !state.sending,
                    ) {
                        if (state.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }

                state.messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.error == null) "还没有消息" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.loadingMore) {
                        item(key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        DmBubble(message = message)
                    }
                }
            }
        }
    }
}

@Composable
private fun DmBubble(message: DmUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (message.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                )
            }
        }
    }
}

private const val PAGE_SIZE = 30
