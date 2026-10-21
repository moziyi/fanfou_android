package com.kansen.fanfan.data.media

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * A request body for bytes that are already in memory, reporting how much of
 * them has been written so the composer can show the upload progress.
 *
 * The bytes are written in chunks instead of one call, so the progress moves
 * during the upload instead of jumping to 100 % at the end.
 */
class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var written = 0
        while (written < bytes.size) {
            val chunk = minOf(CHUNK_BYTES, bytes.size - written)
            sink.write(bytes, written, chunk)
            written += chunk
            onProgress(written.toFloat() / bytes.size)
        }
    }

    private companion object {
        const val CHUNK_BYTES = 32 * 1024
    }
}
