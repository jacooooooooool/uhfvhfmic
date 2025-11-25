package com.uhfvhfmic.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uhfvhfmic.app.MainActivity
import com.uhfvhfmic.app.R
import com.uhfvhfmic.app.audio.FMModulator
import com.uhfvhfmic.app.sdr.SDRManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for FM transmission
 * Handles audio capture and SDR transmission in background
 * © 2024 Iwinosa Aigbokhaevbo. All Rights Reserved.
 */
class TransmitService : Service() {

    companion object {
        private const val TAG = "TransmitService"
        private const val CHANNEL_ID = "uhfvhf_transmit_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_TRANSMIT = "com.uhfvhfmic.app.START_TRANSMIT"
        const val ACTION_STOP_TRANSMIT = "com.uhfvhfmic.app.STOP_TRANSMIT"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sdrManager: SDRManager? = null
    private var fmModulator: FMModulator? = null

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TransmitService = this@TransmitService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sdrManager = SDRManager(this)
        fmModulator = FMModulator(this)

        // Connect modulator output to SDR
        fmModulator?.onModulatedData = { data ->
            sendToSDR(data)
        }

        Log.d(TAG, "TransmitService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRANSMIT -> startTransmitting()
            ACTION_STOP_TRANSMIT -> stopTransmitting()
        }
        return START_STICKY
    }

    fun getSDRManager(): SDRManager? = sdrManager
    fun getFMModulator(): FMModulator? = fmModulator

    fun startTransmitting() {
        if (_isTransmitting.value) return

        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Start audio capture and modulation
        fmModulator?.startCapture()

        // Enable TX on SDR
        sdrManager?.startTransmitting()

        _isTransmitting.value = true
        Log.d(TAG, "Transmission started")
    }

    fun stopTransmitting() {
        _isTransmitting.value = false

        // Stop audio capture
        fmModulator?.stopCapture()

        // Stop TX on SDR
        sdrManager?.stopTransmitting()

        // Stop foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        Log.d(TAG, "Transmission stopped")
    }

    private fun sendToSDR(data: ByteArray) {
        // Send IQ data to SDR hardware via USB
        // This would be the actual USB bulk transfer
        val connection = sdrManager?.connectedDevice?.value ?: return
        // usbConnection?.bulkTransfer(endpoint, data, data.size, 1000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UHF/VHF Transmission",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when transmitting on UHF/VHF frequencies"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        val frequency = sdrManager?.currentFrequencyMHz?.value ?: 100.0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UHF/VHF Mic Transmitting")
            .setContentText("Frequency: ${"%.3f".format(frequency)} MHz")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopTransmitting()
        fmModulator?.onModulatedData = null
        sdrManager?.cleanup()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "TransmitService destroyed")
    }
}
