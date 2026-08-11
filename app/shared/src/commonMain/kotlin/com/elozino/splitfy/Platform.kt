package com.elozino.splitfy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun openAppSettings()
