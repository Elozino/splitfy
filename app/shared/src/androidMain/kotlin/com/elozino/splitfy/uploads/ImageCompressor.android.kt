package com.elozino.splitfy.uploads

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

actual fun platformImageCompressor(): ImageCompressor = ImageCompressor { bytes ->
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@ImageCompressor null

    // Decode at the largest power-of-two reduction that stays above the target, so a
    // 48-megapixel photo never has to fit in memory at full size.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ImageCompressor.MAX_DIMENSION) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@ImageCompressor null

    val longest = max(decoded.width, decoded.height)
    val scaled = if (longest > ImageCompressor.MAX_DIMENSION) {
        val ratio = ImageCompressor.MAX_DIMENSION.toFloat() / longest
        Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).roundToInt(), (decoded.height * ratio).roundToInt(), true)
    } else {
        decoded
    }

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, ImageCompressor.JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        out.toByteArray()
    }
}

actual fun platformAvatarCropper(): ImageCompressor = ImageCompressor { bytes ->
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@ImageCompressor null

    // Decode no larger than needed: the short side only has to cover AVATAR_SIZE.
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_SIZE) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@ImageCompressor null

    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = if (side > AVATAR_SIZE) Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true) else square

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, ImageCompressor.JPEG_QUALITY, out)
        listOf(scaled, square, decoded).distinct().forEach { it.recycle() }
        out.toByteArray()
    }
}
