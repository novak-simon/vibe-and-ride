package com.novaksimon.vibeandride.workout

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.novaksimon.vibeandride.util.VibeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Android implementation of [CadenceSource] using Bluetooth Low Energy.
 */
@SuppressLint("MissingPermission")
class AndroidBluetoothCadenceSource(
    private val context: Context,
) : CadenceSource {
    private val TAG = "BleCadenceSource"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _cadence = MutableStateFlow<Double?>(null)
    override val cadence: StateFlow<Double?> = _cadence.asStateFlow()

    private val _power = MutableStateFlow<Double?>(null)
    override val power: StateFlow<Double?> = _power.asStateFlow()

    private val _status = MutableStateFlow(CadenceSourceStatus.IDLE)
    override val status: StateFlow<CadenceSourceStatus> = _status.asStateFlow()

    private var activeGatt: BluetoothGatt? = null

    private val cscServiceUuid = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    private val cscMeasurementCharUuid = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    
    private val cpServiceUuid = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    private val cpMeasurementCharUuid = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")

    private val clientCharacteristicConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var lastCrankRevolutions = -1
    private var lastCrankTime = -1

    private val descriptorWriteQueue = mutableListOf<BluetoothGattDescriptor>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unknown"
            
            VibeLog.d(TAG, "Discovered BLE: $name (${device.address})")
            
            var hasTargetUuid = false
            val recordStr = result.scanRecord?.toString() ?: ""
            if (recordStr.contains("1816") || recordStr.contains("1818")) {
                hasTargetUuid = true
            }
            
            val matchesName = name.contains("Wahoo", ignoreCase = true) || 
                             name.contains("Trainer", ignoreCase = true) ||
                             name.contains("KICKR", ignoreCase = true) ||
                             name.contains("CORE", ignoreCase = true)
            
            if (hasTargetUuid || matchesName) {
                VibeLog.i(TAG, "Found matching BLE trainer: $name (${device.address}). Connecting...")
                stopScanning()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            VibeLog.e(TAG, "Scan failed with error: $errorCode")
            _status.value = CadenceSourceStatus.ERROR
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                VibeLog.i(TAG, "GATT Connected to ${gatt.device.name ?: "Unknown"}. Discovering services...")
                _status.value = CadenceSourceStatus.CONNECTING
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                VibeLog.i(TAG, "GATT Disconnected from ${gatt.device.name ?: "Unknown"}. Status: $status")
                _status.value = CadenceSourceStatus.IDLE
                activeGatt = null
                _cadence.value = null
                _power.value = null
                descriptorWriteQueue.clear()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                VibeLog.i(TAG, "GATT Services discovered for ${gatt.device.name ?: "Unknown"}")
                setupNotifications(gatt)
            } else {
                VibeLog.e(TAG, "GATT Service discovery failed for ${gatt.device.name ?: "Unknown"}: $status")
                _status.value = CadenceSourceStatus.ERROR
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            VibeLog.d(TAG, "GATT onDescriptorWrite: status=$status characteristic=${descriptor.characteristic.uuid}")
            processDescriptorQueue(gatt)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            parseCharacteristic(characteristic, null)
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            parseCharacteristic(characteristic, value)
        }
    }

    override fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            VibeLog.e(TAG, "Bluetooth adapter not available or disabled")
            _status.value = CadenceSourceStatus.ERROR
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            VibeLog.e(TAG, "Bluetooth LE Scanner not available")
            _status.value = CadenceSourceStatus.ERROR
            return
        }

        _status.value = CadenceSourceStatus.SCANNING
        
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        VibeLog.i(TAG, "Starting BLE scan (Internal filtering)...")
        scanner.startScan(filters, settings, scanCallback)
    }

    override fun stopScanning() {
        VibeLog.i(TAG, "Stopping BLE scan")
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_status.value == CadenceSourceStatus.SCANNING) {
            _status.value = CadenceSourceStatus.IDLE
        }
    }

    override fun disconnect() {
        VibeLog.i(TAG, "Disconnecting from BLE device")
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _status.value = CadenceSourceStatus.IDLE
        _cadence.value = null
        _power.value = null
        descriptorWriteQueue.clear()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        VibeLog.i(TAG, "Initiating connection to ${device.name ?: "Unknown"} (${device.address})")
        _status.value = CadenceSourceStatus.CONNECTING
        @Suppress("DEPRECATION")
        activeGatt = device.connectGatt(context, false, gattCallback)
    }

    private fun setupNotifications(gatt: BluetoothGatt) {
        descriptorWriteQueue.clear()

        // 1. Try to find CSC service
        val cscService = gatt.getService(cscServiceUuid)
        cscService?.getCharacteristic(cscMeasurementCharUuid)?.let { char ->
            VibeLog.d(TAG, "GATT: Found CSC characteristic. Adding to subscription queue.")
            gatt.setCharacteristicNotification(char, true)
            char.getDescriptor(clientCharacteristicConfigUuid)?.let { desc ->
                descriptorWriteQueue.add(desc)
            }
        }

        // 2. Try to find CP service
        val cpService = gatt.getService(cpServiceUuid)
        cpService?.getCharacteristic(cpMeasurementCharUuid)?.let { char ->
            VibeLog.d(TAG, "GATT: Found CP characteristic. Adding to subscription queue.")
            gatt.setCharacteristicNotification(char, true)
            char.getDescriptor(clientCharacteristicConfigUuid)?.let { desc ->
                descriptorWriteQueue.add(desc)
            }
        }

        if (descriptorWriteQueue.isNotEmpty()) {
            processDescriptorQueue(gatt)
            _status.value = CadenceSourceStatus.CONNECTED
        } else {
            VibeLog.e(TAG, "Neither CSC nor CP measurement characteristic found on ${gatt.device.name ?: "Unknown"}")
            _status.value = CadenceSourceStatus.ERROR
        }
    }

    private fun processDescriptorQueue(gatt: BluetoothGatt) {
        if (descriptorWriteQueue.isEmpty()) {
            VibeLog.d(TAG, "GATT: All descriptor writes complete.")
            return
        }

        val descriptor = descriptorWriteQueue.removeAt(0)
        @Suppress("DEPRECATION")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        VibeLog.d(TAG, "GATT: Writing descriptor for ${descriptor.characteristic.uuid}")
        @Suppress("DEPRECATION")
        gatt.writeDescriptor(descriptor)
    }

    private fun parseCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray? = null) {
        @Suppress("DEPRECATION")
        val data = value ?: characteristic.value ?: return
        VibeLog.d(TAG, "GATT parseCharacteristic: UUID=${characteristic.uuid} DataSize=${data.size}")
        
        when (characteristic.uuid) {
            cscMeasurementCharUuid -> {
                val flags = data[0].toInt()
                val hasCrankData = (flags and 0x02) != 0
                
                if (hasCrankData) {
                    val hasWheelData = (flags and 0x01) != 0
                    val offset = if (hasWheelData) 7 else 1 
                    if (data.size >= offset + 4) {
                        val cumulativeCrankRevs = unsignedShortToInt(data[offset], data[offset + 1])
                        val lastCrankEventTime = unsignedShortToInt(data[offset + 2], data[offset + 3])
                        calculateCscCadence(cumulativeCrankRevs, lastCrankEventTime)
                    }
                }
            }
            cpMeasurementCharUuid -> {
                val flags = unsignedShortToInt(data[0], data[1])
                
                // Instantaneous Power is always present in bytes 2-3
                if (data.size >= 4) {
                    val instantaneousPower = unsignedShortToInt(data[2], data[3]) // Actually sint16, but usually positive
                    _power.value = instantaneousPower.toDouble()
                    VibeLog.d(TAG, "GATT CP Power Data: $instantaneousPower W")
                }

                // Bit 5 (0x20): Crank Revolution Data
                val hasCrankData = (flags and 0x20) != 0
                if (hasCrankData) {
                    var offset = 4 
                    if ((flags and 0x01) != 0) offset += 1 // Pedal Power Balance
                    if ((flags and 0x04) != 0) offset += 2 // Accumulated Torque
                    if ((flags and 0x10) != 0) offset += 6 // Wheel Revolution Data
                    
                    if (data.size >= offset + 4) {
                        val cumulativeCrankRevs = unsignedShortToInt(data[offset], data[offset + 1])
                        val lastCrankEventTime = unsignedShortToInt(data[offset + 2], data[offset + 3])
                        VibeLog.d(TAG, "GATT CP Crank Data: Revs=$cumulativeCrankRevs Time=$lastCrankEventTime")
                        calculateCscCadence(cumulativeCrankRevs, lastCrankEventTime)
                    }
                }
            }
        }
    }

    private fun calculateCscCadence(revs: Int, time: Int) {
        if (lastCrankRevolutions != -1) {
            val deltaRevs = if (revs >= lastCrankRevolutions) {
                revs - lastCrankRevolutions
            } else {
                (65535 - lastCrankRevolutions) + revs
            }
            
            val deltaTime = if (time >= lastCrankTime) {
                time - lastCrankTime
            } else {
                (65535 - lastCrankTime) + time
            }
            
            if (deltaTime > 0 && deltaRevs > 0) {
                val rpm = (deltaRevs.toDouble() * 1024.0 * 60.0) / deltaTime.toDouble()
                VibeLog.d(TAG, "GATT Calculated RPM: $rpm (DeltaRevs=$deltaRevs DeltaTime=$deltaTime)")
                if (rpm < 200) { 
                    _cadence.value = rpm
                }
            } else if (deltaTime > 2048) { 
                _cadence.value = 0.0
            }
        }
        lastCrankRevolutions = revs
        lastCrankTime = time
    }

    private fun unsignedShortToInt(b1: Byte, b2: Byte): Int {
        return (b1.toInt() and 0xFF) or ((b2.toInt() and 0xFF) shl 8)
    }
}
