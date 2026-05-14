package com.elozino.splitify

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun openAppSettings()
