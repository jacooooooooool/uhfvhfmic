package com.uhfvhfmic.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FM Modulator for UHF/VHF transmission
 * Captures audio from microphone and modulates it for FM transmission
 */
class FMModulator(private val context: Context) {

    companion object {
        private const val TAG = "FMModulator"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // FM modulation parameters
        private const val FM_DEVIATION_HZ = 75000.0 // 75 kHz deviation for broadcast FM
        private const val NARROW_FM_DEVIATION_HZ = 5000.0 // 5 kHz for narrow FM (wireless mics)
    }

    private var audioRecord: AudioRecord? = null
    private var modulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // State
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Modulation settings
    private val _preEmphasis = MutableStateFlow(true)
    val preEmphasis: StateFlow<Boolean> = _preEmphasis.asStateFlow()

    private val _stereoEnabled = MutableStateFlow(false)
    val stereoEnabled: StateFlow<Boolean> = _stereoEnabled.asStateFlow()

    private val _narrowFM = MutableStateFlow(true) // Use narrow FM for wireless mics
    val narrowFM: StateFlow<Boolean> = _narrowFM.asStateFlow()

    private val _audioGain = MutableStateFlow(1.0f)
    val audioGain: StateFlow<Float> = _audioGain.asStateFlow()

    private val _squelchLevel = MutableStateFlow(0.05f)
    val squelchLevel: StateFlow<Float> = _squelchLevel.asStateFlow()

    // Recording
    private var recordingFile: File? = null
    private var recordingOutputStream: FileOutputStream? = null
    private var totalBytesWritten: Long = 0
    private var recordingTimerJob: Job? = null

    // FM modulation state
    private var phase = 0.0

    // Callback for modulated data
    var onModulatedData: ((ByteArray) -> Unit)? = null

    fun startCapture() {
        if (_isCapturing.value) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            _errorMessage.value = "Microphone permission not granted"
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = minBufferSize * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _errorMessage.value = "Failed to initialize audio capture"
                return
            }

            audioRecord?.startRecording()
            _isCapturing.value = true

            modulationJob = scope.launch {
                processAudio()
            }

