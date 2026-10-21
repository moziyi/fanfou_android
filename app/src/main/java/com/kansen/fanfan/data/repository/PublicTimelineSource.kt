package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.dto.StatusDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The public timeline ("随便看看") in memory only.
 *
 * Nothing of it is cached: the entries never reach the local database, so
 * they do not survive the process. The price is that the tab needs the
 * network; the gain is that the cache stays free of the noise of a feed that
 * is only interesting while it is fresh.
 *
 * The favorites of these entries are the exception: the local override table
 * is written as usual, so a favorite stays a favorite when the status shows
 * up in another timeline.
 */
@Singleton
class PublicTimelineSource @Inject constructor(
    private val statusDao: StatusDao,
    private val replyTargets: ReplyTargetResolver,
) {

    private val state = MutableStateFlow<List<StatusUi>>(emptyList())

    /** The parents of the replies on screen, so a card can show its quote. */
    private val parents = LinkedHashMap<String, StatusUi>()

    val items: StateFlow<List<StatusUi>> = state.asStateFlow()

    /** The oldest entry on screen, for the `max_id` of the next page. */
    fun oldestId(): String? = state.value.lastOrNull()?.id?.takeIf { it.isNotBlank() }

    /** Replaces the list with a fresh page. */
    suspend fun replace(page: List<StatusDto>) {
        state.value = build(page)
    }

    /** Appends an older page. */
    suspend fun append(page: List<StatusDto>) {
        if (page.isEmpty()) return
        val current = state.value
        val known = current.map { it.id }.toSet()
        val fresh = page.filter { it.id != null && it.id !in known }
        if (fresh.isEmpty()) return
        // The list starts with the newest entry, so the head is what is kept.
        state.value = (current + build(fresh)).take(MAX_ITEMS)
    }

    /** Records a favorite state that was changed somewhere else. */
    fun applyFavorite(id: String, favorited: Boolean) {
        val current = state.value
        if (current.none { it.id == id }) return
        state.value = current.map { if (it.id == id) it.copy(favorited = favorited) else it }
    }

    private suspend fun build(page: List<StatusDto>): List<StatusUi> {
        val items = page.mapNotNull { dto ->
            val id = dto.id ?: return@mapNotNull null
            dto.toUi().copy(id = id)
        }
        if (items.isEmpty()) return emptyList()
        val overrides = statusDao.favoriteOverrides(items.map { it.id })
            .associate { it.statusId to it.favorited }
        val withFavorites = items.map { it.copy(favorited = overrides[it.id] ?: it.favorited) }
        return attachParents(withFavorites)
    }

    /** Puts the quoted parent of a reply on the card. */
    private suspend fun attachParents(items: List<StatusUi>): List<StatusUi> {
        val replyIds = items.mapNotNull { it.inReplyToStatusId }.distinct()
        if (replyIds.isEmpty()) return items
        val missing = replyIds.filter { it !in parents }
        if (missing.isNotEmpty()) {
            // A parent that is cached for another timeline is read from the
            // database — that is a read, not a cache of this feed.
            val known = statusDao.statusByIds(missing)
                .associate { it.status.id to StatusMapper.toUi(it) }
            val absent = missing.filter { it !in known }
            parents.putAll(known)
            parents.putAll(replyTargets.fetchParents(absent))
            while (parents.size > MAX_PARENTS) {
                val oldest = parents.keys.firstOrNull() ?: break
                parents.remove(oldest)
            }
        }
        return items.map { item ->
            val parent = item.inReplyToStatusId?.let { parents[it] }
            if (parent == null) item else item.copy(replied = parent)
        }
    }

    private companion object {
        /** The list is bounded; the oldest entries fall out of it. */
        const val MAX_ITEMS = 300
        const val MAX_PARENTS = 100
    }
}
