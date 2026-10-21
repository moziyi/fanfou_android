package com.kansen.fanfan.core.common

/**
 * A problem with the picture the user picked — it cannot be read, it is too
 * big or the device cannot decode it. It is not a network problem, so it must
 * not be reported as one.
 */
class PictureException(message: String) : RuntimeException(message)
