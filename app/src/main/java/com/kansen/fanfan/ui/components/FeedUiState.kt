package com.kansen.fanfan.ui.components

/** Shared state of a paged status feed (home timelines and @我). */
data class FeedUiState(
    /** First load of a timeline: shows the centered spinner, never the pull indicator. */
    val initialLoading: Boolean = false,
    /** User-initiated pull: drives the pull-to-refresh indicator only. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val initialLoaded: Boolean = false,
    val error: String? = null,
)
