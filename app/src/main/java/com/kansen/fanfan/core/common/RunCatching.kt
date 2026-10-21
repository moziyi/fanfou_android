package com.kansen.fanfan.core.common

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] that rethrows cancellation.
 *
 * The stdlib version turns a `CancellationException` into a failed [Result],
 * so a cancelled coroutine keeps running its error handling (WorkManager stop,
 * Compose scope teardown) and hides the real reason for the cancellation.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
