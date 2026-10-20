package com.kansen.fanfan.core.notifications

import com.kansen.fanfan.core.network.dto.StatusDto

/**
 * The rules that decide which mentions are "new", kept free of Android and
 * network code so they can be unit-tested against real payloads.
 */
object MentionRules {

    /** Highest `rawid` of a page; 0 for an empty page. */
    fun newestRawid(page: List<StatusDto>): Long = page.maxOfOrNull { it.rawid ?: 0L } ?: 0L

    /**
     * Mentions newer than the watermark. The very first run (watermark 0) has
     * nothing to compare against, so it reports nothing — installing the app
     * must not fire a notification for every mention that ever arrived.
     */
    fun freshMentions(page: List<StatusDto>, notifiedUpTo: Long): List<StatusDto> {
        if (notifiedUpTo == 0L) return emptyList()
        return page.filter { (it.rawid ?: 0L) > notifiedUpTo }
    }
}
