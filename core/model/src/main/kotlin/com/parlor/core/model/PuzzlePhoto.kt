package com.parlor.core.model

import kotlinx.serialization.Serializable

/** A metadata-free square JPEG. Small Base64 chunks retain the backup parser's string bound. */
@Serializable
data class PuzzlePhoto(val id: String, val jpeg: List<String>)

object PhotoLimits {
    const val SIDE = 1024
    const val IMAGE_BYTES = 512 * 1024
    const val TOTAL_BYTES = 8 * 1024 * 1024
    const val CHUNK = 4000
    const val MAX_CHUNKS = ((IMAGE_BYTES + 2) / 3 * 4 + CHUNK - 1) / CHUNK
    fun validId(id: String) = id.matches(Regex("[0-9a-f]{64}"))
}
