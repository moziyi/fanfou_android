package com.kansen.fanfan.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A problem with the picture the user picked must not be reported as a
 * network problem.
 */
class UserMessageTest {

    @Test
    fun `a picture problem keeps its own message`() {
        val message = PictureException("图片太大（超过 20 MB）").userMessage()

        assertEquals("图片太大（超过 20 MB）", message)
    }

    @Test
    fun `a network problem still says so`() {
        val message = IOException("Unable to resolve host").userMessage()

        assertTrue(message.contains("网络"))
    }
}
