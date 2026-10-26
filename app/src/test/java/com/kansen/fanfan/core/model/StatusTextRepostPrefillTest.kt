package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repost prefill: `转@<author> <original>`, truncated so the whole text
 * still fits the 140-point limit, exactly the way the official client's own
 * repost chains look (golden data in StatusQuoteDisplayTest).
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
    fun overlongOriginalIsTruncatedToTheLimitWithoutAMarker() {
        val original = "ゅ".repeat(80) + " Ordinary story".repeat(10)
        val result = StatusText.repostPrefill("Fallacy", original)!!
        assertEquals(StatusText.MAX_LENGTH, StatusText.length(result))
        assertTrue(result.startsWith("转@Fallacy "))
    }

    @Test
    fun truncationNeverSplitsASurrogatePair() {
        // "转@咕咕咕Y " is 7 code points, leaving 133 for the original;
        // the cut runs through a run of emoji, and the pair stays whole.
        val original = "😀".repeat(70) + "x".repeat(200)
        // budget = 140 - 7 points of "转@咕咕咕Y " → 133: all 70 emoji fit,
        // the cut lands in the x run on a full code-point boundary.
        val result = StatusText.repostPrefill("咕咕咕Y", original)!!
        assertEquals("转@咕咕咕Y " + "😀".repeat(70) + "x".repeat(63), result)
        assertEquals(StatusText.MAX_LENGTH, StatusText.length(result))
    }

    @Test
    fun aCodePointThatFitsOnlyWithItsPairIsDroppedAtTheBoundary() {
        // "转@咕咕咕Y " leaves 133 points; 133 x's fit, the emoji would be #134
        // and must not appear alone in half.
        val original = "x".repeat(133) + "😀😀😀"
        val result = StatusText.repostPrefill("咕咕咕Y", original)!!
        assertEquals("转@咕咕咕Y " + "x".repeat(133), result)
        assertEquals(StatusText.MAX_LENGTH, StatusText.length(result))
    }

    @Test
    fun exactFitBoundaryKeepsEverything() {
        // "转@Fallacy " leaves 130 code points for the original.
        val original = "的的".repeat(70) // 140 code points
        assertEquals("转@Fallacy " + "的的".repeat(65), StatusText.repostPrefill("Fallacy", original))
    }
}
