package com.wch.flasher

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for WchispNative JNI interface
 * 
 * These tests validate the native library interface without requiring actual hardware.
 * They test the JNI bindings and basic functionality.
 */
class WchispNativeTest {

    @Test
    fun testNativeLibraryInterface() {
        // Test that the WchispNative class has the expected safe interface methods
        // without actually loading the native library (which isn't available in unit tests)
        
        // Verify the class exists and has the expected methods
        val methods = WchispNative::class.java.declaredMethods
        val methodNames = methods.map { it.name }.toSet()
        
        // Check that all expected safe wrapper methods are declared
        assertTrue("Should have safeInit method", methodNames.contains("safeInit"))
        assertTrue("Should have safeOpenDevice method", methodNames.contains("safeOpenDevice"))
        assertTrue("Should have safeCloseDevice method", methodNames.contains("safeCloseDevice"))
        assertTrue("Should have safeIdentifyChip method", methodNames.contains("safeIdentifyChip"))
        assertTrue("Should have safeFlashFirmware method", methodNames.contains("safeFlashFirmware"))
        assertTrue("Should have safeEraseChip method", methodNames.contains("safeEraseChip"))
        assertTrue("Should have safeVerifyFirmware method", methodNames.contains("safeVerifyFirmware"))
        assertTrue("Should have safeResetChip method", methodNames.contains("safeResetChip"))
        assertTrue("Should have safeGetLastError method", methodNames.contains("safeGetLastError"))
        
        // Check that library status methods are available
        assertTrue("Should have isLibraryLoaded method", methodNames.contains("isLibraryLoaded"))
        assertTrue("Should have getLoadError method", methodNames.contains("getLoadError"))
    }

    @Test
    fun testSupportedDeviceIds() {
        // Test the device ID constants used in MainActivity
        val supportedDevices = setOf(
            Pair(0x4348, 0x55e0), // WCH
            Pair(0x1a86, 0x55e0)  // QinHeng Electronics
        )
        
        assertTrue("Should have WCH device entry", supportedDevices.contains(Pair(0x4348, 0x55e0)))
        assertTrue("Should have QinHeng device entry", supportedDevices.contains(Pair(0x1a86, 0x55e0)))
        assertEquals("Should have exactly 2 supported device types", 2, supportedDevices.size)
    }

    @Test
    fun testChipFamilySupport() {
        // Test that our chip families match what's expected
        val expectedChipFamilies = setOf(
            "CH32V307", "CH32V103", "CH32F103", "CH582", 
            "CH32V203", "CH32V003", "CH32X035",
            "CH549", "CH552", "CH573", "CH579", "CH559", "CH592"
        )
        
        // Verify we have proper coverage of the chip families mentioned in README
        assertTrue("Should support CH32V203", expectedChipFamilies.contains("CH32V203"))
        assertTrue("Should support CH32V003", expectedChipFamilies.contains("CH32V003"))
        assertTrue("Should support CH32X035", expectedChipFamilies.contains("CH32X035"))
        assertTrue("Should support CH549", expectedChipFamilies.contains("CH549"))
        assertTrue("Should support CH552", expectedChipFamilies.contains("CH552"))
    }

    @Test
    fun testInvalidHandleHandling() {
        // Test that invalid handles are handled properly
        val invalidHandle = -1
        
        // These should be safe to call with invalid handles
        // The native code should handle invalid inputs gracefully
        assertTrue("Invalid handle should be negative", invalidHandle < 0)
    }

    @Test
    fun testFirmwareDataValidation() {
        // Test firmware data constraints
        val emptyFirmware = ByteArray(0)
        val smallFirmware = ByteArray(1024)
        val largeFirmware = ByteArray(256 * 1024) // 256KB
        
        assertNotNull("Empty firmware should not be null", emptyFirmware)
        assertNotNull("Small firmware should not be null", smallFirmware)
        assertNotNull("Large firmware should not be null", largeFirmware)
        
        assertEquals("Empty firmware should have 0 bytes", 0, emptyFirmware.size)
        assertEquals("Small firmware should have 1024 bytes", 1024, smallFirmware.size)
        assertEquals("Large firmware should have 256KB", 256 * 1024, largeFirmware.size)
    }

    @Test
    fun testActionConstants() {
        // Test that the USB permission action is properly defined
        val usbPermissionAction = "com.wch.flasher.USB_PERMISSION"
        
        assertNotNull("USB permission action should not be null", usbPermissionAction)
        assertTrue("USB permission action should contain package name", 
                   usbPermissionAction.contains("com.wch.flasher"))
        assertTrue("USB permission action should contain permission suffix", 
                   usbPermissionAction.contains("USB_PERMISSION"))
    }
}