package com.kansen.fanfan.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.ui.components.StatusFeed

/**
 * The search of the site. The field is the top bar, the results are below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenStatus: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (StatusUi) -> Unit,
    onRepost: (StatusUi) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // The keyboard is only wanted when there is nothing to look at yet.
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focusRequester.requestFocus()
    }

    // The error stays in the state: the snackbar is gone after a few seconds,
    // and the empty state has to say that the search failed instead of
    // pretending that nothing was found. The next search clears it.
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索消息") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                viewModel.search()
                            },
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clear()
                            focusRequester.requestFocus()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatusFeed(
                statuses = state.results,
                refreshing = state.searching,
                initialLoaded = true,
                loadingMore = state.loadingMore,
                endReached = state.endReached,
                emptyText = when {
                    state.searching -> "搜索中…"
                    state.error != null -> "搜索失败，请检查网络后重试"
                    state.searched -> "没有找到相关消息"
                    else -> "输入关键词搜索全站消息"
                },
                snackbar = snackbar,
                onRefresh = { viewModel.search() },
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
