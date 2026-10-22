package com.kansen.fanfan.ui.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.common.userMessage
import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.model.UserProfileUi
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.data.repository.FavoritesRepository
import com.kansen.fanfan.data.repository.StatusRepository

/** One paged people list (friends or followers). */
data class PeoplePage(
    val items: List<UserProfileUi> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val loadedOnce: Boolean = false,
    val page: Int = 0,
    /** Set when the request failed — e.g. a protected account hides its list. */
    val error: String? = null,
)

/** A page of statuses, like [PeoplePage] but for statuses. */
data class StatusPage(
    val items: List<StatusUi> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val loadedOnce: Boolean = false,
    val page: Int = 0,
    val error: String? = null,
)

data class UserUiState(
    val loading: Boolean = true,
    val profile: UserProfileUi? = null,
    val isSelf: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val followingBusy: Boolean = false,
    val statuses: List<StatusUi> = emptyList(),
    val statusesLoading: Boolean = false,
    val statusesLoadedOnce: Boolean = false,
    val statusesEndReached: Boolean = false,
    /** Set when the status list could not be loaded (e.g. a protected account). */
    val statusesError: String? = null,
    val friends: PeoplePage = PeoplePage(),
    val followers: PeoplePage = PeoplePage(),
    /** The favorites of the logged-in user; only on one's own profile. */
    val favorites: StatusPage = StatusPage(),
)

