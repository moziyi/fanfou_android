package com.kansen.fanfan.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kansen.fanfan.core.model.DraftUi

/**
 * The draft box. A row opens the composer the draft belongs to — the text is
 * restored there, and the picture (if there was one) is not, because drafts
 * keep the text only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onOpenDraft: (DraftUi) -> Unit,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // A new draft is sorted to the top, but the list keeps the scroll
    // anchored to the item that was visible — so the new draft would land
    // above the viewport. Follow the list instead.
    LaunchedEffect(drafts.size) {
        if (drafts.isNotEmpty()) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("草稿箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有草稿",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            // The scaffold's padding keeps the first row out from under the
            // top bar.
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(drafts, key = { it.id }) { draft ->
                DraftRow(
                    draft = draft,
                    onClick = { onOpenDraft(draft) },
                    onDelete = { viewModel.delete(draft.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun DraftRow(
    draft: DraftUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.contextLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = draft.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = draft.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除草稿",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
