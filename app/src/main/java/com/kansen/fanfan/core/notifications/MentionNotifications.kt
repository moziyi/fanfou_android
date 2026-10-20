package com.kansen.fanfan.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kansen.fanfan.R
import com.kansen.fanfan.core.network.dto.StatusDto
import com.kansen.fanfan.ui.MainActivity

/**
 * System notifications for new mentions. One notification per status, grouped
 * so several arriving in the same poll collapse in the shade, plus a summary
 * that opens the @我 tab.
 */
object MentionNotifications {

    const val CHANNEL_ID = "mentions"
    private const val GROUP_KEY = "fanfan.mentions"
    private const val SUMMARY_ID = 1
    private const val MAX_INDIVIDUAL = 5

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "提及", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "有人 @ 你 时提醒"
            },
        )
    }

    fun show(context: Context, statuses: List<StatusDto>) {
        if (statuses.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = NotificationManagerCompat.from(context)

        runCatching {
            statuses.take(MAX_INDIVIDUAL).forEach { status ->
                val id = status.id ?: return@forEach
                val author = status.user?.screen_name.orEmpty().ifBlank { "有人" }
                val body = status.text.orEmpty().replace('\n', ' ').trim()
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_mentions)
                    .setContentTitle("$author 提到了你")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openStatus(context, id))
                    .build()
                manager.notify(id.hashCode(), notification)
            }
            if (statuses.size > 1) manager.notify(SUMMARY_ID, summary(context, statuses.size))
        }
    }

    private fun summary(context: Context, count: Int) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_mentions)
        .setContentTitle("$count 条新提及")
        .setContentText("点击查看 @我")
        .setAutoCancel(true)
        .setGroup(GROUP_KEY)
        .setGroupSummary(true)
        .setContentIntent(openMentions(context))
        .build()

    /**
     * Debug-only helper: posts a notification with a real status id so the
     * channel, the shade rendering and the tap-through can be verified
     * without waiting for a real mention.
     */
    fun showDebug(context: Context, statusId: String, author: String, text: String) {
        show(
            context,
            listOf(
                StatusDto(
                    id = statusId,
                    text = text,
                    user = com.kansen.fanfan.core.network.dto.UserDto(
                        id = "debug",
                        screen_name = author,
                    ),
                ),
            ),
        )
    }

    private fun openStatus(context: Context, statusId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_STATUS_ID, statusId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            statusId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openMentions(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MENTIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
