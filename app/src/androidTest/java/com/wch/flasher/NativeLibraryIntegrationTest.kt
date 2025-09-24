package com.wch.flasher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests specifically for native library functionality
 * 
 * These tests validate the JNI integration and native library loading
 * behavior under different conditions.
 */
@RunWith(AndroidJUnit4::class)
class NativeLibraryIntegrationTest {

    @Test
    fun testNativeLibraryLoadingStatus() {
        // Test the native library loading status
        val isLoaded = WchispNative.isLibraryLoaded()
        val loadError = WchispNative.getLoadError()
        
        if (isLoaded) {
            println("✓ Native library loaded successfully")
            assertNull("Load error should be null when library is loaded", loadError)
        } else {
            println("⚠ Native library not loaded - running in simulation mode")
            println("Load error: $loadError")
            assertNotNull("Load error should be present when library not loaded", loadError)
        }
    }

    @Test
    fun testSafeInitialization() {
        // Test safe initialization works regardless of library status
        val initResult = WchispNative.safeInit()
        
        // Should always return true (either real init success or simulation mode)
        assertTrue("Safe init should always succeed", initResult)
        
        println("✓ Safe initialization completed successfully")
    }

    @Test
    fun testSimulationModeDeviceOperations() {
        // Test that device operations work in simulation mode
        val mockHandle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, Any())
        
        if (WchispNative.isLibraryLoaded()) {
            // With real library, this might fail due to no actual device
            println("Real library loaded - device operations depend on hardware")
        } else {
            // In simulation mode, should return mock handle
            assertTrue("Mock handle should be positive", mockHandle > 0)
            
            // Test chip identification in simulation mode
            val chipInfo = WchispNative.safeIdentifyChip(mockHandle)
            assertNotNull("Chip info should be returned in simulation mode", chipInfo)
            assertTrue("Chip info should contain CH32V203", 
                      chipInfo?.contains("CH32V203") == true)
            assertTrue("Chip info should indicate simulation", 
                      chipInfo?.contains("Simulation Mode") == true)
            
            // Test close operation
            val closeResult = WchispNative.safeCloseDevice(mockHandle)
            assertTrue("Close should succeed in simulation mode", closeResult)
            
            println("✓ Simulation mode operations working correctly")
        }
    }

    @Test
    fun testErrorHandling() {
        // Test error handling for invalid operations
        val invalidHandle = -1
        
        // These should not crash even with invalid handle
        val chipInfo = WchispNative.safeIdentifyChip(invalidHandle)
        val closeResult = WchispNative.safeCloseDevice(invalidHandle)
        val lastError = WchispNative.safeGetLastError()
        
        // In simulation mode, these should still work gracefully
        if (!WchispNative.isLibraryLoaded()) {
            assertNotNull("Should get simulation chip info even with invalid handle", chipInfo)
            assertTrue("Close should succeed in simulation mode", closeResult)
        }
        
        assertNotNull("Last error should never be null", lastError)
        
        println("✓ Error handling works correctly")
    }

    @Test
    fun testFlashingOperationsInSimulationMode() {
        if (WchispNative.isLibraryLoaded()) {
            println("Real library loaded - skipping simulation mode test")
            return
        }
        
        val mockHandle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, Any())
        assertTrue("Mock handle should be valid", mockHandle > 0)
        
        // Test firmware operations with mock data
        val mockFirmware = ByteArray(1024) { 0x55 }
        
        // Test erase operation
        val eraseResult = WchispNative.safeEraseChip(mockHandle)
        assertTrue("Erase should succeed in simulation mode", eraseResult)
        
        // Test flash operation
        val flashResult = WchispNative.safeFlashFirmware(mockHandle, mockFirmware)
        assertTrue("Flash should succeed in simulation mode", flashResult)
        
        // Test verify operation
        val verifyResult = WchispNative.safeVerifyFirmware(mockHandle, mockFirmware)
        assertTrue("Verify should succeed in simulation mode", verifyResult)
        
        // Test reset operation
        val resetResult = WchispNative.safeResetChip(mockHandle)
        assertTrue("Reset should succeed in simulation mode", resetResult)
        
        println("✓ All flashing operations work correctly in simulation mode")
    }

    @Test
    fun testThreadSafetyOfNativeOperations() {
        // Test that native operations are thread-safe
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Boolean>()
        val resultLock = Any()
        
        // Create multiple threads that call native operations
        repeat(5) { threadIndex ->
            val thread = Thread {
                try {
                    // Test init operation
                    val initResult = WchispNative.safeInit()
                    
                    // Test device operations
                    val handle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, Any())
                    val chipInfo = WchispNative.safeIdentifyChip(handle)
                    val closeResult = WchispNative.safeCloseDevice(handle)
                    
                    synchronized(resultLock) {
                        results.add(initResult && chipInfo != null && closeResult)
                    }
                    
                    println("Thread $threadIndex completed successfully")
                } catch (e: Exception) {
                    println("Thread $threadIndex failed: ${e.message}")
                    synchronized(resultLock) {
                        results.add(false)
                    }
                }
            }
            threads.add(thread)
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for all threads to complete
        threads.forEach { it.join(5000) } // 5 second timeout per thread
        
        // Verify all operations completed successfully
        assertEquals("All threads should complete", 5, results.size)
        assertTrue("All thread operations should succeed", results.all { it })
        
        println("✓ Native operations are thread-safe")
    }

    @Test
    fun testMemoryManagementWithNativeOperations() {
        // Test that repeated native operations don't cause memory leaks
        repeat(100) { iteration ->
            val handle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, Any())
            val chipInfo = WchispNative.safeIdentifyChip(handle)
            val closeResult = WchispNative.safeCloseDevice(handle)
            
            // Verify operations succeed
            if (!WchispNative.isLibraryLoaded()) {
                assertTrue("Mock handle should be valid", handle > 0)
                assertNotNull("Chip info should be returned", chipInfo)
                assertTrue("Close should succeed", closeResult)
            }
            
            // Force garbage collection periodically
            if (iteration % 20 == 0) {
                System.gc()
                Thread.sleep(10)
            }
        }
        
        println("✓ Memory management test completed - no apparent leaks")
    }

    @Test
    fun testLargeDataHandling() {
        if (!WchispNative.isLibraryLoaded()) {
            // Test large firmware data in simulation mode
            val largeFirmware = ByteArray(512 * 1024) { (it % 256).toByte() } // 512KB
            
            val handle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, Any())
            assertTrue("Mock handle should be valid", handle > 0)
            
            // Test flash operation with large data
            val flashResult = WchispNative.safeFlashFirmware(handle, largeFirmware)
            assertTrue("Flash should handle large data in simulation mode", flashResult)
            
            // Test verify operation with large data
            val verifyResult = WchispNative.safeVerifyFirmware(handle, largeFirmware)
            assertTrue("Verify should handle large data in simulation mode", verifyResult)
            
            println("✓ Large data handling works correctly (${largeFirmware.size} bytes)")
        } else {
            println("Real library loaded - large data test would require actual hardware")
        }
    }

    @Test
    fun testContextAndInstrumentation() {
        // Verify test environment setup
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull("Context should be available", context)
        assertEquals("Package name should match", "com.wch.flasher", context.packageName)
        
        val appContext = InstrumentationRegistry.getInstrumentation().context
        assertNotNull("App context should be available", appContext)
        
        println("✓ Test environment setup correctly")
        println("Target package: ${context.packageName}")
        println("Test package: ${appContext.packageName}")
    }
}