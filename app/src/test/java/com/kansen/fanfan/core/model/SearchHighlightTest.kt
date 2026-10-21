package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search results carry the search words as escaped markup, live-verified
 * 2026-10-21: `&lt;strong&gt;成都&lt;/strong&gt;`.
 */
class SearchHighlightTest {

    @Test
    fun `the escaped tags become bold parts`() {
        val spans = SearchHighlight.parse("花小雕万圣节要来&lt;strong&gt;成都&lt;/strong&gt; 我周围吧啦")

        assertEquals(3, spans.size)
        assertEquals("花小雕万圣节要来", spans[0].text)
        assertTrue(!spans[0].bold)
        assertEquals("成都", spans[1].text)
        assertTrue(spans[1].bold)
        assertEquals(" 我周围吧啦", spans[2].text)
        assertTrue(!spans[2].bold)
    }

    @Test
    fun `the plain text has no markup left`() {
        val text = SearchHighlight.plain("来&lt;strong&gt;成都&lt;/strong&gt; 玩")

        assertEquals("来成都 玩", text)
    }

    @Test
    fun `a text without a match is one plain part`() {
        val spans = SearchHighlight.parse("没有关键词的消息")

        assertEquals(1, spans.size)
        assertEquals("没有关键词的消息", spans[0].text)
        assertTrue(!spans[0].bold)
    }

    @Test
    fun `the other entities are decoded as well`() {
        val text = SearchHighlight.plain("a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39;")

        assertEquals("a & b <c> \"d\" 'e'", text)
    }

    @Test
    fun `several matches are all marked`() {
        val spans = SearchHighlight.parse("&lt;strong&gt;成都&lt;/strong&gt;和&lt;strong&gt;重庆&lt;/strong&gt;")

        assertEquals(3, spans.size)
        assertEquals("成都", spans[0].text)
        assertTrue(spans[0].bold)
        assertEquals("和", spans[1].text)
        assertTrue(!spans[1].bold)
        assertEquals("重庆", spans[2].text)
        assertTrue(spans[2].bold)
    }

    @Test
    fun `an empty text has no parts`() {
        assertTrue(SearchHighlight.parse("").isEmpty())
    }
}
