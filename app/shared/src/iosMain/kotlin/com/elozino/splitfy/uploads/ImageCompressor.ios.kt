package com.elozino.splitfy.uploads

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
actual fun platformImageCompressor(): ImageCompressor = ImageCompressor { bytes ->
    if (bytes.isEmpty()) return@ImageCompressor null
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return@ImageCompressor null

    val (width, height) = image.size.useContents { width to height }
    val longest = max(width, height)
    val scale = if (longest > ImageCompressor.MAX_DIMENSION) ImageCompressor.MAX_DIMENSION / longest else 1.0

    // Drawing also applies the photo's EXIF orientation, so the upload is upright.
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width * scale, height * scale), format = format)
    val resized = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    }

    val jpeg = UIImageJPEGRepresentation(resized, ImageCompressor.JPEG_QUALITY / 100.0) ?: return@ImageCompressor null
    ByteArray(jpeg.length.toInt()).apply {
        usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformAvatarCropper(): ImageCompressor = ImageCompressor { bytes ->
    if (bytes.isEmpty()) return@ImageCompressor null
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return@ImageCompressor null

    val (width, height) = image.size.useContents { width to height }
    val side = minOf(width, height)
    val target = minOf(side, AVATAR_SIZE.toDouble())
    val scale = target / side

    // Draw the whole photo scaled and offset so its centred square fills the canvas;
    // drawing also applies the EXIF orientation.
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(target, target), format = format)
    val cropped = renderer.imageWithActions { _ ->
        image.drawInRect(
            CGRectMake(-(width - side) / 2 * scale, -(height - side) / 2 * scale, width * scale, height * scale)
        )
    }

    val jpeg = UIImageJPEGRepresentation(cropped, ImageCompressor.JPEG_QUALITY / 100.0) ?: return@ImageCompressor null
    ByteArray(jpeg.length.toInt()).apply {
        usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    }
}
