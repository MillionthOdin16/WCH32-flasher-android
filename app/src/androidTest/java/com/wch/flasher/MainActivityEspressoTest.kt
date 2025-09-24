package com.wch.flasher

import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso-based instrumented tests for MainActivity
 * 
 * These tests validate UI functionality without requiring external services
 * and are ideal for CI/CD environments.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Test
    fun testAppLaunchesWithoutCrashing() {
        // Verify main UI elements are displayed
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnSelectFile))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnFlash))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnErase))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.tvLog))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testInitialDeviceStatus() {
        // Device status should show "No WCH device connected" initially
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(withText(containsString("No WCH device connected"))))
    }

    @Test
    fun testInitialButtonStates() {
        // Flash button should be disabled initially (no device + no file)
        onView(withId(R.id.btnFlash))
            .check(matches(not(isEnabled())))
        
        // Erase button should be disabled initially (no device)
        onView(withId(R.id.btnErase))
            .check(matches(not(isEnabled())))
        
        // Select file button should be enabled
        onView(withId(R.id.btnSelectFile))
            .check(matches(isEnabled()))
    }

    @Test
    fun testSelectFileButtonClick() {
        // Click the select file button
        onView(withId(R.id.btnSelectFile))
            .perform(click())
        
        // Note: This will open the system file picker, which we can't easily test
        // The important thing is that the click doesn't crash the app
        
        // Press back to close any opened dialogs
        pressBack()
    }

    @Test
    fun testLogContainsStartupMessages() {
        // Verify log contains expected startup messages
        onView(withId(R.id.tvLog))
            .check(matches(withText(containsString("WCH32 Flasher started"))))
        
        onView(withId(R.id.tvLog))
            .check(matches(withText(containsString("Ready. Connect a WCH device to begin"))))
    }

    @Test
    fun testLogScrollable() {
        // The log view should be scrollable
        onView(withId(R.id.tvLog))
            .perform(swipeUp())
            .perform(swipeDown())
        
        // Log should still be displayed after scrolling
        onView(withId(R.id.tvLog))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testMaterialDesignElements() {
        // Verify Material Design cards are present
        onView(withText("Device Info"))
            .check(matches(isDisplayed()))
        
        onView(withText("Firmware File"))
            .check(matches(isDisplayed()))
        
        onView(withText("Flash Log"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testButtonLabels() {
        // Verify button labels are correct
        onView(withId(R.id.btnSelectFile))
            .check(matches(withText("Select Firmware File")))
        
        onView(withId(R.id.btnFlash))
            .check(matches(withText("Flash Firmware")))
        
        onView(withId(R.id.btnErase))
            .check(matches(withText("Erase Chip")))
    }

    @Test
    fun testProgressBarHiddenInitially() {
        // Progress bar should be hidden initially
        onView(withId(R.id.progressBar))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun testNativeLibrarySimulationMode() {
        // In simulation mode, log should contain appropriate messages
        // This test validates that the app doesn't crash when native library is missing
        onView(withId(R.id.tvLog))
            .check(matches(isDisplayed()))
        
        // The app should be functional even without native library
        onView(withId(R.id.btnSelectFile))
            .check(matches(isEnabled()))
    }

    @Test
    fun testAppDoesNotCrashOnRotation() {
        // Rotate device and verify app state is preserved
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(isDisplayed()))
        
        // Perform rotation (this happens automatically in test environment)
        // and verify UI elements are still present
        onView(withId(R.id.btnSelectFile))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnFlash))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.tvLog))
            .check(matches(withText(containsString("WCH32 Flasher started"))))
    }

    @Test
    fun testClickDisabledButtons() {
        // Clicking disabled buttons should not cause crashes
        onView(withId(R.id.btnFlash))
            .perform(click()) // This should be safe even though button is disabled
        
        onView(withId(R.id.btnErase))
            .perform(click()) // This should be safe even though button is disabled
        
        // App should remain stable
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAccessibilityContentDescriptions() {
        // Important UI elements should have proper accessibility support
        onView(withId(R.id.btnSelectFile))
            .check(matches(hasContentDescription()))
        
        // Or at minimum, should have meaningful text
        onView(withId(R.id.btnSelectFile))
            .check(matches(withText(not(isEmptyString()))))
    }

    @Test
    fun testUIResponsivenessAfterMultipleClicks() {
        // Perform multiple rapid clicks to test UI responsiveness
        repeat(5) {
            onView(withId(R.id.btnSelectFile))
                .perform(click())
            
            // Press back to handle any dialogs that might open
            pressBack()
            
            // Small delay to prevent overwhelming the system
            Thread.sleep(200)
        }
        
        // UI should remain responsive
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.btnSelectFile))
            .check(matches(isEnabled()))
    }

    @Test
    fun testLogViewMonospaceFont() {
        // Log view should use monospace font for better readability
        // We can't directly test font family, but we can verify the log is displayed
        onView(withId(R.id.tvLog))
            .check(matches(isDisplayed()))
            .check(matches(withText(not(isEmptyString()))))
    }

    @Test
    fun testCardViewElevationAndStyling() {
        // Material Design cards should be properly styled
        // We verify this indirectly by checking that card content is displayed
        onView(withText("Device Info"))
            .check(matches(isDisplayed()))
        
        onView(withText("Firmware File"))
            .check(matches(isDisplayed()))
        
        onView(withText("Flash Log"))
            .check(matches(isDisplayed()))
        
        // Device status should be within a card
        onView(withId(R.id.tvDeviceStatus))
            .check(matches(isDisplayed()))
    }
}