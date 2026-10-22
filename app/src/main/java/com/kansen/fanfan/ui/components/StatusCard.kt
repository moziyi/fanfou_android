package com.kansen.fanfan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kansen.fanfan.core.model.ApiTime
import com.kansen.fanfan.core.model.PhotoUi
import com.kansen.fanfan.core.model.StatusUi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle

/**
 * One status row, laid out the way Fanfou's own clients do it: avatar column on
 * the left, then author / body / media / quoted repost, and a footer that keeps
 * the source on the left and the actions on the right.
 *
 * Quote blocks follow one rule — never print words the card already shows. A
 * reply quotes the status it answers (fetched separately, see ReplyTargetResolver),
 * and a repost only quotes its target when Fanfou truncated the composed text.
 *
 * The status payload carries no interaction counts (unlike Weibo), so the
 * action row is icon-only — labels would imply numbers we cannot show.
 */
@Composable
fun StatusCard(
    status: StatusUi,
    onClick: () -> Unit,
    onReply: () -> Unit,
    onRepost: () -> Unit,
    onFavorite: () -> Unit,
    /** Opens the full-screen viewer; null keeps photos inert (e.g. inside the viewer itself). */
    onPhotoClick: ((PhotoUi) -> Unit)? = null,
    /** Opens the author's profile; null keeps the avatar and name inert. */
    onUserClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = true,
    showActions: Boolean = true,
    /**
     * Whether the reply and repost buttons are drawn. The favorites list has
     * only the star: it removes the entry.
     */
    showReplyRepost: Boolean = true,
    isContextTarget: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isContextTarget) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) else Modifier)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            UserAvatar(status.user?.avatarUrl, onUserClick)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                AuthorRow(status, onUserClick)
                if (status.isReply) ReplyLine(status.inReplyToScreenName)

                if (status.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = status.bodyText(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // A reply quotes what it answers, in the same block style as a
                // repost. Fanfou does not put the parent in the payload, so it
                // is resolved separately and absent when it could not be fetched.
                status.replied?.let { parent ->
                    Spacer(Modifier.height(8.dp))
                    QuotedStatus(parent, onPhotoClick)
                }

                // A repost's text already contains the original, so the block is
                // only shown when Fanfou truncated that text (see showsRepostQuote).
                if (status.showsRepostQuote) {
                    status.repost?.let { original ->
                        Spacer(Modifier.height(8.dp))
                        QuotedStatus(original, onPhotoClick)
                    }
                }

                status.bodyPhoto?.let { photo ->
                    Spacer(Modifier.height(8.dp))
                    StatusPhoto(photo, maxHeight = 320, onClick = onPhotoClick)
                }

                Spacer(Modifier.height(8.dp))
                FooterRow(
                    status = status,
                    showActions = showActions,
                    showFavorite = showFavorite,
                    showReplyRepost = showReplyRepost,
                    onReply = onReply,
                    onRepost = onRepost,
                    onFavorite = onFavorite,
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(url: String?, onUserClick: (() -> Unit)?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onUserClick != null) Modifier.clickable(onClick = onUserClick) else Modifier),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Name (bold) on the left, relative time pushed to the right — the Fanfou web
 * layout. The handle is only shown when it differs from the display name, so
 * "徒然草 @徒然草" does not appear twice.
 */
@Composable
private fun AuthorRow(status: StatusUi, onUserClick: (() -> Unit)?) {
    val name = status.user?.name.orEmpty()
    val screenName = status.user?.screenName.orEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onUserClick != null) Modifier.clickable(onClick = onUserClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Non-weighted sibling is measured first, so a long name
                // ellipsizes instead of pushing the handle out of the row.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (screenName.isNotBlank() && screenName != name) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "@$screenName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = ApiTime.relative(status.createdAtEpoch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReplyLine(screenName: String?) {
    val handle = screenName?.takeIf { it.isNotBlank() }
    Text(
        text = if (handle != null) "回复 @$handle" else "回复",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Source on the left, actions on the right — one row instead of two stacked ones. */
@Composable
private fun FooterRow(
    status: StatusUi,
    showActions: Boolean,
    showFavorite: Boolean,
    showReplyRepost: Boolean,
    onReply: () -> Unit,
    onRepost: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val meta = buildList {
            status.sourceName?.let { add("来自 $it") }
            status.location?.let { add(it) }
        }.joinToString(" · ")

        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (showActions) {
            if (showReplyRepost) {
                ActionIcon(Icons.Outlined.ChatBubbleOutline, "回复", onReply)
                ActionIcon(Icons.Outlined.SwapHoriz, "转发", onRepost)
            }
            if (showFavorite) {
                ActionIcon(
                    icon = if (status.favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (status.favorited) "取消收藏" else "收藏",
                    onClick = onFavorite,
                    tint = if (status.favorited) MaterialTheme.colorScheme.primary else null,
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Timeline photo: asks the CDN for the pixels it will actually draw (see
 * [PhotoUi.atWidth]) and is capped so a tall image cannot swallow the screen.
 * The `@120w_120h` variants are containment boxes, not square crops — the
 * aspect ratio is preserved.
 */
@Composable
private fun StatusPhoto(photo: PhotoUi, maxHeight: Int, onClick: ((PhotoUi) -> Unit)? = null) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Width of the card's content column: screen minus the row's start padding
    // (16), the avatar and its gap (42 + 10) and the end padding (8).
    val contentWidthDp = (configuration.screenWidthDp - 76).dp
    val targetWidthPx = with(density) { contentWidthDp.roundToPx() }

    AsyncImage(
        model = photo.atWidth(targetWidthPx),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .then(if (onClick != null) Modifier.clickable { onClick(photo) } else Modifier),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
private fun QuotedStatus(status: StatusUi, onPhotoClick: ((PhotoUi) -> Unit)? = null) {
    val name = status.user?.name.orEmpty()
    val screenName = status.user?.screenName.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (screenName.isNotBlank() && screenName != name) "$name @$screenName" else name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = status.bodyText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        status.photo?.let { photo ->
            Spacer(Modifier.height(6.dp))
            StatusPhoto(photo, maxHeight = 220, onClick = onPhotoClick)
        }
    }
}

/** Placeholder for screens scheduled in a later milestone. */
@Composable
fun PlaceholderScreen(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The text of a status. In a search result the words that matched are drawn
 * in bold; everywhere else the text is plain.
 */
private fun StatusUi.bodyText(): AnnotatedString {
    val spans = highlight ?: return AnnotatedString(text)
    return buildAnnotatedString {
        spans.forEach { span ->
            if (span.bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(span.text)
                }
            } else {
                append(span.text)
            }
        }
    }
}
