package com.novaksimon.vibeandride

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform