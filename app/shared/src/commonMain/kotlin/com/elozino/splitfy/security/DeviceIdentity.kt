package com.elozino.splitfy.security

import com.elozino.splitfy.api.dto.DevicePlatform

/**
 * How this device introduces itself to the API, so the Devices screen can say
 * "Ada's Pixel" instead of an id. Sent as headers on every request.
 */
data class DeviceIdentity(val name: String, val platform: DevicePlatform)

expect fun currentDeviceIdentity(): DeviceIdentity

/**
 * HTTP header values must be printable ASCII. Device names often are not (the
 * curly apostrophe in "Ada’s iPhone"), so typographic quotes are straightened and
 * anything else outside ASCII is dropped rather than failing the request.
 */
fun String.toHeaderValue(maxLength: Int = 100): String =
    map { char ->
        when (char) {
            '‘', '’', 'ʼ' -> '\''
            '“', '”' -> '"'
            else -> char
        }
    }
        .filter { it in ' '..'~' }
        .joinToString("")
        .trim()
        .take(maxLength)
