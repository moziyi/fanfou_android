package com.kansen.fanfan.core.model

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fanfou timestamps are `"Wed Nov 09 07:15:21 +0000 2011"` (English locale,
 * numeric offset). Parsed to epoch millis once, formatted for display later.
 */
object ApiTime {

    private val apiFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

    private val sameYearFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    private val otherYearFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun parseEpochMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching {
            OffsetDateTime.parse(raw.trim(), apiFormat).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    /** Relative time for timeline rows; falls back to absolute for old posts. */
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return ""
        val delta = (now - epochMillis).coerceAtLeast(0L)
        return when {
            delta < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
            delta < TimeUnit.HOURS.toMillis(1) -> "${delta / TimeUnit.MINUTES.toMillis(1)}分钟前"
            delta < TimeUnit.DAYS.toMillis(1) -> "${delta / TimeUnit.HOURS.toMillis(1)}小时前"
            delta < TimeUnit.DAYS.toMillis(7) -> "${delta / TimeUnit.DAYS.toMillis(1)}天前"
            else -> absolute(epochMillis, now)
        }
    }

    fun absolute(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val dateTime = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        val nowYear = java.time.Instant.ofEpochMilli(now).atZone(zone).year
        val format = if (dateTime.year == nowYear) sameYearFormat else otherYearFormat
        return dateTime.format(format)
    }
}
