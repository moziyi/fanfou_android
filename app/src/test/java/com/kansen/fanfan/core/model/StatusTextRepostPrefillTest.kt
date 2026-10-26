package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repost prefill: `转@<author> <original>`, copied verbatim into the one
 * composer — exactly what the official web client does with its
 * /home?status=…&repost_status_id=… 转发 links. No truncation at prefill
 * time (the official count goes negative); the cut happens at send time.
 */
class StatusTextRepostPrefillTest {

    @Test
    fun prefillKeepsAuthorSpaceAndFullOriginalWhenItFits() {
        assertEquals(
            "转@咕咕咕Y 饱满的果实！",
            StatusText.repostPrefill("咕咕咕Y", "饱满的果实！"),
        )
    }

    @Test
    fun prefillCopiesAnOverlongOriginalWhole() {
        // The official web prefills the full text and only shows -8 in the
        // counter; the cut is a send-time concern, not a prefill concern.
        val overlong = "的的".repeat(70) + "尾巴"
        val result = StatusText.repostPrefill("Fallacy", overlong)!!
        assertEquals("转@Fallacy $overlong", result)
        assertTrue(!StatusText.fits(result))
    }

    @Test
    fun emptyOriginalBecomesJustTheMention() {
        assertEquals("转@汾河谷", StatusText.repostPrefill("汾河谷", ""))
        assertEquals("转@汾河谷", StatusText.repostPrefill("汾河谷", "  "))
    }

    @Test
    fun noAuthorNameMeansNoPrefill() {
        assertNull(StatusText.repostPrefill(null, "饱满的果实！"))
        assertNull(StatusText.repostPrefill("", "饱满的果实！"))
        assertNull(StatusText.repostPrefill("  ", "饱满的果实！"))
    }

    @Test
    fun truncateCutsByCodePointsAtTheLimit() {
        assertEquals("的的".repeat(65), StatusText.truncate("的的".repeat(70), 130))
        assertEquals(130, StatusText.length(StatusText.truncate("的的".repeat(70), 130)))
    }

    @Test
    fun truncateWholeTextWhenItAlreadyFits() {
        val text = "饱满的果实！"
        assertEquals(text, StatusText.truncate(text, 140))
        assertEquals("", StatusText.truncate(text, 0))
    }

    @Test
    fun truncateNeverSplitsASurrogatePair() {
        // 133 code points requested out of an emoji-led run: all 70 emoji
        // (1 code point each) survive, then x's fill to the limit; the cut
        // lands inside the x run, never inside a pair.
        val text = "😀".repeat(70) + "x".repeat(200)
        val result = StatusText.truncate(text, 133)
        assertEquals("😀".repeat(70) + "x".repeat(63), result)
        assertEquals(133, StatusText.length(result))
    }
}
