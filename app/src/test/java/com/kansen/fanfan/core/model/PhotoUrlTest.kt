package com.kansen.fanfan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fixtures are the three URLs a real status payload carried on 2026-10-19, all
 * pointing at the same S3 object (4000x3000 original).
 */
private const val THUMB =
    "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg@120w_120h_1l.jpg"
private const val IMAGE =
    "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg@200w_200h_1l.jpg"
private const val LARGE =
    "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg@596w_1l.jpg"
private const val OBJECT =
    "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg"

class PhotoUrlTest {

    @Test
    fun `asks the cdn for the width the card draws`() {
        val photo = PhotoUi(thumbUrl = THUMB, imageUrl = IMAGE, largeUrl = LARGE)
        assertEquals("$OBJECT@1080w_1l.jpg", photo.atWidth(1080))
    }

    @Test
    fun `rewrites from the largest variant available`() {
        assertEquals(
            "$OBJECT@924w_1l.jpg",
            PhotoUi(thumbUrl = THUMB, imageUrl = IMAGE, largeUrl = LARGE).atWidth(924),
        )
        assertEquals(
            "$OBJECT@924w_1l.jpg",
            PhotoUi(thumbUrl = THUMB, imageUrl = IMAGE, largeUrl = null).atWidth(924),
        )
        assertEquals("$OBJECT@924w_1l.jpg", PhotoUi(THUMB, null, null).atWidth(924))
    }

    @Test
    fun `keeps a foreign url untouched instead of guessing`() {
        // The wiki-era CDN shape: no processing directive to rewrite.
        val photo = PhotoUi(
            thumbUrl = "http://static.fanfou.com/img/t.jpg",
            imageUrl = "http://static.fanfou.com/img/m.jpg",
            largeUrl = "http://static.fanfou.com/img/l.jpg",
        )
        assertEquals("http://static.fanfou.com/img/l.jpg", photo.atWidth(1080))
        assertNull(photo.originalUrl)
    }

    @Test
    fun `a non positive width means no rewrite`() {
        val photo = PhotoUi(thumbUrl = THUMB, imageUrl = IMAGE, largeUrl = LARGE)
        assertEquals(LARGE, photo.atWidth(0))
    }

    @Test
    fun `original url is the bare object`() {
        val photo = PhotoUi(thumbUrl = THUMB, imageUrl = IMAGE, largeUrl = LARGE)
        assertEquals(OBJECT, photo.originalUrl)
    }

    @Test
    fun `no photo urls at all`() {
        assertNull(PhotoUi(null, null, null).atWidth(1080))
        assertNull(PhotoUi(null, null, null).originalUrl)
    }
}
