package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.common.runCatchingCancellable
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.model.StatusUi
import com.kansen.fanfan.core.network.api.FavoritesApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The favorites of the logged-in user.
 *
 * The list is private, so it is only shown on one's own profile. It is not
 * cached: the favorites change rarely, but they change *somewhere* (on the
 * web), and a stale list is worse than a short wait.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val api: FavoritesApi,
) {

    suspend fun list(page: Int = 1, count: Int = PAGE_SIZE): List<StatusUi> =
        runCatchingCancellable {
            api.list(count = count, page = page).mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                dto.toUi().copy(id = id, favorited = true)
            }
        }.getOrDefault(emptyList())

    companion object {
        const val PAGE_SIZE = 30
    }
}
