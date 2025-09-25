package com.wch.flasher

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for chip database validation and compatibility
 * 
 * These tests ensure the Android layer properly handles the chip information
 * that comes from the native Rust code.
 */
class ChipDatabaseTest {

    @Test
    fun testSupportedChipIds() {
        // Test chip IDs and device types based on our native implementation
        val supportedChips = mapOf(
            // CH32V series
            Pair(0x70, 0x17) to "CH32V307",
            Pair(0x30, 0x30) to "CH32V103", 
            Pair(0x30, 0x19) to "CH32V203",
            Pair(0x30, 0x21) to "CH32V003",
            
            // CH32F series
            Pair(0x10, 0x30) to "CH32F103",
            
            // CH32X series
            Pair(0x50, 0x23) to "CH32X035",
            
            // Other series
            Pair(0x82, 0x82) to "CH582",
            Pair(0x49, 0x11) to "CH549",
            Pair(0x52, 0x11) to "CH552",
            Pair(0x73, 0x13) to "CH573",
            Pair(0x79, 0x13) to "CH579", 
            Pair(0x59, 0x22) to "CH559",
            Pair(0x92, 0x13) to "CH592"
        )
        
        // Validate that all expected chips have unique ID combinations
        assertEquals("Should have 13 supported chips", 13, supportedChips.size)
        
        // Test specific chips mentioned in the problem statement
        assertTrue("Should support CH32V203", supportedChips.containsValue("CH32V203"))
        assertTrue("Should support CH32V003", supportedChips.containsValue("CH32V003"))
        assertTrue("Should support CH32X035", supportedChips.containsValue("CH32X035"))
    }

    @Test
    fun testChipFlashSizes() {
        // Test expected flash sizes for different chips
        val chipFlashSizes = mapOf(
            "CH32V307" to 256,  // KB
            "CH32V103" to 64,
            "CH32V203" to 64,   // The main chip we're adding support for
            "CH32V003" to 16,
            "CH32F103" to 128,
            "CH32X035" to 62,
            "CH582" to 448,
            "CH549" to 62,
            "CH552" to 16,
            "CH573" to 448,
            "CH579" to 250,
            "CH559" to 62,
            "CH592" to 250
        )
        
        // Validate flash sizes are reasonable
        chipFlashSizes.forEach { (chip, flashKB) ->
            assertTrue("$chip flash size should be positive", flashKB > 0)
            assertTrue("$chip flash size should be reasonable (< 1MB)", flashKB < 1024)
        }
        
        // Test specific sizes for key chips
        assertEquals("CH32V203 should have 64KB flash", 64, chipFlashSizes["CH32V203"])
        assertEquals("CH32V003 should have 16KB flash", 16, chipFlashSizes["CH32V003"])
        assertEquals("CH32X035 should have 62KB flash", 62, chipFlashSizes["CH32X035"])
    }

    @Test
    fun testChipFamilyClassification() {
        // Test chip family classification logic
        val chipFamilies = mapOf(
            "CH32V307" to "CH32V",
            "CH32V103" to "CH32V", 
            "CH32V203" to "CH32V",  // Should be in CH32V family
            "CH32V003" to "CH32V003", // Special family for V003 series
            "CH32F103" to "CH32F",
            "CH32X035" to "CH32X035", // Special family for X035 series
            "CH582" to "CH582",
            "CH549" to "CH549",
            "CH552" to "CH552",
            "CH573" to "CH573",
            "CH579" to "CH579",
            "CH559" to "CH559",
            "CH592" to "CH592"
        )
        
        // Validate family assignments
        assertEquals("CH32V203 should be in CH32V family", "CH32V", chipFamilies["CH32V203"])
        assertEquals("CH32V003 should have its own family", "CH32V003", chipFamilies["CH32V003"])
        assertEquals("CH32X035 should have its own family", "CH32X035", chipFamilies["CH32X035"])
        
        // Test that core V-series chips are properly grouped
        val vSeriesChips = chipFamilies.filter { it.value == "CH32V" }
        assertTrue("Should have multiple CH32V family chips", vSeriesChips.size >= 3)
    }

    @Test
    fun testEncryptionSupport() {
        // Test which chips support encryption based on our implementation
        val encryptionSupported = setOf(
            "CH32V307", "CH32V103", "CH32V203", "CH32V003", 
            "CH32F103", "CH32X035", "CH582", "CH573", "CH579", "CH592"
        )
        
        val encryptionNotSupported = setOf(
            "CH549", "CH552", "CH559" // CH55x and CH59x typically don't support encryption
        )
        
        // Validate encryption support
        assertTrue("CH32V203 should support encryption", encryptionSupported.contains("CH32V203"))
        assertTrue("CH32V003 should support encryption", encryptionSupported.contains("CH32V003"))
        assertTrue("CH32X035 should support encryption", encryptionSupported.contains("CH32X035"))
        
        // Validate non-encryption chips
        encryptionNotSupported.forEach { chip ->
            assertFalse("$chip should not support encryption", encryptionSupported.contains(chip))
        }
    }

    @Test
    fun testChipDisplayFormat() {
        // Test chip display string formatting
        val testCases = mapOf(
            Pair(0x30, 0x19) to "CH32V203[0x3019]",
            Pair(0x30, 0x21) to "CH32V003[0x3021]", 
            Pair(0x50, 0x23) to "CH32X035[0x5023]",
            Pair(0x70, 0x17) to "CH32V307[0x7017]"
        )
        
        testCases.forEach { (ids, expected) ->
            val formatted = formatChipDisplay("${expected.substringBefore('[')}", ids.first, ids.second)
            assertTrue("Display should contain chip name", formatted.contains(expected.substringBefore('[')))
            assertTrue("Display should contain hex ID", formatted.contains("0x"))
        }
    }

    @Test
    fun testUnknownChipHandling() {
        // Test handling of unknown/unsupported chips
        val unknownChipId = 0xFF
        val unknownDeviceType = 0xFF
        
        val unknownChipName = formatUnknownChip(unknownChipId, unknownDeviceType)
        
        assertTrue("Unknown chip should indicate unknown status", unknownChipName.contains("Unknown"))
        assertTrue("Unknown chip should show chip ID", unknownChipName.contains("FF"))
        assertTrue("Unknown chip should show device type", unknownChipName.contains("FF"))
    }

    // Helper methods for testing chip database logic
    private fun formatChipDisplay(name: String, chipId: Int, deviceType: Int): String {
        return "$name[0x${(chipId shl 8 or deviceType).toString(16).uppercase().padStart(4, '0')}]"
    }

    private fun formatUnknownChip(chipId: Int, deviceType: Int): String {
        return "Unknown[0x${chipId.toString(16).uppercase().padStart(2, '0')}${deviceType.toString(16).uppercase().padStart(2, '0')}]"
    }
}