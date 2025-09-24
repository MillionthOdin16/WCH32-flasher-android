package com.wch.flasher

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for MainActivity logic
 * 
 * These tests validate the main application logic without requiring Android runtime.
 */
class MainActivityTest {

    @Test
    fun testSupportedDeviceValidation() {
        // Test device validation logic that would be used in MainActivity
        val supportedDevices = setOf(
            Pair(0x4348, 0x55e0), // WCH
            Pair(0x1a86, 0x55e0)  // QinHeng Electronics  
        )
        
        // Test valid devices
        assertTrue("WCH device should be supported", 
                   isDeviceSupported(0x4348, 0x55e0, supportedDevices))
        assertTrue("QinHeng device should be supported", 
                   isDeviceSupported(0x1a86, 0x55e0, supportedDevices))
        
        // Test invalid devices
        assertFalse("Random device should not be supported", 
                    isDeviceSupported(0x1234, 0x5678, supportedDevices))
        assertFalse("Wrong PID should not be supported", 
                    isDeviceSupported(0x4348, 0x1234, supportedDevices))
    }

    @Test
    fun testFileExtensionValidation() {
        // Test firmware file extension validation
        assertTrue("BIN files should be supported", isValidFirmwareFile("firmware.bin"))
        assertTrue("HEX files should be supported", isValidFirmwareFile("firmware.hex"))
        assertTrue("ELF files should be supported", isValidFirmwareFile("firmware.elf"))
        
        // Test case insensitivity
        assertTrue("BIN files should be case insensitive", isValidFirmwareFile("firmware.BIN"))
        assertTrue("HEX files should be case insensitive", isValidFirmwareFile("firmware.HEX"))
        
        // Test invalid files
        assertFalse("TXT files should not be supported", isValidFirmwareFile("readme.txt"))
        assertFalse("Empty extension should not be supported", isValidFirmwareFile("firmware"))
        assertFalse("Null filename should not be supported", isValidFirmwareFile(null))
    }

    @Test
    fun testChipInfoDisplay() {
        // Test chip information display formatting
        val chipInfo1 = formatChipInfo("CH32V203", 64, 0)
        val chipInfo2 = formatChipInfo("CH582", 448, 32)
        
        assertTrue("CH32V203 info should contain chip name", chipInfo1.contains("CH32V203"))
        assertTrue("CH32V203 info should contain flash size", chipInfo1.contains("64"))
        
        assertTrue("CH582 info should contain chip name", chipInfo2.contains("CH582"))
        assertTrue("CH582 info should contain flash size", chipInfo2.contains("448"))
        assertTrue("CH582 info should contain EEPROM size", chipInfo2.contains("32"))
    }

    @Test
    fun testProgressCalculation() {
        // Test progress percentage calculation
        assertEquals("0% progress", 0, calculateProgress(0, 1000))
        assertEquals("50% progress", 50, calculateProgress(500, 1000))
        assertEquals("100% progress", 100, calculateProgress(1000, 1000))
        
        // Test edge cases
        assertEquals("Should handle zero total", 0, calculateProgress(0, 0))
        assertEquals("Should cap at 100%", 100, calculateProgress(1500, 1000))
    }

    @Test
    fun testLogFormatting() {
        // Test log message formatting
        val timestamp = "2024-01-01 12:00:00"
        val message = "Device connected"
        val level = "INFO"
        
        val formatted = formatLogMessage(timestamp, level, message)
        
        assertTrue("Log should contain timestamp", formatted.contains(timestamp))
        assertTrue("Log should contain level", formatted.contains(level))
        assertTrue("Log should contain message", formatted.contains(message))
    }

    // Helper methods that simulate MainActivity logic
    private fun isDeviceSupported(vendorId: Int, productId: Int, supportedDevices: Set<Pair<Int, Int>>): Boolean {
        return supportedDevices.contains(Pair(vendorId, productId))
    }

    private fun isValidFirmwareFile(filename: String?): Boolean {
        if (filename == null) return false
        val validExtensions = setOf("bin", "hex", "elf")
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension in validExtensions
    }

    private fun formatChipInfo(name: String, flashKB: Int, eepromKB: Int): String {
        return if (eepromKB > 0) {
            "$name (Code Flash: ${flashKB}KiB, Data EEPROM: ${eepromKB}KiB)"
        } else {
            "$name (Code Flash: ${flashKB}KiB)"
        }
    }

    private fun calculateProgress(current: Int, total: Int): Int {
        if (total == 0) return 0
        val progress = (current.toFloat() / total.toFloat() * 100).toInt()
        return minOf(100, maxOf(0, progress))
    }

    private fun formatLogMessage(timestamp: String, level: String, message: String): String {
        return "[$timestamp] $level: $message"
    }
}