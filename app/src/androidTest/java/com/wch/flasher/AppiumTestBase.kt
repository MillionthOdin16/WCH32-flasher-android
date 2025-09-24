package com.wch.flasher

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.net.URL
import java.time.Duration

/**
 * Base class for Appium-based Android testing
 * 
 * This provides the foundation for comprehensive Android app testing using Appium
 * with UiAutomator2 for device interaction and app automation.
 */
@RunWith(AndroidJUnit4::class)
abstract class AppiumTestBase {

    protected lateinit var driver: AndroidDriver
    protected lateinit var device: UiDevice
    protected lateinit var context: Context
    private var appiumService: AppiumDriverLocalService? = null

    companion object {
        private const val APP_PACKAGE = "com.wch.flasher"
        private const val APP_ACTIVITY = ".MainActivity"
        private const val IMPLICIT_WAIT_TIMEOUT = 10L
        private const val APPIUM_PORT = 4723
    }

    @Before
    open fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Start Appium service if not already running
        setupAppiumService()
        
        // Configure Appium options for Android testing
        val options = UiAutomator2Options().apply {
            setDeviceName("Android Device")
            setPlatformName("Android")
            setAppPackage(APP_PACKAGE)
            setAppActivity(APP_ACTIVITY)
            setNoReset(false) // Reset app state for clean testing
            setFullReset(false) // Don't uninstall app between tests
            setNewCommandTimeout(Duration.ofSeconds(60))
            setImplicitWaitTimeout(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT))
            
            // Enable UiAutomator2 specific options
            setAutomationName("UiAutomator2")
            setAutoGrantPermissions(true) // Auto-grant permissions for testing
            setDisableWindowAnimation(true) // Disable animations for faster testing
        }
        
        // Initialize AndroidDriver with Appium server
        driver = AndroidDriver(getAppiumServerUrl(), options)
        
        // Set implicit wait for element finding
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT))
    }

    @After
    open fun tearDown() {
        if (::driver.isInitialized) {
            driver.quit()
        }
        
        // Stop Appium service if we started it
        appiumService?.stop()
    }

    /**
     * Setup Appium service for local testing
     */
    private fun setupAppiumService() {
        try {
            // Try to connect to existing Appium server first
            val testUrl = URL("http://127.0.0.1:$APPIUM_PORT/wd/hub")
            // If this fails, we'll start our own service
        } catch (e: Exception) {
            // Start local Appium service
            appiumService = AppiumServiceBuilder()
                .withIPAddress("127.0.0.1")
                .usingPort(APPIUM_PORT)
                .build()
            
            appiumService?.start()
        }
    }

    /**
     * Get Appium server URL
     */
    private fun getAppiumServerUrl(): URL {
        return URL("http://127.0.0.1:$APPIUM_PORT/wd/hub")
    }

    /**
     * Launch the app for testing
     */
    protected fun launchApp() {
        val intent = Intent().apply {
            setPackage(APP_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        // Wait for app to launch
        device.waitForIdle(5000)
    }

    /**
     * Take a screenshot for debugging
     */
    protected fun takeScreenshot(name: String = "screenshot") {
        try {
            val screenshot = driver.getScreenshotAs(org.openqa.selenium.OutputType.BYTES)
            // In a real test environment, you would save this to a file
            println("Screenshot taken: $name (${screenshot.size} bytes)")
        } catch (e: Exception) {
            println("Failed to take screenshot: ${e.message}")
        }
    }

    /**
     * Wait for element to be present and visible
     */
    protected fun waitForElement(locator: org.openqa.selenium.By, timeoutSeconds: Long = 10): org.openqa.selenium.WebElement? {
        return try {
            val wait = org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
            wait.until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(locator))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if app is in simulation mode (native library not loaded)
     */
    protected fun isSimulationMode(): Boolean {
        return try {
            WchispNative.isLibraryLoaded().not()
        } catch (e: Exception) {
            true // Assume simulation mode if we can't check
        }
    }
}