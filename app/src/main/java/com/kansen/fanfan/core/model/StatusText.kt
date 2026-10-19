package com.kansen.fanfan.core.model

/** Fanfou limits a status to 140 characters counted as Unicode code points. */
object StatusText {

    const val MAX_LENGTH = 140

    fun length(text: String): Int = text.codePointCount(0, text.length)

    fun remaining(text: String): Int = MAX_LENGTH - length(text)

    fun fits(text: String): Boolean = remaining(text) >= 0
}
