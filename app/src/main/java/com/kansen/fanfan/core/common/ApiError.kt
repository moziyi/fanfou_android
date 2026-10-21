package com.kansen.fanfan.core.common

import com.kansen.fanfan.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Standard fanfou error envelope: {"request": .., "error": ..}. Extracts the
 * human-readable message; null when the body is not the expected shape.
 */
fun parseApiError(body: String?): String? = body?.let { raw ->
    runCatching { errorJson.decodeFromString<ApiErrorDto>(raw).error }.getOrNull()
}

/**
 * Maps a network call failure to a user-facing message. 401 means the stored
 * token is no longer valid (sole force-logout trigger per design §5.4).
 */
fun Throwable.userMessage(): String = when (this) {
    // A problem with the picture the user picked is not a network problem.
    is PictureException -> message ?: "图片处理失败"
    is FanfouApiException -> message ?: "请求失败 (HTTP $httpCode)"
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        parseApiError(body) ?: "请求失败 (HTTP ${code()})"
    }
    // OkHttp reports connectivity problems as IOExceptions; their messages are
    // English stack-ish text ("Unable to resolve host …"), useless in the UI.
    is IOException -> "网络连接失败，请检查网络后重试"
    else -> message ?: "网络错误，请重试"
}

/** Fanfou server error with the parsed `error` message attached. */
class FanfouApiException(val httpCode: Int, message: String) : RuntimeException(message)