@HiltViewModel
class UserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val favoritesRepository: FavoritesRepository,
    private val statusRepository: StatusRepository,
    private val statusesApi: StatusesApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val self = tokenStore.session.first()?.user?.id
            _uiState.update { it.copy(isSelf = self != null && self == userId) }
        }
        loadProfile()
        loadStatuses()
    }

    fun loadProfile() {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            userRepository.profile(userId)
                .onSuccess { profile ->
                    // users/show answers 200 with a partial object for some
                    // protected accounts; treat a missing id as a failure.
                    if (profile.hasProfile) {
                        _uiState.update { it.copy(loading = false, profile = profile) }
                    } else {
                        fallback("用户不存在")
                    }
                }
                .onFailure { t ->
                    val cached = userRepository.cachedProfile(userId)
                    if (cached != null) {
                        _uiState.update {
                            it.copy(loading = false, profile = cached, error = "离线：显示的是缓存资料")
                        }
                    } else {
                        fallback(t.userMessage())
                    }
                }
        }
    }

    private fun fallback(message: String) {
        _uiState.update { it.copy(loading = false, error = message) }
    }

    fun toggleFollow() {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.followingBusy || state.isSelf) return
        val target = !profile.following
        viewModelScope.launch {
            _uiState.update {
                it.copy(followingBusy = true, profile = it.profile?.copy(following = target))
            }
            userRepository.setFollowing(profile.id, target)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            followingBusy = false,
                            profile = if (updated.hasProfile) updated else it.profile,
                            message = if (target) "已关注" else "已取消关注",
                        )
                    }
                }
                .onFailure { t ->
                    val message = t.userMessage()
                    // A protected account answers the follow request with
                    // 「已向 X 发出关注请求，请等待确认」 — the request exists, so
                    // the button switches to 「已申请」 instead of 「关注」.
                    val pending = message.contains("关注请求")
                    _uiState.update {
                        it.copy(
                            followingBusy = false,
                            profile = it.profile?.copy(
                                following = if (pending) false else !target,
                                followRequested = pending || it.profile?.followRequested == true,
                            ),
                            message = message,
                        )
                    }
                }
        }
    }

    /** Called when a people tab becomes visible for the first time. */
    fun ensurePeopleLoaded(tab: Int) {
        when (tab) {
            1 -> if (!_uiState.value.friends.loadedOnce) loadPeople(1)
            2 -> if (!_uiState.value.followers.loadedOnce) loadPeople(2)
            // The favorites can be changed on the web or in another tab, so
            // every visit to the tab asks the server again.
            3 -> if (_uiState.value.favorites.loadedOnce) refreshFavorites() else loadFavorites()
        }
    }

    fun loadStatuses() {
        val state = _uiState.value
        if (state.statusesLoading || state.statusesEndReached) return
        _uiState.update { it.copy(statusesLoading = true) }
        val maxId = state.statuses.lastOrNull()?.id
        viewModelScope.launch {
            runCatchingCancellable { statusesApi.userTimeline(id = userId, maxId = maxId, count = PAGE_SIZE) }
                .onSuccess { page ->
                    val mapped = page.map { it.toUi() }
                    _uiState.update {
                        it.copy(
                            statuses = (it.statuses + mapped).distinctBy { s -> s.id },
                            statusesLoading = false,
                            statusesLoadedOnce = true,
                            statusesEndReached = page.isEmpty(),
                            statusesError = null,
                        )
                    }
                }
                .onFailure { t ->
                    // A protected account answers 403 for users we do not
                    // follow. Stop paging here: without this the footer keeps
                    // requesting and the spinner never settles.
                    _uiState.update {
                        it.copy(
                            statusesLoading = false,
                            statusesLoadedOnce = true,
                            statusesEndReached = true,
                            statusesError = t.userMessage(),
                        )
                    }
                }
        }
    }

    fun loadMoreStatuses() = loadStatuses()

    /** Manual retry after a failed list load. */
    fun retryStatuses() {
        _uiState.update { it.copy(statusesEndReached = false, statusesError = null) }
        loadStatuses()
    }

    fun loadPeople(tab: Int) {
        val current = if (tab == 1) _uiState.value.friends else _uiState.value.followers
        if (current.loading || current.endReached) return
        updatePeople(tab) { it.copy(loading = true) }
        val nextPage = current.page + 1
        viewModelScope.launch {
            val result = if (tab == 1) {
                userRepository.friends(userId, nextPage)
            } else {
                userRepository.followers(userId, nextPage)
            }
            result
                .onSuccess { page ->
                    updatePeople(tab) {
                        it.copy(
                            items = (it.items + page).distinctBy { u -> u.id },
                            loading = false,
                            loadedOnce = true,
                            endReached = page.isEmpty(),
                            page = nextPage,
                        )
                    }
                }
                .onFailure { t ->
                    // A protected account answers 403 for people lists; show
                    // that reason instead of an empty list, and stop paging so
                    // the footer does not retry in a loop. The error body can
                    // only be read once, so the message is parsed once here.
                    val message = t.userMessage()
                    updatePeople(tab) {
                        it.copy(loading = false, loadedOnce = true, endReached = true, error = message)
                    }
                    _uiState.update { it.copy(message = message) }
                }
        }
    }

    fun loadMorePeople(tab: Int) = loadPeople(tab)

    /** Reloads the favorites from the first page. */
    fun refreshFavorites() {
        _uiState.update {
            it.copy(favorites = it.favorites.copy(page = 0, endReached = false, error = null))
        }
        loadFavorites()
    }

    /** Loads the favorites of the logged-in user. */
    fun loadFavorites() {
        val current = _uiState.value.favorites
        if (current.loading || current.endReached) return
        _uiState.update { it.copy(favorites = current.copy(loading = true, error = null)) }
        val nextPage = current.page + 1
        viewModelScope.launch {
            val result = runCatchingCancellable {
                favoritesRepository.list(page = nextPage, count = FavoritesRepository.PAGE_SIZE)
            }
            result
                .onSuccess { page ->
                    _uiState.update { state ->
                        state.copy(
                            favorites = state.favorites.copy(
                                // The first page replaces the list, an older
                                // page is appended to it.
                                items = if (nextPage == 1) {
                                    page
                                } else {
                                    (state.favorites.items + page).distinctBy { s -> s.id }
                                },
                                loading = false,
                                loadedOnce = true,
                                endReached = page.size < FavoritesRepository.PAGE_SIZE,
                                page = nextPage,
                            ),
                        )
                    }
                }
                .onFailure { t ->
                    val message = t.userMessage()
                    _uiState.update { state ->
                        state.copy(
                            favorites = state.favorites.copy(
                                loading = false,
                                loadedOnce = true,
                                endReached = true,
                                error = message,
                            ),
                        )
                    }
                }
        }
    }

    /** Manual retry after a failed favorites load. */
    fun retryFavorites() {
        _uiState.update {
            it.copy(favorites = it.favorites.copy(endReached = false, error = null, page = 0))
        }
        loadFavorites()
    }

    /**
     * Removes a status from the favorites. The server is asked first: a
     * failure keeps the entry, so the list never loses a favorite that the
     * server still has.
     */
    fun removeFavorite(status: StatusUi) {
        viewModelScope.launch {
            statusRepository.toggleFavorite(status)
                .onSuccess { favorited ->
                    if (favorited) return@onSuccess
                    _uiState.update { state ->
                        state.copy(
                            favorites = state.favorites.copy(
                                items = state.favorites.items.filterNot { it.id == status.id },
                            ),
                            // The count in the header belongs to the profile
                            // that was loaded before; it follows the change.
                            profile = state.profile?.let { profile ->
                                profile.copy(
                                    favouritesCount = ((profile.favouritesCount ?: 0) - 1)
                                        .coerceAtLeast(0),
                                )
                            },
                        )
                    }
                }
                .onFailure { t -> _uiState.update { it.copy(message = t.userMessage()) } }
        }
    }

    /** Manual retry after a failed people list load. */
    fun retryPeople(tab: Int) {
        updatePeople(tab) { it.copy(endReached = false, error = null) }
        loadPeople(tab)
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun updatePeople(tab: Int, transform: (PeoplePage) -> PeoplePage) {
        _uiState.update { state ->
            if (tab == 1) state.copy(friends = transform(state.friends))
            else state.copy(followers = transform(state.followers))
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