            Log.d(TAG, "Audio capture started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture", e)
            _errorMessage.value = "Capture error: ${e.message}"
        }
    }

    fun stopCapture() {
        _isCapturing.value = false
        stopRecording()

        modulationJob?.cancel()
        modulationJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        audioRecord = null

        Log.d(TAG, "Audio capture stopped")
    }

    private suspend fun processAudio() {
        val bufferSize = 1024
        val audioBuffer = ShortArray(bufferSize)
        val modulatedBuffer = ByteArray(bufferSize * 4) // IQ data is larger

        while (_isCapturing.value) {
            val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

            if (readResult > 0) {
                // Calculate input level
                val level = calculateLevel(audioBuffer, readResult)
                _inputLevel.value = level

                // Apply squelch
                if (level < _squelchLevel.value) {
                    // Below squelch - send silence
                    continue
                }

                // Apply gain
                applyGain(audioBuffer, readResult)

                // Apply pre-emphasis if enabled
                if (_preEmphasis.value) {
                    applyPreEmphasis(audioBuffer, readResult)
                }

                // FM modulate the audio
                val modulatedSize = fmModulate(audioBuffer, readResult, modulatedBuffer)

                // Send to SDR hardware
                onModulatedData?.invoke(modulatedBuffer.copyOf(modulatedSize))

                // Record if enabled
                if (_isRecording.value) {
                    writeToRecording(audioBuffer, readResult)
                }
            }
        }
    }

    private fun calculateLevel(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        val rms = kotlin.math.sqrt(sum / size)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    private fun applyGain(buffer: ShortArray, size: Int) {
        val gain = _audioGain.value
        if (gain == 1.0f) return

        for (i in 0 until size) {
            val sample = (buffer[i] * gain).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun applyPreEmphasis(buffer: ShortArray, size: Int) {
        // Simple pre-emphasis filter for FM
        // H(z) = 1 - 0.95 * z^-1
        var lastSample = 0.0
        for (i in 0 until size) {
            val input = buffer[i].toDouble()
            val output = input - 0.95 * lastSample
            lastSample = input
            buffer[i] = output.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
    }

    /**
     * FM modulate audio samples to IQ baseband
     * Returns the number of bytes written to the output buffer
     */
    private fun fmModulate(audioBuffer: ShortArray, audioSize: Int, outputBuffer: ByteArray): Int {
        val deviation = if (_narrowFM.value) NARROW_FM_DEVIATION_HZ else FM_DEVIATION_HZ
        val deviationPerSample = 2.0 * PI * deviation / SAMPLE_RATE

        var outputIndex = 0

        for (i in 0 until audioSize) {
            // Normalize audio sample to -1.0 to 1.0
            val audioSample = audioBuffer[i].toDouble() / Short.MAX_VALUE

            // Update phase based on audio amplitude (FM modulation)
            phase += deviationPerSample * audioSample

            // Keep phase in range
            if (phase > PI) phase -= 2.0 * PI
            if (phase < -PI) phase += 2.0 * PI

            // Generate I/Q samples
            val i_sample = cos(phase)
            val q_sample = sin(phase)

            // Convert to 8-bit signed (for SDR hardware)
            val i_byte = (i_sample * 127).toInt().coerceIn(-128, 127).toByte()
            val q_byte = (q_sample * 127).toInt().coerceIn(-128, 127).toByte()

            outputBuffer[outputIndex++] = i_byte
            outputBuffer[outputIndex++] = q_byte
        }

        return outputIndex
    }

    // Settings
    fun setPreEmphasis(enabled: Boolean) {
        _preEmphasis.value = enabled
    }

    fun setStereo(enabled: Boolean) {
        _stereoEnabled.value = enabled
    }

    fun setNarrowFM(enabled: Boolean) {
        _narrowFM.value = enabled
    }

    fun setAudioGain(gain: Float) {
        _audioGain.value = gain.coerceIn(0.1f, 5.0f)
    }

    fun setSquelchLevel(level: Float) {
        _squelchLevel.value = level.coerceIn(0f, 0.5f)
    }

    // Recording functions
    fun startRecording() {
        if (!_isCapturing.value || _isRecording.value) return

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "transmission_${dateFormat.format(Date())}.wav"

            val recordingsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "UHFVHFMic")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()

            recordingFile = File(recordingsDir, fileName)
            recordingOutputStream = FileOutputStream(recordingFile)

            writeWavHeader(recordingOutputStream!!, 0)
            totalBytesWritten = 0

            _isRecording.value = true
            _recordingDuration.value = 0

            recordingTimerJob = scope.launch {
                while (_isRecording.value) {
                    kotlinx.coroutines.delay(100)
                    _recordingDuration.value += 100
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _errorMessage.value = "Recording error: ${e.message}"
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        _isRecording.value = false
        recordingTimerJob?.cancel()

        try {
            recordingOutputStream?.close()
            recordingFile?.let { updateWavHeader(it, totalBytesWritten) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        recordingOutputStream = null
        recordingFile = null
    }

    private fun writeToRecording(buffer: ShortArray, size: Int) {
        try {
            val byteBuffer = ByteArray(size * 2)
            for (i in 0 until size) {
                byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
            }
            recordingOutputStream?.write(byteBuffer)
            totalBytesWritten += byteBuffer.size
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to recording", e)
        }
    }

    private fun writeWavHeader(out: FileOutputStream, dataSize: Long) {
        val header = ByteArray(44)
        val totalDataLen = dataSize + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = (SAMPLE_RATE shr 8 and 0xff).toByte()
        header[26] = (SAMPLE_RATE shr 16 and 0xff).toByte()
        header[27] = (SAMPLE_RATE shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = (dataSize shr 8 and 0xff).toByte()
        header[42] = (dataSize shr 16 and 0xff).toByte()
        header[43] = (dataSize shr 24 and 0xff).toByte()

        out.write(header)
    }

    private fun updateWavHeader(file: File, dataSize: Long) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalDataLen = dataSize + 36
                raf.seek(4)
                raf.write((totalDataLen and 0xff).toInt())
                raf.write((totalDataLen shr 8 and 0xff).toInt())
                raf.write((totalDataLen shr 16 and 0xff).toInt())
                raf.write((totalDataLen shr 24 and 0xff).toInt())
                raf.seek(40)
                raf.write((dataSize and 0xff).toInt())
                raf.write((dataSize shr 8 and 0xff).toInt())
                raf.write((dataSize shr 16 and 0xff).toInt())
                raf.write((dataSize shr 24 and 0xff).toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }

    fun getRecordings(): List<File> {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "UHFVHFMic")
        return if (dir.exists()) {
            dir.listFiles()?.filter { it.extension == "wav" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        return try { file.delete() } catch (e: Exception) { false }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
