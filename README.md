This is a fork of the original application, which turned out to be a sham. Virtually everything has been changed—except for the appearance—with many new features added, including full support for the LimeSDR Mini via libusb and LimeSuiteNg. 
The application can function as a simple walkie-talkie. 

## Note: The source code currently reflects the "original" state, while the output file, `app-debug-jacek.apk`, includes the changes listed below. I will publish the complete source code at a later time.

Report of the changes I introduced:

# Technical Report: UHF/VHF Mic – Mobile SDR Transceiver (TX/RX) for LimeSDR Mini on Android 10, Walkie-Talkie! (without root privileges)

## 1. General Description and Application Purpose

UHF/VHF Mic is a native Android application (Android 10 / API 29, ARM64-v8a architecture) that transforms a smartphone with a LimeSDR Mini v1.3 module (FTDI FT601 + Altera MAX10 FPGA + LMS7002M) connected via USB OTG into a two-way, multi-system software-defined radio (Half-Duplex SDR Transceiver).
The application runs completely without administrator privileges (root) and without an external PC, supporting bands from 50 MHz – 960 MHz (VHF Low, FM broadcast band 88–108 MHz, VHF High 136–174 MHz, UHF 400–470 MHz, PMR446, and UHF High 860–960 MHz).

## 2. Full List of Implemented Features

1. **Direct LimeSDR Mini support via USB OTG (no root):**
	* Automatic detection of FTDI FT601 (`0x0403:0x601F`) and OpenMoko (`0x1D50:0x60A7`, `0x60A8`, `0x6108`) chips.
	* Android USB Host permission management and safe passing of the file descriptor to `libusb` and `LimeSuiteNG`.
2. **Multi-system TX transmitter (WFM / NFM / AM / USB / LSB):**
	* **WFM (Wide FM):** full ±75 kHz deviation for FM broadcast receivers.
	* **NFM (Narrow FM):** ±5 kHz deviation for PMR446 walkie-talkies and 2m / 70cm amateur radio bands.
	* **AM (Amplitude Modulation):** amplitude modulation with carrier.
	* **USB / LSB (Single Sideband):** single-sideband modulation based on a 15-tap FIR Hilbert transform.
3. **Subaudible CTCSS tone generator:**
	* Built-in table of 22 standard tones (OFF, 67.0 Hz – 250.3 Hz, including the European 88.5 Hz), mixed at ~12% deviation level in WFM and NFM modes for opening amateur radio repeaters.
4. **Half-Duplex RX receiver (RX Monitor):**
	* Native C++ receiver thread (`RFStream::StreamRx`) with 2.4 MS/s → 48 kHz (×50) decimation, quadrature FM demodulator, envelope AM demodulator, SSB (USB/LSB) product detector, and Squelch gate.
	* Automatic RX ↔ TX switching: initiating transmission immediately stops reception and switches the LMS7002M chip into TX mode.
5. **WAV audio recorder and over-the-air player (Play-to-Air):**
	* Saves microphone audio to 48 kHz, 16-bit PCM Mono `.wav` files in the `/sdcard/Android/data/com.uhfvhfmic.app/files/Music/UHFVHFMic/` directory.
	* Play (▶) / Stop (■) button next to each recording in the Recordings tab, which simultaneously plays back the audio for monitoring and routes the modulated signal directly to the LimeSDR Mini (automatically muting the microphone during playback).

## 3. Signal Flow Architecture (DSP & Hardware Pipeline)

All real-time operations run on a common base clock of 48 kHz (audio) and 2.4 MS/s (RF I/Q), resulting in an exact integer decimation and interpolation factor of 50 (`2,400,000 / 50 = 48,000`).

```text
TRANSMIT PATH (TX):
[AudioRecord 48 kHz] or [.wav File 48 kHz]
        │
        ▼  (FMModulator.kt - Singleton)
  • Audio Gain, Squelch gate, pre-emphasis
  • CTCSS tone mixer (67.0–250.3 Hz)
  • I/Q Modulator: WFM (±75 kHz) / NFM (±5 kHz) / AM / USB / LSB
        │
        ▼  (TransmitService.kt -> JNI: LimeNative.nativeSendSamples)
  [lime_jni.cpp -> lime_device.cpp]
  • Interpolation (upsampling ×50): 48 kHz -> 2.4 MS/s (lime::complex16_t)
  • RFStream::StreamTx() -> FT601 USB 3.0 -> FPGA MAX10 -> LMS7002M (BAND2, 0..73 dB)

RECEIVE PATH (RX HALF-DUPLEX):
[LMS7002M LNAW (path=2) -> FPGA MAX10 -> RFStream::StreamRx 2.4 MS/s]
        │
        ▼  (rx_worker_loop thread in lime_device.cpp)
  • Averaging decimation (÷50): 2.4 MS/s -> 48 kHz
  • FM / AM / USB / LSB demodulation + Squelch -> FIFO queue
        │
        ▼  (JNI: LimeNative.nativeReadRxAudio)
  [SDRManager.kt -> Android AudioTrack 48 kHz Mono]
```

