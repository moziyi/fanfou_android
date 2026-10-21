package com.kansen.fanfan.ui.compose

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kansen.fanfan.data.media.PreparedPhoto
import com.kansen.fanfan.data.media.computeSampleSize

/**
 * A small preview of the prepared picture. The composer shows it in a small
 * box, so decoding it at full size would waste memory.
 */
internal fun PreparedPhoto.previewBitmap(): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = computeSampleSize(width, height, PREVIEW_EDGE)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap.asImageBitmap()
}

/** The long edge of the preview image. */
private const val PREVIEW_EDGE = 512
