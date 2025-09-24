package com.wch.flasher

/**
 * JNI wrapper for the native wchisp library
 * 
 * This class provides Kotlin/Java bindings to the Rust-based WCH ISP functionality
 */
object WchispNative {
    
    init {
        try {
            System.loadLibrary("wchisp_android")
        } catch (e: UnsatisfiedLinkError) {
            throw RuntimeException("Failed to load native library: ${e.message}")
        }
    }

    /**
     * Initialize the native library
     * @return true if initialization was successful
     */
    external fun init(): Boolean

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
     * Close USB device connection
     * @param handle Device handle returned by openDevice
     * @return true if successful
     */
    external fun closeDevice(handle: Int): Boolean

    /**
     * Identify the connected chip
     * @param handle Device handle
     * @return Chip identification string, or null on error
     */
    external fun identifyChip(handle: Int): String?

    /**
     * Flash firmware to the chip
     * @param handle Device handle
     * @param firmwareData Firmware binary data
     * @return true if successful
     */
    external fun flashFirmware(handle: Int, firmwareData: ByteArray): Boolean

    /**
     * Erase chip flash memory
     * @param handle Device handle
     * @return true if successful
     */
    external fun eraseChip(handle: Int): Boolean

    /**
     * Verify firmware on the chip
     * @param handle Device handle
     * @param firmwareData Expected firmware data for verification
     * @return true if verification passed
     */
    external fun verifyFirmware(handle: Int, firmwareData: ByteArray): Boolean

    /**
     * Reset the chip
     * @param handle Device handle
     * @return true if successful
     */
    external fun resetChip(handle: Int): Boolean

    /**
     * Get the last error message from the native library
     * @return Error message string, or "No error" if no error occurred
     */
    external fun getLastError(): String
}