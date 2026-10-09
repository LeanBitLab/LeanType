package com.leanbitlab.leantype.gif

object Contract {
    const val VERSION = 2
    const val KIND_GIF = 0
    const val KIND_STICKER = 1
    const val KIND_ANIMATED_EMOJI = 2
    const val KIND_MEME = 3

    const val KINDS_GIF = 1
    const val KINDS_STICKER = 2
    const val KINDS_ANIMATED_EMOJI = 4
    const val KINDS_MEME = 8

    const val CAP_SEARCH = 1
    const val CAP_TRENDING = 2
    const val CAP_PACKS = 4

    const val ERR_BAD_REQUEST = 1
    const val ERR_NOT_CONFIGURED = 2
    const val ERR_NETWORK = 3
    const val ERR_RATE_LIMITED = 4
    const val ERR_CONVERSION = 5
    const val ERR_DENIED = 6
    const val ERR_CANCELLED = 7
    const val ERR_TIMEOUT = 8
    const val ERR_OTHER = 99
}

/** Thrown by providers and helpers. The service maps it to IGifCallback.onError. */
class GifException(val code: Int, message: String) : Exception(message)
