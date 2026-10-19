package com.kansen.fanfan.core.model

import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.core.model.StatusMapper.toUi
import com.kansen.fanfan.core.network.dto.StatusDto
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repost and reply rendering rules, from payloads captured off the live API
 * (2026-10-19). Fanfou composes a repost's text as `comment 转@author <original>`;
 * when that exceeds 140 characters it is cut there, and only then does the
 * nested original hold text the outer one does not.
 */
class StatusQuoteDisplayTest {

    private fun status(json: String): StatusUi =
        JsonCodec.json.decodeFromString<StatusDto>(json).toUi()

    /** rZy_SVRUlCY: 60-char text that does embed the original verbatim. */
    @Test
    fun `repost whose text embeds the original hides the quote block`() {
        val ui = status(REPOST_TEXT_EMBEDS_ORIGINAL)

        assertTrue(ui.isRepost)
        assertFalse(ui.showsRepostQuote)
    }

    /** ETZecNyVCwk: the composed text was cut at 140 characters. */
    @Test
    fun `repost cut at the 140 character limit shows the quote block`() {
        val ui = status(REPOST_TEXT_TRUNCATED)

        assertEquals(140, ui.text.length)
        assertTrue(ui.showsRepostQuote)
        assertEquals("Fallacy", ui.repost?.user?.name)
    }

    /** dzGdxkEbthc: same cut, but the original is far longer than the tail kept. */
    @Test
    fun `repost cut short still shows the quote block`() {
        val ui = status(REPOST_TEXT_TRUNCATED_SHORT)

        assertEquals(140, ui.text.length)
        assertTrue(ui.showsRepostQuote)
    }

    /**
     * The original's photo is nested inside the repost; when the quote block is
     * hidden the card body falls back to it, and when the block is shown it must
     * not (the block renders the same photo).
     */
    @Test
    fun `body photo follows the quote block`() {
        val embeds = status(REPOST_TEXT_EMBEDS_ORIGINAL_WITH_PHOTO)
        assertEquals("http://static.fanfou.com/img/t.jpg", embeds.bodyPhoto?.thumbUrl)

        val truncated = status(REPOST_TEXT_TRUNCATED)
        assertNull("truncated repost has no photo outside the quote", truncated.repost?.photo)
        assertNull(truncated.bodyPhoto)
    }

    /**
     * A reply payload carries only the id of the status it answers, so the UI
     * model never invents a parent — the repository resolves it separately.
     */
    @Test
    fun `reply payload resolves no parent on its own`() {
        val ui = status(REPLY)

        assertTrue(ui.isReply)
        assertEquals("LxL7Kbm5Bt4", ui.inReplyToStatusId)
        assertEquals("向告别飞驰吧", ui.inReplyToScreenName)
        assertNull(ui.replied)
        assertFalse(ui.showsRepostQuote)
    }
}

private const val REPOST_TEXT_EMBEDS_ORIGINAL = """
{
  "id": "rZy_SVRUlCY",
  "rawid": 276438800,
  "created_at": "Mon Oct 19 07:20:06 +0000 2026",
  "text": "太随心所欲了 转@梓曜_外卖员 这次出来主打一个没有计划，到了杭州就随便走走。晚上到火车站再看去哪里，大概率要去金温线。",
  "source": "网页",
  "repost_status_id": "cz8fQrF0Dh4",
  "repost_screen_name": "梓曜_外卖员",
  "repost_status": {
    "id": "cz8fQrF0Dh4",
    "rawid": 276438700,
    "text": "这次出来主打一个没有计划，到了杭州就随便走走。晚上到火车站再看去哪里，大概率要去金温线。",
    "user": { "id": "ziyao", "name": "梓曜_外卖员", "screen_name": "梓曜_外卖员" }
  },
  "user": { "id": "turancao", "name": "徒然草", "screen_name": "徒然草" }
}
"""

private const val REPOST_TEXT_TRUNCATED = """
{
  "id": "ETZecNyVCwk",
  "rawid": 276438500,
  "text": "有道理，先甜后苦肯定不会亏。转@Fallacy 如果你喜欢玩游戏，你就先玩游戏，如果你喜欢看电影，你就先看电影，反正把你特别想干的事情先做掉，再去干其他所谓的“正事儿”，这样你就不会有一种我为了玩游戏我要先痛苦的学一个小时的感受。你就先玩，玩开心了再学，玩不开心就一直玩，不要把玩",
  "repost_status_id": "a1b2c3",
  "repost_screen_name": "Fallacy",
  "repost_status": {
    "id": "a1b2c3",
    "text": "如果你喜欢玩游戏，你就先玩游戏，如果你喜欢看电影，你就先看电影，反正把你特别想干的事情先做掉，再去干其他所谓的“正事儿”，这样你就不会有一种我为了玩游戏我要先痛苦的学一个小时的感受。你就先玩，玩开心了再学，玩不开心就一直玩，不要把玩当作奖励，要当作你要完成的任务。",
    "user": { "id": "fallacy", "name": "Fallacy", "screen_name": "Fallacy" }
  },
  "user": { "id": "turancao", "name": "徒然草", "screen_name": "徒然草" }
}
"""

private const val REPOST_TEXT_TRUNCATED_SHORT = """
{
  "id": "dzGdxkEbthc",
  "rawid": 276438400,
  "text": "问 ds 尖锐一点的话题需要截图，他输出一半就会自我审查。元宝有时候会把文字夹掉但是出声音。 转@被动 昨天让国产 AI 解答解读一个博主的观点，DeepSeek 直接给出了正确答案，但是回答完之后把答案撤回了；元宝多问了两句，给出了正确的答案；豆包专家模式跟个傻子一样； Kim",
  "repost_status_id": "d4e5f6",
  "repost_status": {
    "id": "d4e5f6",
    "text": "目前聊天尺度千问大于元宝大于 DS，kimi 因为没充值已经是路边一条了",
    "user": { "id": "beidong", "name": "被动", "screen_name": "被动" }
  },
  "user": { "id": "turancao", "name": "徒然草", "screen_name": "徒然草" }
}
"""

private const val REPOST_TEXT_EMBEDS_ORIGINAL_WITH_PHOTO = """
{
  "id": "photo-repost",
  "rawid": 276438300,
  "text": "好照片 转@摄影师 黄昏的江边",
  "repost_status_id": "photo-original",
  "repost_status": {
    "id": "photo-original",
    "text": "黄昏的江边",
    "photo": {
      "imageurl": "http://static.fanfou.com/img/m.jpg",
      "thumburl": "http://static.fanfou.com/img/t.jpg",
      "largeurl": "http://static.fanfou.com/img/l.jpg"
    },
    "user": { "id": "shutter", "name": "摄影师", "screen_name": "摄影师" }
  },
  "user": { "id": "turancao", "name": "徒然草", "screen_name": "徒然草" }
}
"""

private const val REPLY = """
{
  "id": "4yhj3mO0lmQ",
  "rawid": 276438759,
  "text": "@向告别飞驰吧 也是很高精力了，换我已经是睡着的那一波里的一位了。",
  "in_reply_to_status_id": "LxL7Kbm5Bt4",
  "in_reply_to_user_id": "~9i5RVqY8aqs",
  "in_reply_to_screen_name": "向告别飞驰吧",
  "user": { "id": "turancao", "name": "徒然草", "screen_name": "徒然草" }
}
"""