## 4. Key Source Code Modifications

### A. Native Layer: LimeSuiteNG and libusb (`native/LimeSuiteNG/`)

* `native/LimeSuiteNG/src/comms/USB/UnixUsb.h` and `UnixUsb.cpp`:
	* Added `UnixUsb::SetAndroidUsbFd(int fd)` and `UnixUsb::GetAndroidUsbFd()` methods.
	* In `SessionRefCountIncrement()`, added `libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY)` before calling `libusb_init(&gContextLibUsb)` — this is mandatory on non-rooted Android 10 so that `libusb` does not attempt to scan the SELinux-blocked `/dev/bus/usb` directory.
	* In `UnixUsb::enumerateDevices()` and `UnixUsb::Connect()`, implemented `libusb_wrap_sys_device(gContextLibUsb, static_cast<intptr_t>(dup(androidFd)), &handle)`. Duplicating the file descriptor via `dup()` prevents the Android `fdsan` (File Descriptor Sanitizer) mechanism from killing the process during repeated opening of the FT601 interface by `DeviceFactoryFTDI::make()`.

### B. JNI Layer and Device Driver (`app/src/main/cpp/`)

* `lime_device.cpp` and `lime_device.h`:
	* `lime_device_init()` — registers the LimeSuiteNG logger in Android logcat (`lime::registerLogHandler`) and sets `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`.
	* `lime_device_connect()` / `lime_device_initialize()` — passes `fd` to `UnixUsb`, discovers the device via `DeviceRegistry::enumerate()` / `makeDevice()`, and initializes the chip via `g_sdr_device->Init()`.
	* `lime_device_configure_tx()` — configures frequency, 2.4 MS/s sample rate, `BAND2` antenna path (`path = 2`, `SetAntenna(..., 2)`) covering 10 MHz – 2 GHz, and the full 0–73 dB gain range (`eGainTypes::UNKNOWN`). During active transmission, it dynamically adjusts power/frequency (`SetGain` / `SetFrequency`) without recalibrating the FPGA.
	* `lime_device_start_tx()` / `lime_device_send_samples()` — manages the `RFStream` (`DataFormat::I16`, `bufferSize = 65536`) with ×50 interpolation.
	* `lime_device_start_rx()` / `lime_device_stop_rx()` / `lime_device_read_rx_audio()` — implements the RX receiver in a separate C++ thread with ÷50 decimation.
* `lime_jni.cpp` and `LimeNative.kt`:
	* Explicit library loading in dependency order: `usb-1.0` → `limesuiteng` → `lime_sdr_native`.
	* All `external fun` methods and the `onDeviceStatusChanged(Int)` callback are annotated with `@JvmStatic`, ensuring JNI signature compliance (`Java_com_uhfvhfmic_app_sdr_LimeNative_*`).

### C. Android Application Layer (`app/src/main/java/com/uhfvhfmic/app/`)

* `SDRManager.kt` and `FMModulator.kt` (`getInstance(context)` Singletons):
	* Shared between the Jetpack Compose UI (`UHFVHFMicApp.kt`) and `TransmitService.kt`, preventing two threads from simultaneously opening the USB port and avoiding `AudioRecord` microphone access conflicts.
	* Throttling UI audio level meter updates to 20 Hz (50 ms) eliminates interface lag during transmission.
* `TransmitService.kt`:
	* ForegroundService that starts audio capture upon transmission start and releases all resources (USB, RF stream, notification) in `onTaskRemoved()`.

## 5. Directory Structure and Build Instructions

```text
C:\Users\Jacek\StudioProjects\uhfvhfmic\android\
├── native/
│   ├── libusb/                  # libusb-1.0 library (ARM64)
│   └── LimeSuiteNG/             # Modified LimeSuiteNG sources (UnixUsb.h / UnixUsb.cpp)
├── build-android/arm64-v8a/
│   ├── libusb/                  # CMake build for libusb
│   └── limesuiteng/             # CMake build for LimeSuiteNG
└── app/
    └── src/main/
        ├── cpp/                 # CMakeLists.txt, lime_jni.cpp, lime_device.cpp, lime_device.h
        ├── jniLibs/arm64-v8a/   # libusb-1.0.so, liblimesuiteng.so
        ├── java/com/uhfvhfmic/app/
        │   ├── MainActivity.kt
        │   ├── sdr/             # LimeNative.kt, SDRManager.kt
        │   ├── audio/           # FMModulator.kt
        │   ├── service/         # TransmitService.kt
        │   └── ui/              # UHFVHFMicApp.kt
        └── res/xml/
            └── device_filter.xml # USB Host filters: 0x0403:0x601F (FT601) and 0x1D50
```

### Build and Deployment Commands (Windows + Git Bash)

Build procedure (Windows + Android Studio + Git Bash environment)

**NOTE !!!**
Remember to use a good USB-OTG cable! Tuning the frequency in the app causes LAGS because the LimeSDR is tuned "live". Tested and working on my OnePlus 7 Pro and Samsung S9+; the app only requires microphone permission. I am providing a compiled APK for testing.
