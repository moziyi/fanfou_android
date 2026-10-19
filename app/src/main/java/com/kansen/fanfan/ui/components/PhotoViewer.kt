package com.kansen.fanfan.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.kansen.fanfan.core.model.PhotoUi
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val MinScale = 1f
private const val MaxScale = 6f
private const val DoubleTapScale = 2.5f

/** Width asked of the CDN when the payload carries no URL the directive can be applied to. */
private const val FallbackWidthPx = 2160

/**
 * Full-screen viewer for one status photo.
 *
 * Opens on the original bytes (bare CDN object, measured 4000x3000 / 3.1 MB) at
 * fit-to-screen, pinch or double-tap to zoom up to 6x, drag to pan. Long press
 * saves the original to `Download/Fanfan`.
 *
 * A status carries at most one photo (Fanfou has no multi-image status), so
 * there is no pager here — a swipe would have nothing to page to.
 */
@Composable
fun PhotoViewer(
    photo: PhotoUi,
    onDismiss: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val saveState by viewModel.save.collectAsStateWithLifecycle()
    val url = photo.originalUrl ?: photo.atWidth(FallbackWidthPx)

    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize.ORIGINAL)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val painterState by painter.state.collectAsStateWithLifecycle()

    var scale by remember { mutableFloatStateOf(MinScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Long press never downloads on its own: it raises the action row and the
    // save only runs from the explicit confirmation there.
    var showActions by remember { mutableStateOf(false) }

    val intrinsic = (painterState as? AsyncImagePainter.State.Success)
        ?.result?.image
        ?.let { Size(it.width.toFloat(), it.height.toFloat()) }
        ?: Size.Unspecified
    val fit = fitWithin(intrinsic, viewport)

    LaunchedEffect(saveState) {
        if (saveState is PhotoViewerViewModel.SaveState.Saved ||
            saveState is PhotoViewerViewModel.SaveState.Failed
        ) {
            delay(2500)
            viewModel.consumeResult()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (url == null) {
                Text(
                    text = "图片不可用",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // Image (not AsyncImage) so the painter's intrinsic size is
                // observable — the pan limits need to know the fitted size.
                Image(
                    painter = painter,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewport = it }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )

                // Gestures sit on their own layer so the tap coordinates stay in
                // screen space and never have to undo the graphicsLayer above.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(fit, viewport) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(MinScale, MaxScale)
                                val factor = if (scale > 0f) next / scale else 1f
                                val pivot = centroid - viewport.center()
                                offset = clampOffset(pivot - (pivot - offset) * factor + pan, next, fit, viewport)
                                scale = next
                            }
                        }
                        .pointerInput(fit, viewport) {
                            detectTapGestures(
                                onTap = { showActions = false },
                                onDoubleTap = { tap ->
                                    val next = if (scale > MinScale + 0.01f) MinScale else DoubleTapScale
                                    val factor = next / scale
                                    val pivot = tap - viewport.center()
                                    offset = clampOffset(pivot - (pivot - offset) * factor, next, fit, viewport)
                                    scale = next
                                },
                                onLongPress = { showActions = true },
                            )
                        },
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }

                SaveStatus(
                    state = saveState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                if (showActions) {
                    SaveActions(
                        onSave = {
                            showActions = false
                            viewModel.saveOriginal(url)
                        },
                        onDismiss = { showActions = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    Text(
                        text = "长按保存原图",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveStatus(
    state: PhotoViewerViewModel.SaveState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PhotoViewerViewModel.SaveState.Idle -> Unit

        is PhotoViewerViewModel.SaveState.Saving -> Pill(modifier) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Text(
                text = "保存中 ${(state.progress * 100).roundToInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        PhotoViewerViewModel.SaveState.Saved -> Pill(modifier) {
            Text(
                text = "已保存到「下载/Fanfan」",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        is PhotoViewerViewModel.SaveState.Failed -> Pill(modifier) {
            Text(
                text = state.message,
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Pill(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.62f),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 44.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Confirmation row raised by a long press; the download starts only from here. */
@Composable
private fun SaveActions(
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE6202020),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "保存原图到「下载/Fanfan」？",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.7f))
            }
            TextButton(onClick = onSave) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun IntSize.center(): Offset = Offset(width / 2f, height / 2f)

/** Keeps the scaled image covering the viewport, so it can never be dragged away. */
private fun clampOffset(raw: Offset, scale: Float, fit: IntSize, viewport: IntSize): Offset {
    val maxX = ((fit.width * scale - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((fit.height * scale - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
}

/** Size of a [ContentScale.Fit] image inside the viewport. */
private fun fitWithin(intrinsic: Size, viewport: IntSize): IntSize {
    if (intrinsic.width.isNaN() || intrinsic.height.isNaN() ||
        viewport.width <= 0 || viewport.height <= 0
    ) {
        return viewport
    }
    val factor = min(viewport.width / intrinsic.width, viewport.height / intrinsic.height)
    return IntSize((intrinsic.width * factor).roundToInt(), (intrinsic.height * factor).roundToInt())
}
