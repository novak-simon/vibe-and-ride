package com.novaksimon.vibeandride.workout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCadenceSource : CadenceSource {
    private val _cadence = MutableStateFlow<Double?>(null)
    override val cadence: StateFlow<Double?> = _cadence.asStateFlow()

    private val _status = MutableStateFlow(CadenceSourceStatus.IDLE)
    override val status: StateFlow<CadenceSourceStatus> = _status.asStateFlow()

    fun emitCadence(value: Double?) {
        _cadence.value = value
    }

    fun emitStatus(value: CadenceSourceStatus) {
        _status.value = value
    }

    override fun startScanning() {
        _status.value = CadenceSourceStatus.SCANNING
    }

    override fun stopScanning() {
        _status.value = CadenceSourceStatus.IDLE
    }

    override fun disconnect() {
        _status.value = CadenceSourceStatus.IDLE
    }
}
