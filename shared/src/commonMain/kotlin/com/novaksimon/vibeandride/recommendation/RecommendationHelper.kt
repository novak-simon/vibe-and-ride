package com.novaksimon.vibeandride.recommendation

import kotlin.math.abs

fun clamp01(x: Float, y: Float = 1.0f): Float {
    return when {
        x < 0f -> 0f
        x > y -> y
        else -> x
    }
}

fun clamp02(x: Float, y: Float = 1.0f): Float {
    return when {
        x < -y -> -y
        x > y -> y
        else -> x
    }
}

fun round3(x: Float): Float {
    return "%.3f".format(x).toFloat()
}

fun calculateBpmMatch(songBpm: Float?, targetCadence: Double?, bpmTolerance: Float): BPMMatch {
    if (songBpm == null || targetCadence == null) {
        return BPMMatch(compatible = false, effectiveBpm = null, difference = null, multiplier = null)
    }

    var bestMult: Float? = null
    var bestEff: Float? = null
    var bestDiff: Float? = null

    for (mult in floatArrayOf(0.5f, 1.0f, 2.0f)) {
        val eff = songBpm * mult
        val diff = abs(eff - targetCadence.toFloat())
        if (bestDiff == null || diff < bestDiff) {
            bestDiff = diff
            bestEff = eff
            bestMult = mult
        }
    }

    val compatible = bestDiff != null && bestDiff <= bpmTolerance
    return BPMMatch(compatible = compatible, effectiveBpm = bestEff, difference = bestDiff, multiplier = bestMult)
}

fun getAlgorithmCadence(cadence: Int): Int {
    val maxIterations = 20
    var iterations = 0
    var c = cadence

    while (c !in 60..120 && iterations < maxIterations) {
        c = if (c < 60) c * 2 else c / 2
        iterations++
    }

    if (iterations >= maxIterations) {
        // logger.warn equivalent — see note below
        c = 120
    }

    return c
}