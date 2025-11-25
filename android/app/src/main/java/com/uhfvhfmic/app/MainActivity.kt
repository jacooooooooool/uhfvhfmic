package com.uhfvhfmic.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.uhfvhfmic.app.audio.FMModulator
import com.uhfvhfmic.app.sdr.SDRManager
import com.uhfvhfmic.app.service.TransmitService
import com.uhfvhfmic.app.ui.UHFVHFMicApp
import com.uhfvhfmic.app.ui.theme.UHFVHFMicTheme

/**
 * UHF/VHF Wireless Microphone App
 * Transmits audio via SDR hardware on UHF/VHF frequencies
 *
 * © 2024 Iwinosa Aigbokhaevbo. All Rights Reserved.
 */
class MainActivity : ComponentActivity() {

    private var transmitService: TransmitService? = null
    private var serviceBound by mutableStateOf(false)

    private lateinit var sdrManager: SDRManager
    private lateinit var fmModulator: FMModulator

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TransmitService.LocalBinder
            transmitService = binder.getService()
            serviceBound = true

            // Use service's managers if available
            transmitService?.getSDRManager()?.let { sdrManager = it }
            transmitService?.getFMModulator()?.let { fmModulator = it }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transmitService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            bindTransmitService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers (will be replaced by service managers when bound)
        sdrManager = SDRManager(this)
        fmModulator = FMModulator(this)

        checkAndRequestPermissions()

        setContent {
            UHFVHFMicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UHFVHFMicApp(
                        sdrManager = sdrManager,
                        fmModulator = fmModulator,
                        onStartTransmit = { startTransmitting() },
                        onStopTransmit = { stopTransmitting() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            bindTransmitService()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun bindTransmitService() {
        val intent = Intent(this, TransmitService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startTransmitting() {
        val intent = Intent(this, TransmitService::class.java).apply {
            action = TransmitService.ACTION_START_TRANSMIT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTransmitting() {
        val intent = Intent(this, TransmitService::class.java).apply {
            action = TransmitService.ACTION_STOP_TRANSMIT
        }
        startService(intent)
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        sdrManager.cleanup()
        super.onDestroy()
    }
}
