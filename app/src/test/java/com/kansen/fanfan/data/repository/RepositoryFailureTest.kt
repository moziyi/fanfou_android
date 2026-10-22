package com.kansen.fanfan.data.repository

import com.kansen.fanfan.core.network.api.FavoritesApi
import com.kansen.fanfan.core.network.api.SearchApi
import com.kansen.fanfan.core.network.dto.StatusDto
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A failing request must reach the screen as a failure. A repository that
 * swallows the error and answers with an empty list makes "no network" look
 * like "nothing found" (measured on the device 2026-10-22).
 */
class RepositoryFailureTest {

    private class FailingSearchApi : SearchApi {
        override suspend fun publicTimeline(
            query: String,
            sinceId: String?,
            maxId: String?,
            count: Int,
            mode: String,
        ): List<StatusDto> = throw IOException("网络连接失败，请检查网络后重试")
    }

    private class FailingFavoritesApi : FavoritesApi {
        override suspend fun create(id: String): StatusDto = throw IOException("network")
        override suspend fun destroy(id: String): StatusDto = throw IOException("network")
        override suspend fun list(count: Int, page: Int): List<StatusDto> =
            throw IOException("网络连接失败，请检查网络后重试")
    }

    @Test
    fun `a failed search is not an empty result`() {
        val repository = SearchRepository(FailingSearchApi())

        assertThrows(IOException::class.java) {
            runBlocking { repository.search("linux") }
        }
    }

    @Test
    fun `a failed favorites list is not an empty list`() {
        val repository = FavoritesRepository(FailingFavoritesApi())

        assertThrows(IOException::class.java) {
            runBlocking { repository.list() }
        }
    }

    @Test
    fun `a successful search is the list of the server`() {
        val repository = SearchRepository(object : SearchApi {
            override suspend fun publicTimeline(
                query: String,
                sinceId: String?,
                maxId: String?,
                count: Int,
                mode: String,
            ): List<StatusDto> = listOf(statusDto("a"), statusDto("b"))
        })

        val results = runBlocking { repository.search("linux") }

        assertEquals(listOf("a", "b"), results.map { it.id })
    }

    private fun statusDto(id: String) = StatusDto(
        id = id,
        rawid = 1,
        created_at = "2026-10-22T10:00:00",
        text = "hello",
        favorited = false,
    )
}
