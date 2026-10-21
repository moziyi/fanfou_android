package com.kansen.fanfan.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A picture that is ready to be uploaded: the (possibly shrunk) JPEG bytes,
 * the file name the multipart part carries and the pixel size.
 */
data class PreparedPhoto(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
    val width: Int,
    val height: Int,
) {
    // Data class with a ByteArray: identity equality would compare the
    // reference, so compare the content.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreparedPhoto) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileName == other.fileName &&
            width == other.width &&
            height == other.height
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Turns a picked image into something worth uploading.
 *
 * Phone cameras produce 4000×3000 / 3 MB files; the app never shows more than
 * ~1080 px, so the picture is scaled down to 2048 px on the long edge and
 * re-encoded as JPEG (quality 85). That keeps a photo post in the few hundred
 * kilobytes, which matters on a mobile connection.
 *
 * An image the device cannot decode (a HEIC on API 26/27) is sent unchanged
 * rather than being refused.
 */
@Singleton
class PhotoPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun prepare(uri: Uri): PreparedPhoto = withContext(Dispatchers.Default) {
        val original = readBounded(uri)
        val mime = context.contentResolver.getType(uri) ?: mimeFromName(uri)

        val bitmap = decodeScaled(original)
        if (bitmap == null) {
            // Unsupported format: send the original bytes.
            PreparedPhoto(
                bytes = original,
                mimeType = mime,
                fileName = fileNameFor(uri, mime),
                width = 0,
                height = 0,
            )
        } else {
            // The size is read before the encoding: encodeJpeg recycles the
            // bitmap, and a recycled bitmap cannot be asked for its size.
            val width = bitmap.width
            val height = bitmap.height
            val bytes = encodeJpeg(bitmap, rotationOf(original))
            PreparedPhoto(
                bytes = bytes,
                mimeType = "image/jpeg",
                fileName = fileNameFor(uri, "image/jpeg"),
                width = width,
                height = height,
            )
        }
    }

    /**
     * Reads the picture, but not an endless one: a huge file would exhaust
     * the memory of the app before it is scaled down.
     */
    private fun readBounded(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选图片")
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_SOURCE_BYTES) throw IOException("图片太大（超过 20 MB）")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val target = fitWithin(bitmap.width, bitmap.height, MAX_EDGE)
        if (target.first != bitmap.width || target.second != bitmap.height) {
            val scaled = Bitmap.createScaledBitmap(bitmap, target.first, target.second, true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    /** Applies the EXIF rotation, so a photo taken sideways is not uploaded sideways. */
    private fun encodeJpeg(bitmap: Bitmap, rotation: Int): ByteArray {
        val rotated = if (rotation == 0) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (rotated != bitmap) rotated.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun rotationOf(bytes: ByteArray): Int = try {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun mimeFromName(uri: Uri): String {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun fileNameFor(uri: Uri, mime: String): String {
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("heic") -> "heic"
            else -> "jpg"
        }
        val stamp = System.currentTimeMillis()
        return "fanfan_$stamp.$ext"
    }

    companion object {
        /** The long edge of the uploaded picture. */
        const val MAX_EDGE = 2048
        const val JPEG_QUALITY = 85

        /** Pictures bigger than this are refused instead of exhausting the heap. */
        const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
    }
}

/**
 * The power-of-two sample size that brings the image below [maxEdge] during
 * decoding — decoding at full size first is what makes big photos run out of
 * memory.
 */
internal fun computeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    var longEdge = maxOf(width, height)
    // Halve until the decoded size fits: the decoder rounds up, so the
    // ceiling is used.
    while (longEdge > maxEdge) {
        longEdge = (longEdge + 1) / 2
        sample *= 2
    }
    return sample
}

/** The size the image is scaled to, keeping its aspect ratio. */
internal fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return width to height
    val longEdge = maxOf(width, height)
    if (longEdge <= maxEdge) return width to height
    val ratio = maxEdge.toFloat() / longEdge
    return (width * ratio).toInt().coerceAtLeast(1) to (height * ratio).toInt().coerceAtLeast(1)
}
