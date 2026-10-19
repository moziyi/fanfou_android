package com.kansen.fanfan.core.common

import kotlinx.serialization.json.Json

/**
 * Single JSON configuration for the whole app: unknown keys are ignored (the
 * 2012 doc predates several live payload fields), lenient parsing tolerates
 * the occasional malformed number/string.
 */
object JsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
}
