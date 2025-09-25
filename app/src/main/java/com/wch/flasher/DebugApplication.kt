package com.wch.flasher

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Debug Application class to catch early crashes and provide detailed logging
 */
class DebugApplication : Application() {
    
    companion object {
        private const val TAG = "DebugApplication"
    }
    
    override fun onCreate() {
        Log.i(TAG, "=== DebugApplication.onCreate() starting ===")
        Log.i(TAG, "Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.i(TAG, "App package: ${packageName}")
        
        try {
            super.onCreate()
            Log.i(TAG, "super.onCreate() completed")
            
            // Test critical components early
            testSystemServices()
            testWchispNative()
            testManifestPermissions()
            testLayoutResources()
            
            Log.i(TAG, "=== DebugApplication.onCreate() completed successfully ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "=== CRASH in DebugApplication.onCreate() ===", e)
            Log.e(TAG, "Stack trace: ${Log.getStackTraceString(e)}")
            // Don't re-throw - let the app continue if possible
        }
    }
    
    private fun testSystemServices() {
        try {
            Log.d(TAG, "Testing system services...")
            
            val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
            Log.d(TAG, "USB Manager: ${if (usbManager != null) "Available" else "NOT AVAILABLE"}")
            
            val pm = packageManager
            Log.d(TAG, "Package Manager: Available")
            
            val hasUsbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
            Log.d(TAG, "USB Host feature: $hasUsbHost")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing system services: ${e.message}", e)
        }
    }
    
    private fun testWchispNative() {
        try {
            Log.d(TAG, "Testing WchispNative class...")
            
            val isLoaded = WchispNative.isLibraryLoaded()
            Log.d(TAG, "WchispNative.isLibraryLoaded() = $isLoaded")
            
            val error = WchispNative.getLoadError()
            Log.d(TAG, "WchispNative.getLoadError() = $error")
            
            val initResult = WchispNative.safeInit()
            Log.d(TAG, "WchispNative.safeInit() = $initResult")
            
            val testHandle = WchispNative.safeOpenDevice(0, 0x4348, 0x55e0, this)
            Log.d(TAG, "WchispNative.safeOpenDevice() = $testHandle")
            
            val chipInfo = WchispNative.safeIdentifyChip(testHandle)
            Log.d(TAG, "WchispNative.safeIdentifyChip() = $chipInfo")
            
            Log.d(TAG, "WchispNative class tests passed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing WchispNative: ${e.message}", e)
        }
    }
    
    private fun testManifestPermissions() {
        try {
            Log.d(TAG, "Testing manifest permissions...")
            
            val pm = packageManager
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val permissions = packageInfo.requestedPermissions
            
            if (permissions != null) {
                Log.d(TAG, "Requested permissions: ${permissions.joinToString(", ")}")
            } else {
                Log.d(TAG, "No permissions requested")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing manifest permissions: ${e.message}", e)
        }
    }
    
    private fun testLayoutResources() {
        try {
            Log.d(TAG, "Testing layout resources...")
            
            // Try to access the main layout resource
            val layoutId = resources.getIdentifier("activity_main", "layout", packageName)
            Log.d(TAG, "activity_main layout ID: $layoutId")
            
            val appName = getString(R.string.app_name)
            Log.d(TAG, "App name string: $appName")
            
            Log.d(TAG, "Layout resources test passed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing layout resources: ${e.message}", e)
        }
    }
}