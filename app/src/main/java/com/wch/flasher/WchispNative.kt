package com.wch.flasher

import android.util.Log

/**
 * JNI wrapper for the native wchisp library
 * 
 * This class provides Kotlin/Java bindings to the Rust-based WCH ISP functionality
 * with graceful fallback when native library is not available.
 */
object WchispNative {
    
    private const val TAG = "WchispNative"
    private var libraryLoaded = false
    private var loadError: String? = null
    
    init {
        try {
            System.loadLibrary("wchisp_android")
            libraryLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            libraryLoaded = false
            loadError = e.message
            Log.w(TAG, "Failed to load native library: ${e.message}")
            Log.w(TAG, "App will run in simulation mode")
        }
    }

    /**
     * Check if the native library is loaded and available
     * @return true if native library is loaded
     */
    fun isLibraryLoaded(): Boolean = libraryLoaded

    /**
     * Get the library load error message if any
     * @return error message or null if loaded successfully
     */
    fun getLoadError(): String? = loadError

    /**
     * Initialize the native library
     * @return true if initialization was successful
     */
    external fun init(): Boolean

    /**
     * Safe wrapper for init() that handles library not loaded
     */
    fun safeInit(): Boolean {
        return if (libraryLoaded) {
            try {
                init()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native init failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Native library not loaded, using simulation mode")
            true // Return true for simulation mode
        }
    }

    /**
     * Open USB device connection
     * @param deviceFd USB device file descriptor from Android USB Host API
     * @param vendorId Device vendor ID
     * @param productId Device product ID
     * @param usbConnection Android UsbDeviceConnection object
     * @return Device handle (positive integer) on success, negative on error
     */
    external fun openDevice(deviceFd: Int, vendorId: Int, productId: Int, usbConnection: Any): Int

    /**
     * Safe wrapper for openDevice that handles library not loaded
     */
    fun safeOpenDevice(deviceFd: Int, vendorId: Int, productId: Int, usbConnection: Any): Int {
        return if (libraryLoaded) {
            try {
                openDevice(deviceFd, vendorId, productId, usbConnection)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native openDevice failed: ${e.message}")
                -1
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock device handle returned")
            1 // Return mock handle for simulation
        }
    }

    /**
     * Close USB device connection
     * @param handle Device handle returned by openDevice
     * @return true if successful
     */
    external fun closeDevice(handle: Int): Boolean

    /**
     * Safe wrapper for closeDevice
     */
    fun safeCloseDevice(handle: Int): Boolean {
        return if (libraryLoaded) {
            try {
                closeDevice(handle)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native closeDevice failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock close successful")
            true
        }
    }

    /**
     * Identify the connected chip
     * @param handle Device handle
     * @return Chip identification string, or null on error
     */
    external fun identifyChip(handle: Int): String?

    /**
     * Safe wrapper for identifyChip
     */
    fun safeIdentifyChip(handle: Int): String? {
        return if (libraryLoaded) {
            try {
                identifyChip(handle)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native identifyChip failed: ${e.message}")
                null
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock CH32V203 chip identification")
            "CH32V203 (Code Flash: 64KiB) [Simulation Mode]"
        }
    }

    /**
     * Flash firmware to the chip
     * @param handle Device handle
     * @param firmwareData Firmware binary data
     * @return true if successful
     */
    external fun flashFirmware(handle: Int, firmwareData: ByteArray): Boolean

    /**
     * Safe wrapper for flashFirmware
     */
    fun safeFlashFirmware(handle: Int, firmwareData: ByteArray): Boolean {
        return if (libraryLoaded) {
            try {
                flashFirmware(handle, firmwareData)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native flashFirmware failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock flash operation (${firmwareData.size} bytes)")
            // Simulate progress delay for realistic behavior
            Thread.sleep(2000)
            true
        }
    }

    /**
     * Erase chip flash memory
     * @param handle Device handle
     * @return true if successful
     */
    external fun eraseChip(handle: Int): Boolean

    /**
     * Safe wrapper for eraseChip
     */
    fun safeEraseChip(handle: Int): Boolean {
        return if (libraryLoaded) {
            try {
                eraseChip(handle)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native eraseChip failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock erase operation")
            Thread.sleep(1000)
            true
        }
    }

    /**
     * Verify firmware on the chip
     * @param handle Device handle
     * @param firmwareData Expected firmware data for verification
     * @return true if verification passed
     */
    external fun verifyFirmware(handle: Int, firmwareData: ByteArray): Boolean

    /**
     * Safe wrapper for verifyFirmware
     */
    fun safeVerifyFirmware(handle: Int, firmwareData: ByteArray): Boolean {
        return if (libraryLoaded) {
            try {
                verifyFirmware(handle, firmwareData)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native verifyFirmware failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock verify operation (${firmwareData.size} bytes)")
            Thread.sleep(1500)
            true
        }
    }

    /**
     * Reset the chip
     * @param handle Device handle
     * @return true if successful
     */
    external fun resetChip(handle: Int): Boolean

    /**
     * Safe wrapper for resetChip
     */
    fun safeResetChip(handle: Int): Boolean {
        return if (libraryLoaded) {
            try {
                resetChip(handle)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native resetChip failed: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "Simulation mode: Mock reset operation")
            true
        }
    }

    /**
     * Get the last error message from the native library
     * @return Error message string, or "No error" if no error occurred
     */
    external fun getLastError(): String

    /**
     * Safe wrapper for getLastError
     */
    fun safeGetLastError(): String {
        return if (libraryLoaded) {
            try {
                getLastError()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native getLastError failed: ${e.message}")
                "Native library error: ${e.message}"
            }
        } else {
            loadError ?: "No error (simulation mode)"
        }
    }
}