package com.kansen.fanfan.core.network.api

import com.kansen.fanfan.core.network.dto.StatusDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The search of the whole site.
 *
 * Live-verified 2026-10-21: `page` is ignored by the server, so the older
 * results are fetched with `max_id` like every other timeline. The results
 * carry the search words as markup (`&lt;strong&gt;word&lt;/strong&gt;`), which
 * `SearchHighlight` decodes.
 */
interface SearchApi {

    @GET("search/public_timeline.json")
    suspend fun publicTimeline(
        @Query("q") query: String,
        @Query("since_id") sinceId: String? = null,
        @Query("max_id") maxId: String? = null,
        @Query("count") count: Int = 30,
        @Query("mode") mode: String = "lite",
    ): List<StatusDto>
}
