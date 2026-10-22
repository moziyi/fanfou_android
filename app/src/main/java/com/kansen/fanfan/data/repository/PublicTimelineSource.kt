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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * The parents of the replies on screen, so a card can show its quote.
     * A refresh and a pull can run at the same time, so the map is guarded.
     */
    private val parents = LinkedHashMap<String, StatusUi>()
    private val parentsLock = Mutex()

    val items: StateFlow<List<StatusUi>> = state.asStateFlow()

    /** The oldest entry on screen, for the `max_id` of the next page. */
    fun oldestId(): String? = state.value.lastOrNull()?.id?.takeIf { it.isNotBlank() }

    /** Replaces the list with a fresh page. */
    suspend fun replace(page: List<StatusDto>) {
        state.value = build(page)
        // The parents of the previous list are dropped: one of them may have
        // been deleted on the server, and a stale quote is worse than none.
        parentsLock.withLock { parents.clear() }
    }

    /**
     * Appends an older page. Returns false when the page adds nothing, which
     * means the end of the list: the server would send the same page again.
     */
    suspend fun append(page: List<StatusDto>): Boolean {
        if (page.isEmpty()) return false
        val current = state.value
        val known = current.map { it.id }.toSet()
        val fresh = page.filter { it.id != null && it.id !in known }
        if (fresh.isEmpty()) return false
        // The list starts with the newest entry, so the head is what is kept.
        state.value = merge(current, build(fresh), MAX_ITEMS)
        return true
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
        val found = parentsLock.withLock {
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
            replyIds.mapNotNull { id -> parents[id]?.let { id to it } }.toMap()
        }
        return items.map { item ->
            val parent = item.inReplyToStatusId?.let(found::get)
            if (parent == null) item else item.copy(replied = parent)
        }
    }

    private companion object {
        /** The list is bounded; the oldest entries fall out of it. */
        const val MAX_ITEMS = 300
        const val MAX_PARENTS = 100
    }
}

/**
 * Appends a page to the list: the entries that are already on screen are
 * dropped, and the list keeps its newest entries.
 */
internal fun merge(
    current: List<StatusUi>,
    fresh: List<StatusUi>,
    maxItems: Int,
): List<StatusUi> {
    val known = current.map { it.id }.toSet()
    val unique = fresh.filter { it.id !in known }
    return (current + unique).take(maxItems)
}
