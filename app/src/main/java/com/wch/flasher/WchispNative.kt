package com.wch.flasher

import android.util.Log

/**
 * JNI wrapper for the native wchisp library
 * 
 * This class provides Kotlin/Java bindings to the Rust-based WCH ISP functionality.
 * It loads the native library and provides direct access to the hardware functionality.
 */
object WchispNative {
    
    private const val TAG = "WchispNative"
    private const val NATIVE_LIBRARY_NAME = "wchisp_android"
    
    private var isLibraryLoaded = false
    private var loadError: String? = null
    
    init {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
            isLibraryLoaded = true
            Log.i(TAG, "Native WCH ISP library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            loadError = "Failed to load native library: ${e.message}"
            Log.e(TAG, "Failed to load native library", e)
        } catch (e: Exception) {
            isLibraryLoaded = false
            loadError = "Unexpected error loading native library: ${e.message}"
            Log.e(TAG, "Unexpected error loading native library", e)
        }
    }
    
    /**
     * Check if the native library is loaded and available
     * @return true if native library is loaded and ready to use
     */
    fun isLibraryLoaded(): Boolean {
        return isLibraryLoaded
    }

    /**
     * Get the library load error message if any
     * @return error message or null if library loaded successfully
     */
    fun getLoadError(): String? {
        return loadError
    }

    // Native method declarations - these correspond to the Rust JNI functions
    
    /**
     * Initialize the native WCH ISP library
     * @return true if initialization successful
     */
    external fun init(): Boolean
    
    /**
     * Open a USB device connection
     * @param deviceFd USB device file descriptor from Android USB Host API
     * @param vendorId USB vendor ID
     * @param productId USB product ID  
     * @param usbConnection Android UsbDeviceConnection object
     * @return device handle (positive integer) or -1 on error
     */
    external fun openDevice(deviceFd: Int, vendorId: Int, productId: Int, usbConnection: Any): Int
    
    /**
     * Close a USB device connection
     * @param handle device handle from openDevice
     * @return true if successful
     */
    external fun closeDevice(handle: Int): Boolean
    
    /**
     * Identify the connected chip
     * @param handle device handle
     * @return chip information string or null on error
     */
    external fun identifyChip(handle: Int): String?
    
    /**
     * Flash firmware to the chip
     * @param handle device handle
     * @param firmwareData firmware binary data
     * @return true if successful
     */
    external fun flashFirmware(handle: Int, firmwareData: ByteArray): Boolean
    
    /**
     * Erase chip flash memory
     * @param handle device handle
     * @return true if successful
     */
    external fun eraseChip(handle: Int): Boolean
    
    /**
     * Verify firmware on the chip
     * @param handle device handle
     * @param firmwareData firmware binary data to verify against
     * @return true if verification successful
     */
    external fun verifyFirmware(handle: Int, firmwareData: ByteArray): Boolean
    
    /**
     * Reset the chip
     * @param handle device handle
     * @return true if successful
     */
    external fun resetChip(handle: Int): Boolean
    
    /**
     * Get the last error message from the native library
     * @return error message string
     */
    external fun getLastError(): String
    
    // Safe wrapper methods that handle library loading gracefully
    
    /**
     * Safe initialization that checks if library is loaded
     */
    fun safeInit(): Boolean {
        return if (isLibraryLoaded) {
            try {
                init()
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native init: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot initialize - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe device opening that checks if library is loaded
     */
    fun safeOpenDevice(deviceFd: Int, vendorId: Int, productId: Int, usbConnection: Any): Int {
        return if (isLibraryLoaded) {
            try {
                openDevice(deviceFd, vendorId, productId, usbConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native openDevice: ${e.message}", e)
                -1
            }
        } else {
            Log.w(TAG, "Cannot open device - native library not loaded: $loadError")
            -1
        }
    }
    
    /**
     * Safe device closing that checks if library is loaded
     */
    fun safeCloseDevice(handle: Int): Boolean {
        return if (isLibraryLoaded) {
            try {
                closeDevice(handle)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native closeDevice: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot close device - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe chip identification that checks if library is loaded
     */
    fun safeIdentifyChip(handle: Int): String {
        return if (isLibraryLoaded) {
            try {
                identifyChip(handle) ?: "Unknown chip (identification failed)"
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native identifyChip: ${e.message}", e)
                "Unknown chip (error: ${e.message})"
            }
        } else {
            Log.w(TAG, "Cannot identify chip - native library not loaded: $loadError")
            "Unknown chip (native library not available)"
        }
    }
    
    /**
     * Safe firmware flashing that checks if library is loaded
     */
    fun safeFlashFirmware(handle: Int, firmwareData: ByteArray): Boolean {
        return if (isLibraryLoaded) {
            try {
                flashFirmware(handle, firmwareData)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native flashFirmware: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot flash firmware - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe chip erasing that checks if library is loaded
     */
    fun safeEraseChip(handle: Int): Boolean {
        return if (isLibraryLoaded) {
            try {
                eraseChip(handle)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native eraseChip: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot erase chip - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe firmware verification that checks if library is loaded
     */
    fun safeVerifyFirmware(handle: Int, firmwareData: ByteArray): Boolean {
        return if (isLibraryLoaded) {
            try {
                verifyFirmware(handle, firmwareData)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native verifyFirmware: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot verify firmware - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe chip reset that checks if library is loaded
     */
    fun safeResetChip(handle: Int): Boolean {
        return if (isLibraryLoaded) {
            try {
                resetChip(handle)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native resetChip: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot reset chip - native library not loaded: $loadError")
            false
        }
    }
    
    /**
     * Safe error retrieval that checks if library is loaded
     */
    fun safeGetLastError(): String {
        return if (isLibraryLoaded) {
            try {
                getLastError()
            } catch (e: Exception) {
                Log.e(TAG, "Error calling native getLastError: ${e.message}", e)
                "Error retrieving last error: ${e.message}"
            }
        } else {
            loadError ?: "Native library not loaded"
        }
    }
}