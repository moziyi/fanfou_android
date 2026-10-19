package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiTimeTest {

    @Test
    fun `parses the documented english timestamp format`() {
        assertEquals(
            1320822921000L,
            ApiTime.parseEpochMillis("Wed Nov 09 07:15:21 +0000 2011"),
        )
    }

    @Test
    fun `parses non-utc offsets`() {
        // 2011-11-09 15:15:21 +0800 == 07:15:21 UTC
        assertEquals(
            1320822921000L,
            ApiTime.parseEpochMillis("Wed Nov 09 15:15:21 +0800 2011"),
        )
    }

    @Test
    fun `unparseable input falls back to zero`() {
        assertEquals(0L, ApiTime.parseEpochMillis(null))
        assertEquals(0L, ApiTime.parseEpochMillis(""))
        assertEquals(0L, ApiTime.parseEpochMillis("not a date"))
    }

    @Test
    fun `relative labels cover the common buckets`() {
        val now = 10_000_000_000L
        assertEquals("刚刚", ApiTime.relative(now - 30_000, now))
        assertEquals("5分钟前", ApiTime.relative(now - TimeUnit.MINUTES.toMillis(5), now))
        assertEquals("3小时前", ApiTime.relative(now - TimeUnit.HOURS.toMillis(3), now))
        assertEquals("2天前", ApiTime.relative(now - TimeUnit.DAYS.toMillis(2), now))
        assertEquals("", ApiTime.relative(0, now))
    }
}

class SourceTextTest {

    @Test
    fun `plain text source is kept as-is`() {
        assertEquals("网页", SourceText.name("网页"))
        assertNull(SourceText.url("网页"))
    }

    @Test
    fun `html source yields label and link`() {
        val source = """<a href="http://fanfou.com" rel="nofollow">网页</a>"""
        assertEquals("网页", SourceText.name(source))
        assertEquals("http://fanfou.com", SourceText.url(source))
    }

    @Test
    fun `blank source yields null`() {
        assertNull(SourceText.name(null))
        assertNull(SourceText.name("   "))
    }
}
