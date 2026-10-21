package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.database.dao.UserDao
import com.kansen.fanfan.core.model.UserProfileUi
import com.kansen.fanfan.core.model.toProfileUi
import com.kansen.fanfan.core.network.api.UsersApi
import com.kansen.fanfan.core.network.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton
import com.kansen.fanfan.core.common.runCatchingCancellable

/**
 * Read path for user pages and relationship lists. Profiles are deliberately
 * not cached in Room: `following` flips as soon as the user taps the button,
 * and a stale snapshot would offer the wrong action. The raw user snapshot in
 * the `user` table is only a fallback header when the network is unavailable.
 */
@Singleton
class UserRepository @Inject constructor(
    private val api: UsersApi,
    private val userDao: UserDao,
) {

    suspend fun profile(id: String): Result<UserProfileUi> = runCatchingCancellable {
        api.show(id).toProfileUi()
    }

    suspend fun cachedProfile(id: String): UserProfileUi? = runCatchingCancellable {
        userDao.byId(id)?.let { entity ->
            JsonCodec.json.decodeFromString<UserDto>(entity.rawJson).toProfileUi()
        }
    }.getOrNull()

    suspend fun friends(id: String?, page: Int): Result<List<UserProfileUi>> = runCatchingCancellable {
        api.friends(id = id, page = page).map { it.toProfileUi() }
    }

    suspend fun followers(id: String?, page: Int): Result<List<UserProfileUi>> = runCatchingCancellable {
        api.followers(id = id, page = page).map { it.toProfileUi() }
    }

    /** Follow/unfollow; the response carries the new `following` state. */
    suspend fun setFollowing(id: String, follow: Boolean): Result<UserProfileUi> = runCatchingCancellable {
        val dto = if (follow) api.follow(id) else api.unfollow(id)
        dto.toProfileUi()
    }
}
