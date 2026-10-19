package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextTest {

    @Test
    fun `counts cjk characters by code point`() {
        assertEquals(6, StatusText.length("你好世界你好"))
        assertEquals(134, StatusText.remaining("你好世界你好"))
    }

    @Test
    fun `counts emoji as single code point`() {
        assertEquals(1, StatusText.length("👍"))
        // Family emoji is several code points (ZWJ sequence); must not crash.
        assertTrue(StatusText.length("👨‍👩‍👧‍👦") >= 1)
    }

    @Test
    fun `limit boundary is inclusive`() {
        val exactly = "a".repeat(StatusText.MAX_LENGTH)
        assertTrue(StatusText.fits(exactly))
        assertEquals(0, StatusText.remaining(exactly))
        assertFalse(StatusText.fits(exactly + "a"))
    }
}
