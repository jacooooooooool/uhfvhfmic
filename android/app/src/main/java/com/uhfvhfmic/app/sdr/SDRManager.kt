package com.uhfvhfmic.app.sdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SDRDeviceType {
    UNKNOWN,
    RTL_SDR,
    HACKRF,
    LIMESDR,
    PLUTOSDR,
    YARDSTICK
}

data class SDRDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val type: SDRDeviceType,
    val device: UsbDevice
)

enum class SDRConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    TRANSMITTING,
    ERROR
}

data class FrequencyBand(
    val name: String,
    val minFreqMHz: Double,
    val maxFreqMHz: Double,
    val defaultFreqMHz: Double
)

class SDRManager(private val context: Context) {

    companion object {
        private const val TAG = "SDRManager"
        private const val ACTION_USB_PERMISSION = "com.uhfvhfmic.app.USB_PERMISSION"

        // Supported frequency bands
        val VHF_LOW = FrequencyBand("VHF Low", 30.0, 50.0, 49.830)
        val VHF_HIGH = FrequencyBand("VHF High", 174.0, 216.0, 183.0)
        val UHF = FrequencyBand("UHF", 470.0, 698.0, 536.0)
        val FM_BROADCAST = FrequencyBand("FM Broadcast", 87.5, 108.0, 100.0)
        val WIRELESS_MIC_A = FrequencyBand("Wireless Mic A", 944.0, 952.0, 947.0)
        val WIRELESS_MIC_B = FrequencyBand("Wireless Mic B", 524.0, 608.0, 566.0)

        val ALL_BANDS = listOf(VHF_LOW, VHF_HIGH, UHF, FM_BROADCAST, WIRELESS_MIC_A, WIRELESS_MIC_B)

        // Known SDR devices
        private val KNOWN_DEVICES = mapOf(
            Pair(0x0bda, 0x2832) to SDRDeviceType.RTL_SDR,
            Pair(0x0bda, 0x2838) to SDRDeviceType.RTL_SDR,
            Pair(0x1d50, 0x6089) to SDRDeviceType.HACKRF,
            Pair(0x1d50, 0x6108) to SDRDeviceType.LIMESDR,
            Pair(0x0456, 0xb673) to SDRDeviceType.PLUTOSDR,
            Pair(0x2fa2, 0x5a02) to SDRDeviceType.PLUTOSDR,
            Pair(0x1d50, 0x605b) to SDRDeviceType.YARDSTICK
        )
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // State
    private val _connectionStatus = MutableStateFlow(SDRConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<SDRConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDevice = MutableStateFlow<SDRDeviceInfo?>(null)
    val connectedDevice: StateFlow<SDRDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<SDRDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<SDRDeviceInfo>> = _availableDevices.asStateFlow()

    private val _currentFrequencyMHz = MutableStateFlow(100.0)
    val currentFrequencyMHz: StateFlow<Double> = _currentFrequencyMHz.asStateFlow()

    private val _currentBand = MutableStateFlow(FM_BROADCAST)
    val currentBand: StateFlow<FrequencyBand> = _currentBand.asStateFlow()

    private val _txPowerDbm = MutableStateFlow(0)
    val txPowerDbm: StateFlow<Int> = _txPowerDbm.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private var usbConnection: UsbDeviceConnection? = null
    private var currentUsbDevice: UsbDevice? = null

    // USB permission receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectToDevice(it) }
                        } else {
                            _errorMessage.value = "USB permission denied"
                            _connectionStatus.value = SDRConnectionStatus.ERROR
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (device?.deviceId == currentUsbDevice?.deviceId) {
                        disconnect()
                    }
                    refreshDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        refreshDevices()
    }

    fun refreshDevices() {
        val devices = mutableListOf<SDRDeviceInfo>()

        usbManager.deviceList.values.forEach { device ->
            val type = KNOWN_DEVICES[Pair(device.vendorId, device.productId)] ?: SDRDeviceType.UNKNOWN

            if (type != SDRDeviceType.UNKNOWN) {
                val deviceInfo = SDRDeviceInfo(
                    name = device.productName ?: getDeviceTypeName(type),
                    vendorId = device.vendorId,
                    productId = device.productId,
                    type = type,
                    device = device
                )
                devices.add(deviceInfo)
            }
        }

        _availableDevices.value = devices
        Log.d(TAG, "Found ${devices.size} SDR devices")
    }

    private fun getDeviceTypeName(type: SDRDeviceType): String {
        return when (type) {
            SDRDeviceType.RTL_SDR -> "RTL-SDR"
            SDRDeviceType.HACKRF -> "HackRF One"
            SDRDeviceType.LIMESDR -> "LimeSDR"
            SDRDeviceType.PLUTOSDR -> "PlutoSDR"
            SDRDeviceType.YARDSTICK -> "YARD Stick One"
            SDRDeviceType.UNKNOWN -> "Unknown SDR"
        }
    }

    fun requestConnection(deviceInfo: SDRDeviceInfo) {
        _connectionStatus.value = SDRConnectionStatus.CONNECTING

        if (usbManager.hasPermission(deviceInfo.device)) {
            connectToDevice(deviceInfo.device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(deviceInfo.device, permissionIntent)
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        try {
            usbConnection = usbManager.openDevice(device)
            if (usbConnection != null) {
                currentUsbDevice = device
                val type = KNOWN_DEVICES[Pair(device.vendorId, device.productId)] ?: SDRDeviceType.UNKNOWN

                _connectedDevice.value = SDRDeviceInfo(
                    name = device.productName ?: getDeviceTypeName(type),
                    vendorId = device.vendorId,
                    productId = device.productId,
                    type = type,
                    device = device
                )
                _connectionStatus.value = SDRConnectionStatus.CONNECTED

                // Initialize the device based on type
                initializeDevice(type)

                Log.d(TAG, "Connected to ${device.productName}")
            } else {
                _errorMessage.value = "Failed to open USB device"
                _connectionStatus.value = SDRConnectionStatus.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            _errorMessage.value = "Connection error: ${e.message}"
            _connectionStatus.value = SDRConnectionStatus.ERROR
        }
    }

    private fun initializeDevice(type: SDRDeviceType) {
        // Device-specific initialization
        when (type) {
            SDRDeviceType.HACKRF -> initHackRF()
            SDRDeviceType.RTL_SDR -> initRtlSdr()
            SDRDeviceType.LIMESDR -> initLimeSdr()
            SDRDeviceType.PLUTOSDR -> initPlutoSdr()
            else -> Log.d(TAG, "Generic SDR initialization")
        }
    }

    private fun initHackRF() {
        // HackRF supports TX
        Log.d(TAG, "Initializing HackRF for TX")
        // Send initialization commands via USB
    }

    private fun initRtlSdr() {
        // RTL-SDR is RX only - show warning
        Log.d(TAG, "RTL-SDR detected - RX only device")
        _errorMessage.value = "RTL-SDR is receive-only. Use HackRF or similar for transmission."
    }

    private fun initLimeSdr() {
        Log.d(TAG, "Initializing LimeSDR for TX")
    }

    private fun initPlutoSdr() {
        Log.d(TAG, "Initializing PlutoSDR for TX")
    }

    fun disconnect() {
        stopTransmitting()
        usbConnection?.close()
        usbConnection = null
        currentUsbDevice = null
        _connectedDevice.value = null
        _connectionStatus.value = SDRConnectionStatus.DISCONNECTED
    }

    fun setFrequency(frequencyMHz: Double) {
        val band = _currentBand.value
        val clampedFreq = frequencyMHz.coerceIn(band.minFreqMHz, band.maxFreqMHz)
        _currentFrequencyMHz.value = clampedFreq

        // Send frequency command to hardware
        sendFrequencyCommand(clampedFreq)
    }

    fun setBand(band: FrequencyBand) {
        _currentBand.value = band
        _currentFrequencyMHz.value = band.defaultFreqMHz
        sendFrequencyCommand(band.defaultFreqMHz)
    }

    fun setTxPower(powerDbm: Int) {
        _txPowerDbm.value = powerDbm.coerceIn(-40, 20)
        sendPowerCommand(_txPowerDbm.value)
    }

    private fun sendFrequencyCommand(frequencyMHz: Double) {
        val device = _connectedDevice.value ?: return
        val connection = usbConnection ?: return

        val frequencyHz = (frequencyMHz * 1_000_000).toLong()
        Log.d(TAG, "Setting frequency to $frequencyMHz MHz ($frequencyHz Hz)")

        // Device-specific frequency setting
        when (device.type) {
            SDRDeviceType.HACKRF -> {
                // HackRF frequency command
                // This would be actual USB control transfer
            }
            else -> {}
        }
    }

    private fun sendPowerCommand(powerDbm: Int) {
        Log.d(TAG, "Setting TX power to $powerDbm dBm")
    }

    fun startTransmitting() {
        if (_connectionStatus.value != SDRConnectionStatus.CONNECTED) {
            _errorMessage.value = "No SDR device connected"
            return
        }

        val device = _connectedDevice.value
        if (device?.type == SDRDeviceType.RTL_SDR) {
            _errorMessage.value = "RTL-SDR cannot transmit. Use HackRF or similar."
            return
        }

        _isTransmitting.value = true
        _connectionStatus.value = SDRConnectionStatus.TRANSMITTING
        Log.d(TAG, "Started transmitting on ${_currentFrequencyMHz.value} MHz")
    }

    fun stopTransmitting() {
        _isTransmitting.value = false
        if (_connectionStatus.value == SDRConnectionStatus.TRANSMITTING) {
            _connectionStatus.value = SDRConnectionStatus.CONNECTED
        }
        Log.d(TAG, "Stopped transmitting")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun canTransmit(): Boolean {
        val device = _connectedDevice.value ?: return false
        return device.type != SDRDeviceType.RTL_SDR && device.type != SDRDeviceType.UNKNOWN
    }

    fun cleanup() {
        try {
            disconnect()
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
