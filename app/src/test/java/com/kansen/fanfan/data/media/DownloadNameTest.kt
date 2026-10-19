package com.kansen.fanfan.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixtures are the URL shapes measured on 2026-10-19 (CDN directive, bare object, avatar query). */
class DownloadNameTest {

    @Test
    fun `drops the resize directive`() {
        assertEquals(
            "fanfan_8g_119611.jpg",
            downloadFileName(
                "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg@596w_1l.jpg",
            ),
        )
    }

    @Test
    fun `keeps the bare object name`() {
        assertEquals(
            "fanfan_8g_119611.jpg",
            downloadFileName(
                "https://s3-img.meituan.net/v1/mss_3d027b52ec5a4d589e68050845611e68/ff/n0/0p/dh/8g_119611.jpg",
            ),
        )
    }

    @Test
    fun `drops a cache busting query`() {
        assertEquals(
            "fanfan_qp.jpg",
            downloadFileName("https://s3.meituan.net/v1/mss_x/avatar/l0/01/cg/qp.jpg?1612104596"),
        )
    }

    @Test
    fun `falls back when the url carries no name`() {
        assertEquals("fanfan_photo.jpg", downloadFileName("https://s3-img.meituan.net/"))
    }

    @Test
    fun `mime follows the object extension`() {
        assertEquals("image/jpeg", downloadMimeType("https://host/ff/n0/8g_119611.jpg@596w_1l.jpg"))
        assertEquals("image/png", downloadMimeType("https://host/ff/n0/abc.PNG"))
        assertEquals("image/gif", downloadMimeType("https://host/ff/n0/abc.gif?1"))
        assertEquals("image/jpeg", downloadMimeType("https://host/ff/n0/abc"))
    }
}
