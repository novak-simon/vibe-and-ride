package com.novaksimon.vibeandride.workout

import kotlinx.coroutines.flow.StateFlow

enum class CadenceSourceStatus {
    IDLE, SCANNING, CONNECTING, CONNECTED, ERROR
}

interface CadenceSource {
    val cadence: StateFlow<Double?>
    val status: StateFlow<CadenceSourceStatus>
    
    fun startScanning()
    fun stopScanning()
    fun disconnect()
}
