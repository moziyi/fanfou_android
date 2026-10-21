package com.kansen.fanfan.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kansen.fanfan.data.repository.StatusRepository

data class SearchUiState(
    val query: String = "",
    val results: List<StatusUi> = emptyList(),
    val searching: Boolean = false,
    val loadingMore: Boolean = false,
    /** No older results are left to load. */
    val endReached: Boolean = false,
    /** A search was made, so the empty list means "nothing found". */
    val searched: Boolean = false,
    val error: String? = null,
)

/**
 * The search of the site. The results live in the state of this view model
 * only: a search is a question with a moment, not a feed to keep.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Runs the search of the current query. */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), searched = false, error = null) }
            return
        }
        if (_uiState.value.searching) return
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, error = null) }
            val result = runCatchingCancellable { searchRepository.search(query) }
            result
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            results = results,
                            searching = false,
                            searched = true,
                            endReached = results.size < SearchRepository.PAGE_SIZE,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(searching = false, searched = true, error = t.userMessage())
                    }
                }
        }
    }

    /** Loads the results that come before the oldest one on screen. */
    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.endReached || state.searching) return
        if (state.results.isEmpty()) return
        val query = state.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true, error = null) }
            val oldest = state.results.last().id
            val result = runCatchingCancellable {
                searchRepository.search(query, maxId = oldest)
            }
            result
                .onSuccess { older ->
                    val known = state.results.map { it.id }.toSet()
                    val fresh = older.filter { it.id !in known }
                    _uiState.update {
                        it.copy(
                            results = (state.results + fresh),
                            loadingMore = false,
                            endReached = older.size < SearchRepository.PAGE_SIZE,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(loadingMore = false, error = t.userMessage()) }
                }
        }
    }

    /** Flips the favorite state of a result. */
    fun toggleFavorite(status: StatusUi) {
        viewModelScope.launch {
            statusRepository.toggleFavorite(status)
                .onSuccess { favorited ->
                    _uiState.update { state ->
                        state.copy(
                            results = state.results.map {
                                if (it.id == status.id) it.copy(favorited = favorited) else it
                            },
                        )
                    }
                }
                .onFailure { t -> _uiState.update { it.copy(error = t.userMessage()) } }
        }
    }

    /** Empties the field and the results. */
    fun clear() {
        _uiState.update {
            it.copy(query = "", results = emptyList(), searched = false, error = null, endReached = false)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
