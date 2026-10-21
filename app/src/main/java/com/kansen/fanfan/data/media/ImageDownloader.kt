package com.kansen.fanfan.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.kansen.fanfan.core.common.runCatchingCancellable

/**
 * Saves a photo's original bytes into `Download/Fanfan`.
 *
 * Fanfou's image CDN serves plain public URLs (no OAuth), so this uses the app's
 * OkHttp client directly — the signing interceptor only touches api.fanfou.com.
 *
 * MediaStore's download collection only exists from API 29. On 26-28 saving
 * would need WRITE_EXTERNAL_STORAGE plus a legacy path; that branch is
 * deliberately not implemented (see the design doc, D7) and callers hide the
 * action on those releases.
 */
@Singleton
class ImageDownloader @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    suspend fun saveToDownloads(
        url: String,
        onProgress: (Float) -> Unit = {},
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext Result.failure(IOException("Android 10 起才支持保存到下载目录"))
        }
        val target = try {
            createPendingEntry(url)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        try {
            transfer(url, target, onProgress)
            Result.success(publish(target))
        } catch (e: Exception) {
            // A pending row that never publishes would linger invisibly.
            runCatchingCancellable { context.contentResolver.delete(target, null, null) }
            Result.failure(e)
        }
    }

    private fun createPendingEntry(url: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, downloadFileName(url))
            put(MediaStore.Downloads.MIME_TYPE, downloadMimeType(url))
            put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return context.contentResolver.insert(collection, values)
            ?: throw IOException("无法创建下载条目")
    }

    private suspend fun transfer(url: String, target: Uri, onProgress: (Float) -> Unit) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use { result ->
            if (!result.isSuccessful) throw IOException("HTTP ${result.code}")
            val body = result.body
            val total = body.contentLength()
            val sink = context.contentResolver.openOutputStream(target)
                ?: throw IOException("无法写入下载目录")
            sink.use { output ->
                body.byteStream().use { source ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var written = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            onProgress((written.toFloat() / total).coerceIn(0f, 0.99f))
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun publish(target: Uri): Uri {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(target, values, null, null)
        return target
    }

    private companion object {
        val DOWNLOAD_FOLDER = "${Environment.DIRECTORY_DOWNLOADS}/Fanfan"
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

/**
 * `.../8g_119611.jpg@596w_1l.jpg` (or the bare object) → `fanfan_8g_119611.jpg`.
 * Everything the CDN adds after `@` is a resize directive, not part of the name.
 */
internal fun downloadFileName(url: String): String {
    val bare = url.substringBefore('@').substringBefore('?')
    val last = bare.substringAfterLast('/')
    val name = last
        .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        .takeIf { it.isNotBlank() && it.substringBefore('.').isNotBlank() }
    return "fanfan_${name ?: "photo.jpg"}"
}

/** Fanfou served JPEG for all 13 photos measured on 2026-10-19; keep the rest as a guard. */
internal fun downloadMimeType(url: String): String = when (downloadFileName(url).substringAfterLast('.').lowercase()) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}
