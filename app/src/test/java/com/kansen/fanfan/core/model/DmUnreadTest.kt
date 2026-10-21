package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unread rule of the 私信 tab. The server flag `new_conv` cannot be used
 * (it reports the other side's read state), so the rule works on the last
 * message plus the local read marker.
 */
class DmUnreadTest {

    private fun conversation(
        otherId: String,
        lastMessageId: String,
        lastFromOther: Boolean,
    ) = DmConversationUi(
        otherId = otherId,
        otherName = otherId,
        otherAvatarUrl = null,
        lastText = "hello",
        lastCreatedAt = 0L,
        messageCount = 1,
        lastMessageId = lastMessageId,
        lastFromOther = lastFromOther,
    )

    @Test
    fun `a message from the other side we have not seen is unread`() {
        val conversations = listOf(conversation("corday", "1001", lastFromOther = true))

        val unread = DmUnread.evaluate(conversations, readMarkers = emptyMap())

        assertEquals(setOf("corday"), unread)
    }

    @Test
    fun `the message we have already seen is read`() {
        val conversations = listOf(conversation("corday", "1001", lastFromOther = true))

        val unread = DmUnread.evaluate(conversations, readMarkers = mapOf("corday" to "1001"))

        assertTrue(unread.isEmpty())
    }

    @Test
    fun `a conversation we wrote last is not unread`() {
        val conversations = listOf(conversation("corday", "1002", lastFromOther = false))

        val unread = DmUnread.evaluate(conversations, readMarkers = emptyMap())

        assertTrue(unread.isEmpty())
    }

    @Test
    fun `a new message after the marker is unread again`() {
        val conversations = listOf(conversation("corday", "1003", lastFromOther = true))

        val unread = DmUnread.evaluate(conversations, readMarkers = mapOf("corday" to "1001"))

        assertEquals(setOf("corday"), unread)
    }

    @Test
    fun `only the conversations that need attention are reported`() {
        val conversations = listOf(
            conversation("corday", "1001", lastFromOther = true),
            conversation("fanfou", "13990207", lastFromOther = true),
            conversation("zoeysa", "1222826", lastFromOther = false),
        )

        val unread = DmUnread.evaluate(
            conversations,
            readMarkers = mapOf("corday" to "1001", "fanfou" to "13990207"),
        )

        assertTrue(unread.isEmpty())
    }

    @Test
    fun `a conversation without a last message id is ignored`() {
        val conversations = listOf(conversation("corday", "", lastFromOther = true))

        val unread = DmUnread.evaluate(conversations, readMarkers = emptyMap())

        assertTrue(unread.isEmpty())
    }
}
