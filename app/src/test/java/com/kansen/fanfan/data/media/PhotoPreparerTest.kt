package com.kansen.fanfan.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picture is scaled down before it is uploaded, so a 4000×3000 phone
 * photo does not become a 3 MB upload. The maths is kept free of Android
 * types so it can be tested on the JVM.
 */
class PhotoPreparerTest {

    @Test
    fun `sample size is a power of two that fits the target edge`() {
        assertEquals(1, computeSampleSize(1000, 800, 2048))
        assertEquals(2, computeSampleSize(4000, 3000, 2048))
        assertEquals(4, computeSampleSize(8000, 6000, 2048))
    }

    @Test
    fun `sample size never scales below one`() {
        assertEquals(1, computeSampleSize(0, 0, 2048))
        assertEquals(1, computeSampleSize(100, 100, 2048))
    }

    @Test
    fun `the image is scaled to the target edge keeping its aspect ratio`() {
        assertEquals(2048 to 1536, fitWithin(4000, 3000, 2048))
        assertEquals(1536 to 2048, fitWithin(3000, 4000, 2048))
    }

    @Test
    fun `a small image is not scaled up`() {
        assertEquals(800 to 600, fitWithin(800, 600, 2048))
    }

    @Test
    fun `scaling never produces a zero edge`() {
        val (width, height) = fitWithin(4000, 3, 2048)
        assertTrue(width > 0 && height > 0)
    }
}
