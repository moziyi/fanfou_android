package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.model.SearchHighlight
import com.kansen.fanfan.core.model.StatusMapper
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.api.SearchApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The search of the site. The results are not cached: a search is a question
 * with a moment, not a feed to keep.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val api: SearchApi,
) {

    /**
     * Searches for the given words. Several words are separated by a `|`,
     * which is what the server expects.
     */
    suspend fun search(
        query: String,
        maxId: String? = null,
        count: Int = PAGE_SIZE,
    ): List<StatusUi> = runCatchingCancellable {
        api.publicTimeline(query = query.trim(), maxId = maxId, count = count)
            .mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                val item = dto.toUi()
                item.copy(
                    id = id,
                    text = SearchHighlight.plain(item.text),
                    highlight = SearchHighlight.parse(item.text),
                )
            }
    }.getOrThrow()

    companion object {
        const val PAGE_SIZE = 30
    }
}
