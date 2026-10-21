package com.elozino.splitfy.uploads

/**
 * Shrinks a photo before upload: longest side at most [MAX_DIMENSION] pixels,
 * re-encoded as JPEG. A phone camera's 12-megapixel PNG or full-quality JPEG becomes
 * a few hundred kilobytes, which is still plenty to read a receipt.
 *
 * Returns null when the bytes are not a decodable image.
 */
fun interface ImageCompressor {
    fun compress(bytes: ByteArray): ByteArray?

    companion object {
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 80

        /** Leaves bytes untouched; for tests. */
        val None = ImageCompressor { it }
    }
}

expect fun platformImageCompressor(): ImageCompressor

/** Side of the square profile photo, in pixels: sharp at 100dp on a 3x screen. */
const val AVATAR_SIZE = 512

/**
 * Turns a photo into a profile picture: the largest centred square, scaled to
 * [AVATAR_SIZE] pixels and re-encoded as JPEG. Null when the bytes are not an image.
 */
expect fun platformAvatarCropper(): ImageCompressor
