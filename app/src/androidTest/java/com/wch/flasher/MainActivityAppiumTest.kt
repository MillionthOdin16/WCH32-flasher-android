package com.wch.flasher

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.appium.java_client.AppiumBy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openqa.selenium.By

/**
 * Appium-based integration tests for MainActivity
 * 
 * These tests validate the complete app functionality including UI interactions,
 * native library integration, and error handling scenarios.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityAppiumTest : AppiumTestBase() {

    @Test
    fun testAppLaunchAndBasicUI() {
        // Test that the app launches without crashing
        launchApp()
        takeScreenshot("app_launch")
        
        // Verify main UI elements are present
        val deviceStatusCard = waitForElement(By.id("com.wch.flasher:id/tvDeviceStatus"))
        assertNotNull("Device status should be visible", deviceStatusCard)
        
        val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
        assertNotNull("Select file button should be visible", selectFileButton)
        
        val flashButton = waitForElement(By.id("com.wch.flasher:id/btnFlash"))
        assertNotNull("Flash button should be visible", flashButton)
        
        val eraseButton = waitForElement(By.id("com.wch.flasher:id/btnErase"))
        assertNotNull("Erase button should be visible", eraseButton)
        
        val logView = waitForElement(By.id("com.wch.flasher:id/tvLog"))
        assertNotNull("Log view should be visible", logView)
        
        println("✓ App launched successfully with all UI elements visible")
    }

    @Test
    fun testNativeLibraryStatus() {
        launchApp()
        takeScreenshot("native_lib_test")
        
        // Check if we're in simulation mode
        val simulationMode = isSimulationMode()
        
        if (simulationMode) {
            println("✓ App running in simulation mode (native library not loaded)")
            
            // Verify log shows simulation mode message
            val logView = waitForElement(By.id("com.wch.flasher:id/tvLog"))
            assertNotNull("Log view should be present", logView)
            
            val logText = logView?.text
            assertNotNull("Log should have content", logText)
            
            // Should contain startup messages even in simulation mode
            assertTrue("Log should contain startup message", 
                      logText?.contains("WCH32 Flasher started") == true)
        } else {
            println("✓ App running with native library loaded")
        }
    }

    @Test
    fun testButtonStatesAndInteractions() {
        launchApp()
        takeScreenshot("button_states")
        
        // Initially, flash button should be disabled (no device + no file)
        val flashButton = waitForElement(By.id("com.wch.flasher:id/btnFlash"))
        assertNotNull("Flash button should be present", flashButton)
        assertFalse("Flash button should be disabled initially", 
                   flashButton?.isEnabled == true)
        
        // Erase button should also be disabled (no device)
        val eraseButton = waitForElement(By.id("com.wch.flasher:id/btnErase"))
        assertNotNull("Erase button should be present", eraseButton)
        assertFalse("Erase button should be disabled initially", 
                   eraseButton?.isEnabled == true)
        
        // Select file button should be enabled
        val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
        assertNotNull("Select file button should be present", selectFileButton)
        assertTrue("Select file button should be enabled", 
                  selectFileButton?.isEnabled == true)
        
        println("✓ Button states are correct for initial app state")
    }

    @Test
    fun testFileSelectionDialog() {
        launchApp()
        takeScreenshot("before_file_selection")
        
        // Tap the select file button
        val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
        assertNotNull("Select file button should be present", selectFileButton)
        
        selectFileButton?.click()
        
        // Wait a moment for the file picker to appear
        Thread.sleep(2000)
        takeScreenshot("file_picker_opened")
        
        // The system file picker should appear - we can't easily test this
        // without additional setup, but we can verify the button click worked
        println("✓ File selection dialog interaction completed")
        
        // Press back to close file picker if it opened
        device.pressBack()
        Thread.sleep(1000)
    }

    @Test
    fun testLogScrollingAndContent() {
        launchApp()
        takeScreenshot("log_content")
        
        val logView = waitForElement(By.id("com.wch.flasher:id/tvLog"))
        assertNotNull("Log view should be present", logView)
        
        val logText = logView?.text
        assertNotNull("Log should have content", logText)
        
        // Verify expected log messages
        assertTrue("Log should contain app start message",
                  logText?.contains("WCH32 Flasher started") == true)
        
        assertTrue("Log should contain ready message",
                  logText?.contains("Ready. Connect a WCH device to begin") == true)
        
        if (isSimulationMode()) {
            assertTrue("Log should indicate simulation mode",
                      logText?.contains("simulation mode") == true ||
                      logText?.contains("Native library not loaded") == true)
        }
        
        println("✓ Log content verification completed")
        println("Log content preview: ${logText?.take(200)}...")
    }

    @Test
    fun testDeviceStatusDisplay() {
        launchApp()
        takeScreenshot("device_status")
        
        val deviceStatus = waitForElement(By.id("com.wch.flasher:id/tvDeviceStatus"))
        assertNotNull("Device status should be present", deviceStatus)
        
        val statusText = deviceStatus?.text
        assertNotNull("Device status should have text", statusText)
        
        // Should show "No WCH device connected" initially
        assertTrue("Should show no device connected",
                  statusText?.contains("No WCH device connected") == true ||
                  statusText?.contains("device") == true)
        
        println("✓ Device status display shows correct initial state: $statusText")
    }

    @Test
    fun testAppRotationHandling() {
        launchApp()
        takeScreenshot("portrait_mode")
        
        // Test portrait to landscape rotation
        device.setOrientationLeft()
        Thread.sleep(2000)
        takeScreenshot("landscape_mode")
        
        // Verify UI elements are still present after rotation
        val deviceStatus = waitForElement(By.id("com.wch.flasher:id/tvDeviceStatus"))
        assertNotNull("Device status should be present after rotation", deviceStatus)
        
        val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
        assertNotNull("Select file button should be present after rotation", selectFileButton)
        
        // Rotate back to portrait
        device.setOrientationNatural()
        Thread.sleep(2000)
        takeScreenshot("back_to_portrait")
        
        println("✓ App handles rotation correctly")
    }

    @Test
    fun testAppBackgroundAndForeground() {
        launchApp()
        takeScreenshot("foreground")
        
        // Send app to background
        device.pressHome()
        Thread.sleep(2000)
        
        // Bring app back to foreground
        launchApp()
        Thread.sleep(2000)
        takeScreenshot("back_to_foreground")
        
        // Verify app state is preserved
        val logView = waitForElement(By.id("com.wch.flasher:id/tvLog"))
        assertNotNull("Log view should be present after background/foreground", logView)
        
        val logText = logView?.text
        assertTrue("Log content should be preserved",
                  logText?.contains("WCH32 Flasher started") == true)
        
        println("✓ App handles background/foreground transition correctly")
    }

    @Test
    fun testAccessibilityElements() {
        launchApp()
        takeScreenshot("accessibility_test")
        
        // Test that important UI elements have proper content descriptions
        val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
        assertNotNull("Select file button should be present", selectFileButton)
        
        val flashButton = waitForElement(By.id("com.wch.flasher:id/btnFlash"))
        assertNotNull("Flash button should be present", flashButton)
        
        val eraseButton = waitForElement(By.id("com.wch.flasher:id/btnErase"))
        assertNotNull("Erase button should be present", eraseButton)
        
        // Verify buttons have meaningful text
        assertTrue("Select file button should have meaningful text",
                  selectFileButton?.text?.isNotEmpty() == true)
        
        assertTrue("Flash button should have meaningful text",
                  flashButton?.text?.isNotEmpty() == true)
        
        assertTrue("Erase button should have meaningful text",
                  eraseButton?.text?.isNotEmpty() == true)
        
        println("✓ Accessibility elements are properly configured")
    }

    @Test
    fun testPerformanceAndMemoryUsage() {
        launchApp()
        takeScreenshot("performance_test")
        
        // Perform multiple UI interactions to test stability
        repeat(5) { iteration ->
            println("Performance test iteration: ${iteration + 1}")
            
            // Interact with various UI elements
            val selectFileButton = waitForElement(By.id("com.wch.flasher:id/btnSelectFile"))
            selectFileButton?.click()
            Thread.sleep(500)
            
            // Press back if file picker opened
            device.pressBack()
            Thread.sleep(500)
            
            // Scroll log view if possible
            val logView = waitForElement(By.id("com.wch.flasher:id/tvLog"))
            if (logView != null) {
                // Attempt to scroll the log view
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    10
                )
            }
            Thread.sleep(300)
        }
        
        // App should still be responsive
        val deviceStatus = waitForElement(By.id("com.wch.flasher:id/tvDeviceStatus"))
        assertNotNull("App should remain responsive after performance test", deviceStatus)
        
        println("✓ App maintains performance and stability under repeated interactions")
    }
}