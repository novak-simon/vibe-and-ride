package com.novaksimon.vibeandride.model

fun estimateCadenceFromPower(power: Float?): Int {
    val p = power ?: return 80 // matches Python's except-branch default
    val cadence = 70 + 30 * p
    return cadence.coerceIn(75f, 115f).toInt()
}