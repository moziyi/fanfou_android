package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.database.entity.DraftEntity

/** What a draft was written for. */
enum class DraftKind {
    /** A status of its own. */
    NEW,

    /** A reply to another status. */
    REPLY,

    /** A repost of another status. */
    REPOST,
    ;

    val label: String
        get() = when (this) {
            NEW -> "消息"
            REPLY -> "回复"
            REPOST -> "转发"
        }
}

/** A draft as the draft box shows it. */
data class DraftUi(
    val id: String,
    val text: String,
    val kind: DraftKind,
    /** The status this draft replies to or reposts, if any. */
    val targetId: String?,
    /** The name of that status' author, when it is still in the cache. */
    val targetName: String?,
    val updatedAt: Long,
) {
    /** 「回复 @某人」 and the like. */
    val contextLabel: String
        get() = when (kind) {
            DraftKind.NEW -> kind.label
            else -> targetName?.let { "${kind.label} @$it" } ?: kind.label
        }

    val time: String get() = ApiTime.relative(updatedAt)
}

/**
 * Draft → UI mapping, kept out of the view model so the rules (which kind a
 * draft belongs to) can be unit-tested.
 */
object DraftMapper {

    fun toUi(draft: DraftEntity, targetNames: Map<String, String> = emptyMap()): DraftUi {
        val kind = when {
            draft.inReplyToStatusId != null -> DraftKind.REPLY
            draft.repostStatusId != null -> DraftKind.REPOST
            else -> DraftKind.NEW
        }
        val targetId = draft.inReplyToStatusId ?: draft.repostStatusId
        return DraftUi(
            id = draft.id,
            text = draft.text,
            kind = kind,
            targetId = targetId,
            targetName = targetId?.let { targetNames[it] },
            updatedAt = draft.updatedAt,
        )
    }
}
