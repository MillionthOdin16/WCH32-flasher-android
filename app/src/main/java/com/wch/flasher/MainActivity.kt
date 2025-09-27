package com.wch.flasher

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wch.flasher.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var usbManager: UsbManager? = null
    private var deviceHandle: Int = INVALID_HANDLE
        get() = field.takeIf { it != INVALID_HANDLE } ?: INVALID_HANDLE
        set(value) {
            if (field != INVALID_HANDLE && field != value) {
                // Clean up previous handle
                try {
                    WchispNative.safeCloseDevice(field)
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous device handle: ${e.message}")
                }
            }
            field = value
        }
    private var connectedDevice: UsbDevice? = null
    private var selectedFirmwareUri: Uri? = null
    private var receiverRegistered = false
    
    companion object {
        private const val TAG = "WCH32Flasher"
        private const val ACTION_USB_PERMISSION = "com.wch.flasher.USB_PERMISSION"
        private const val CONNECTION_TIMEOUT_MS = 30000L // 30 seconds
        private const val INVALID_HANDLE = -1
        
        // WCH ISP device VID/PID combinations (from wchisp source)
        private val SUPPORTED_DEVICES = setOf(
            Pair(0x4348, 0x55e0), // WCH - USB ISP mode
            Pair(0x1a86, 0x55e0), // QinHeng Electronics - USB ISP mode
            Pair(0x1a86, 0x7523), // CH340 - Serial programming mode
            Pair(0x1a86, 0x5523)  // CH341 - Serial programming mode
        )
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFirmwareUri = it
            updateFirmwareFileDisplay(it)
            updateFlashButtonState()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            logMessage("Storage permissions granted")
        } else {
            logMessage("Storage permissions required for file access")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                logMessage("USB permission granted for device: ${it.deviceName}")
                                onDeviceConnected(it)
                            }
                        } else {
                            logMessage("USB permission denied")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { checkAndRequestDevice(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { onDeviceDisconnected(it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "*** MainActivity.onCreate() STARTING - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ***")
        
        // Set up global exception handler to prevent crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "*** UNCAUGHT EXCEPTION in thread ${thread.name} ***", exception)
            Log.e(TAG, "Exception details: ${exception.message}")
            Log.e(TAG, "Stack trace: ${exception.stackTraceToString()}")
            // Instead of crashing, log and try to recover gracefully
            runOnUiThread {
                try {
                    if (::binding.isInitialized) {
                        logMessage("❌ ERROR: ${exception.localizedMessage ?: "Unknown error occurred"}")
                        logMessage("📱 App recovered from error - basic functionality available")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to recover from exception", e)
                }
            }
        }
        
        try {
            Log.d(TAG, "Step 1: super.onCreate()")
            super.onCreate(savedInstanceState)
            
            Log.d(TAG, "Step 2: View binding")
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            Log.d(TAG, "Step 3: Initialize USB manager safely")
            initializeUsbManager()

            Log.d(TAG, "Step 4: Setup UI")
            setupUI()
            
            Log.d(TAG, "Step 5: Request permissions safely")
            safeRequestPermissions()
            
            Log.d(TAG, "Step 6: Register USB receiver safely")
            safeRegisterUsbReceiver()
            
            Log.d(TAG, "Step 7: Check existing devices safely")
            safeCheckExistingDevices()
            
            Log.d(TAG, "Step 8: Complete initialization")
            
            updateInitialStatus()
            
            // Check if launched from USB device attachment
            handleUsbDeviceIntent(intent)
            
            Log.d(TAG, "*** MainActivity.onCreate() COMPLETED SUCCESSFULLY ***")
            
        } catch (e: Exception) {
            Log.e(TAG, "*** CRASH in MainActivity.onCreate() ***", e)
            handleInitializationError(e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with action: ${intent.action}")
        handleUsbDeviceIntent(intent)
    }
    
    private fun handleUsbDeviceIntent(intent: Intent) {
        try {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        logMessage("🔌 USB device attached: ${it.deviceName}")
                        logMessage("Device VID:${String.format("0x%04X", it.vendorId)}, PID:${String.format("0x%04X", it.productId)}")
                        
                        if (isSupportedDevice(it)) {
                            logMessage("✅ Supported WCH device detected!")
                            checkAndRequestDevice(it)
                        } else {
                            logMessage("❌ Unsupported device - WCH32 Flasher only supports WCH ISP devices")
                            logMessage("Supported: VID:0x4348 or 0x1A86, PID:0x55E0")
                        }
                    }
                }
                else -> {
                    // App was launched normally or from other intents
                    Log.d(TAG, "App launched with intent action: ${intent.action}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling USB device intent: ${e.message}")
            logMessage("Error processing USB device connection: ${e.message}")
        }
    }

    private fun setDeviceStatus(message: String, isConnected: Boolean = false, isError: Boolean = false) {
        val icon = when {
            isError -> "❌"
            isConnected -> "✅" 
            else -> "⚪"
        }
        
        val formattedMessage = "$icon $message"
        binding.tvDeviceStatus.text = formattedMessage
        
        // Set text color based on status
        val colorRes = when {
            isError -> R.color.status_error
            isConnected -> R.color.status_connected
            else -> R.color.status_disconnected
        }
        
        binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun updateInitialStatus() {
        // Set initial app state
        logMessage("WCH32 Flasher started - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        if (WchispNative.isLibraryLoaded()) {
            logMessage("✓ ${getString(R.string.native_library_loaded)}")
            logMessage("Ready to connect WCH32 devices via USB")
            setDeviceStatus(getString(R.string.no_device_connected))
        } else {
            logMessage("⚠ ${getString(R.string.native_library_not_available)}: ${WchispNative.getLoadError()}")
            logMessage("Please ensure native library is built and included")
            setDeviceStatus("${getString(R.string.native_library_not_available)} - Limited functionality", isError = true)
        }
    }

    private fun initializeUsbManager() {
        try {
            usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
            if (usbManager == null) {
                Log.w(TAG, "USB Manager not available on this device")
            } else {
                Log.d(TAG, "USB Manager initialized successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing USB Manager: ${e.message}")
            usbManager = null
        }
    }

    override fun onDestroy() {
        try {
            // Clean up device handle
            safeCloseDevice()
            
            super.onDestroy()
            safeUnregisterReceiver()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onDestroy: ${e.message}")
        }
    }
    
    private fun safeCloseDevice() {
        if (deviceHandle != INVALID_HANDLE) {
            try {
                WchispNative.safeCloseDevice(deviceHandle)
                logMessage("🔌 Device connection closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing device: ${e.message}")
            } finally {
                deviceHandle = INVALID_HANDLE
                connectedDevice = null
                setDeviceStatus(getString(R.string.status_ready), isConnected = false)
                updateFlashButtonState()
            }
        }
    }

    private fun handleInitializationError(e: Exception) {
        Log.e(TAG, "MainActivity initialization failed, entering minimal mode", e)
        
        // Try to set up a minimal UI that won't crash
        try {
            if (!::binding.isInitialized) {
                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)
            }
            
            // Set up basic UI without risky functionality
            setupMinimalUI()
            
            binding.tvDeviceStatus.text = "App started in minimal mode due to initialization error"
            logMessage("ERROR: App started in minimal mode due to: ${e.message}")
            logMessage("This may be due to Android 16 compatibility issues")
            logMessage("Basic functionality is available - please connect a WCH device")
            
        } catch (fallbackError: Exception) {
            Log.e(TAG, "Even minimal mode failed", fallbackError)
        }
    }

    private fun setupMinimalUI() {
        binding.btnSelectFile.setOnClickListener {
            logMessage("File selection available")
            safeOpenFilePicker()
        }
        
        binding.btnFlash.setOnClickListener {
            logMessage("Flash operation started...")
            startFlashing()
        }
        
        binding.btnErase.setOnClickListener {
            logMessage("Erase operation started...")
            eraseChip()
        }
        
        // Initially disable buttons until device is connected
        binding.btnFlash.isEnabled = false
        binding.btnErase.isEnabled = false
    }

    private fun setupUI() {
        try {
            binding.btnSelectFile.setOnClickListener {
                safeOpenFilePicker()
            }

            binding.btnFlash.setOnClickListener {
                startFlashing()
            }

            binding.btnErase.setOnClickListener {
                eraseChip()
            }

            updateFlashButtonState()
            Log.d(TAG, "UI setup completed successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up UI: ${e.message}")
            setupMinimalUI()
        }
    }

    private fun safeOpenFilePicker() {
        try {
            // Restrict to firmware file types only
            val mimeTypes = arrayOf(
                "application/octet-stream", // For .bin files
                "text/plain", // For .hex files
                "*/*" // Fallback for .elf and other firmware files
            )
            filePickerLauncher.launch(mimeTypes)
        } catch (e: Exception) {
            Log.w(TAG, "Error opening file picker: ${e.message}")
            logMessage("File picker error: ${e.message}")
        }
    }

    private fun safeRequestPermissions() {
        try {
            Log.d(TAG, "Requesting permissions safely for Android ${Build.VERSION.SDK_INT}")
            val permissions = mutableListOf<String>()
            
            // Android 16 has different permission requirements
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission not available: ${e.message}")
                }
            }

            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions.toTypedArray())
            } else {
                Log.d(TAG, "No permissions needed or all permissions already granted")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting permissions: ${e.message}")
            logMessage("Permission request failed - continuing with basic functionality")
        }
    }

    private fun safeRegisterUsbReceiver() {
        try {
            if (usbManager == null) {
                Log.d(TAG, "Skipping USB receiver registration - no USB manager")
                return
            }
            
            Log.d(TAG, "Registering USB receiver safely")
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            
            ContextCompat.registerReceiver(
                this,
                usbReceiver, 
                filter, 
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            Log.d(TAG, "USB receiver registered successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register USB receiver: ${e.message}")
            logMessage("USB receiver registration failed - USB detection disabled")
        }
    }
    
    private fun safeUnregisterReceiver() {
        try {
            if (receiverRegistered) {
                unregisterReceiver(usbReceiver)
                receiverRegistered = false
                Log.d(TAG, "USB receiver unregistered successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering USB receiver: ${e.message}")
        }
    }

    private fun safeCheckExistingDevices() {
        try {
            if (usbManager == null) {
                Log.d(TAG, "Skipping device check - no USB manager")
                return
            }
            
            Log.d(TAG, "Checking existing USB devices safely")
            val devices = usbManager!!.deviceList
            
            if (devices.isEmpty()) {
                Log.d(TAG, "No USB devices connected")
                logMessage("No USB devices detected")
                logMessage("Connect a WCH32 device in ISP mode to begin programming")
                return
            }
            
            logMessage("Found ${devices.size} USB device(s), checking for WCH32 ISP devices...")
            
            var foundDevice = false
            var wchDevicesFound = 0
            var serialDevicesFound = 0
            
            for (device in devices.values) {
                val deviceInfo = "VID:0x${String.format("%04X", device.vendorId)}, PID:0x${String.format("%04X", device.productId)}"
                logMessage("📱 USB Device: ${device.deviceName} ($deviceInfo)")
                
                // Check if it's a WCH-related device
                if (device.vendorId == 0x1A86 || device.vendorId == 0x4348) {
                    when (device.productId) {
                        0x55E0 -> {
                            wchDevicesFound++
                            logMessage("✅ WCH32 USB ISP device found: ${device.deviceName}")
                            checkAndRequestDevice(device)
                            foundDevice = true
                            break // Handle first supported device found
                        }
                        0x7523 -> {
                            wchDevicesFound++
                            logMessage("✅ CH340 USB-serial converter found: ${device.deviceName}")
                            logMessage("🔗 Ready for WCH32 serial programming mode")
                            checkAndRequestDevice(device)
                            foundDevice = true
                            break
                        }
                        0x5523 -> {
                            wchDevicesFound++
                            logMessage("✅ CH341 USB-serial converter found: ${device.deviceName}")
                            logMessage("🔗 Ready for WCH32 serial programming mode")
                            checkAndRequestDevice(device)
                            foundDevice = true
                            break
                        }
                        else -> {
                            logMessage("ℹ️ WCH device found but not in supported programming mode (PID:0x${String.format("%04X", device.productId)})")
                        }
                    }
                } else if (isSupportedDevice(device)) {
                    wchDevicesFound++
                    logMessage("✅ Supported WCH device found: ${device.deviceName}")
                    checkAndRequestDevice(device)
                    foundDevice = true
                    break // Handle first supported device found
                }
            }
            
            if (!foundDevice) {
                if (wchDevicesFound == 0 && serialDevicesFound > 0) {
                    logMessage("💡 Found ${serialDevicesFound} WCH USB-serial device(s), but no WCH32 microcontrollers")
                    logMessage("💡 To program WCH32 microcontrollers:")
                    logMessage("   1. Put your WCH32 device into ISP/bootloader mode")
                    logMessage("   2. Hold BOOT button while powering on")
                    logMessage("   3. Or connect BOOT pin to VCC during reset")
                    logMessage("   4. Device should appear as PID:0x55E0 when ready")
                } else if (wchDevicesFound == 0) {
                    Log.d(TAG, "No supported WCH devices found")
                    logMessage("❌ No WCH32 ISP devices found")
                    logMessage("🎯 WCH32 Flasher requires:")
                    logMessage("   • WCH32 microcontrollers (CH32V003, CH32V203, CH32F103, etc.)")
                    logMessage("   • Device must be in ISP/bootloader mode (PID:0x55E0)")
                    logMessage("   • VID: 0x4348 (WCH) or 0x1A86 (QinHeng)")
                    logMessage("💡 Make sure your device is in ISP mode before connecting")
                } else {
                    Log.d(TAG, "Found $wchDevicesFound WCH device(s) but none in ISP mode")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing devices: ${e.message}")
            logMessage("❌ Device detection failed: ${e.message}") 
            logMessage("This may be due to USB host support limitations")
        }
    }

    private fun isSupportedDevice(device: UsbDevice): Boolean {
        val vidPid = Pair(device.vendorId, device.productId)
        return SUPPORTED_DEVICES.contains(vidPid)
    }

    private fun checkAndRequestDevice(device: UsbDevice) {
        if (!isSupportedDevice(device) || usbManager == null) {
            if (!isSupportedDevice(device)) {
                val deviceVid = String.format("0x%04X", device.vendorId)
                val devicePid = String.format("0x%04X", device.productId)
                
                logMessage("❌ Unsupported device: VID:$deviceVid, PID:$devicePid")
                
                // Provide specific guidance based on the device type
                when (device.productId) {
                    0x7523 -> {
                        logMessage("❌ CH340 device found but not supported in this configuration")
                        logMessage("💡 Note: CH340 serial programming is now supported!")
                        logMessage("💡 Make sure your WCH32 device is connected to the CH340 via UART")
                        logMessage("💡 Device should be in bootloader mode for serial programming")
                    }
                    0x5523 -> {
                        logMessage("❌ CH341 device found but not supported in this configuration")
                        logMessage("💡 Note: CH341 serial programming is now supported!")
                        logMessage("💡 Make sure your WCH32 device is connected to the CH341 via UART")
                    }
                    else -> {
                        logMessage("ℹ️ This device is not a supported WCH32 programming interface")
                    }
                }
                
                logMessage("🎯 WCH32 Flasher now supports:")
                logMessage("   • USB ISP mode: VID:0x4348/0x1A86, PID:0x55E0")
                logMessage("   • Serial mode: CH340 (PID:0x7523) or CH341 (PID:0x5523)")
                logMessage("💡 For serial programming:")
                logMessage("   1. Connect WCH32 device UART to CH340/CH341")
                logMessage("   2. Ensure WCH32 is in bootloader mode") 
                logMessage("   3. Use appropriate baud rate (usually 115200)")
            }
            return
        }

        try {
            if (usbManager!!.hasPermission(device)) {
                logMessage("✅ USB permission already granted for ${device.deviceName}")
                onDeviceConnected(device)
            } else {
                logMessage("🔐 Requesting USB permission for ${device.deviceName}")
                logMessage("Please allow USB access in the system dialog")
                
                val permissionIntent = PendingIntent.getBroadcast(
                    this, 0, Intent(ACTION_USB_PERMISSION), 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                usbManager!!.requestPermission(device, permissionIntent)
                
                // Update UI to show permission request
                setDeviceStatus("Requesting permission for ${device.deviceName}...", isConnected = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting device permission: ${e.message}")
            logMessage("❌ USB permission request failed: ${e.message}")
            setDeviceStatus("Permission request failed for ${device.deviceName}", isError = true)
        }
    }

    private fun onDeviceConnected(device: UsbDevice) {
        try {
            connectedDevice = device
            val deviceName = "${device.deviceName} (VID:${String.format("%04X", device.vendorId)}, PID:${String.format("%04X", device.productId)})"
            
            // Determine programming mode based on PID
            val programmingMode = when (device.productId) {
                0x55E0 -> "USB ISP"
                0x7523 -> "CH340 Serial"
                0x5523 -> "CH341 Serial"
                else -> "Unknown"
            }
            
            logMessage("✅ Device connected: ${device.deviceName}")
            logMessage("Device details: VID:0x${String.format("%04X", device.vendorId)}, PID:0x${String.format("%04X", device.productId)}")
            logMessage("🔗 Programming mode: $programmingMode")
            
            // Check native library status first
            if (!WchispNative.isLibraryLoaded()) {
                logMessage("⚠ WARNING: ${getString(R.string.native_library_not_available)}")
                logMessage("Reason: ${WchispNative.getLoadError()}")
                setDeviceStatus("$deviceName - ${getString(R.string.native_library_not_available)}", isError = true)
                logMessage("Device connected but native library unavailable - limited functionality")
                updateFlashButtonState()
                return
            }
            
            // Show connection in progress
            setDeviceStatus("Connecting to $deviceName ($programmingMode)...", isConnected = false)
            
            // Perform connection operations in background with timeout
            lifecycleScope.launch {
                try {
                    val success = connectWithTimeout(device, programmingMode)
                    if (success) {
                        logMessage("🎉 Device ready for programming operations via $programmingMode!")
                    } else {
                        setDeviceStatus("$deviceName - Connection timeout", isError = true)
                        logMessage("❌ Connection failed or timed out - please retry")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in device connection process", e)
                    setDeviceStatus("$deviceName - Connection error", isError = true)
                    logMessage("❌ Connection error: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDeviceConnected", e)
            setDeviceStatus("Device connection failed", isError = true)
            logMessage("❌ Device connection failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private suspend fun connectWithTimeout(device: UsbDevice, programmingMode: String): Boolean {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                try {
                    // Initialize native library
                    if (!WchispNative.safeInit()) {
                        logMessage("❌ ERROR: Failed to initialize native library")
                        return@withTimeoutOrNull false
                    }
                    
                    // Open device connection using USB Host API
                    val usbConnection = usbManager?.openDevice(device)
                    if (usbConnection == null) {
                        logMessage("❌ ERROR: Failed to open USB device connection")
                        logMessage("This may be due to insufficient permissions or device access restrictions")
                        return@withTimeoutOrNull false
                    }
                    
                    try {
                        when (programmingMode) {
                            "USB ISP" -> {
                                logMessage("🔗 USB ISP connection established, opening device in native library...")
                            }
                            "CH340 Serial", "CH341 Serial" -> {
                                logMessage("🔗 Serial connection established via $programmingMode")
                                logMessage("📡 Ready for WCH32 UART bootloader communication")
                            }
                        }
                        
                        // Get the device handle from native library
                        val handle = WchispNative.safeOpenDevice(
                            usbConnection.fileDescriptor, 
                            device.vendorId, 
                            device.productId, 
                            usbConnection
                        )
                        
                        if (handle <= 0) {
                            logMessage("❌ ERROR: Failed to open device in native library")
                            logMessage("Error: ${WchispNative.safeGetLastError()}")
                            return@withTimeoutOrNull false
                        }
                        
                        deviceHandle = handle
                        logMessage("🔧 Device opened successfully, identifying chip...")
                        
                        // Identify the connected chip
                        val chipInfo = WchispNative.safeIdentifyChip(deviceHandle)
                        logMessage("✅ Chip identification: $chipInfo")
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            val deviceName = "${device.deviceName} (VID:${String.format("%04X", device.vendorId)}, PID:${String.format("%04X", device.productId)})"
                            setDeviceStatus("$deviceName - $chipInfo", isConnected = true)
                            updateFlashButtonState()
                        }
                        
                        return@withTimeoutOrNull true
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in device setup", e)
                        usbConnection.close()
                        return@withTimeoutOrNull false
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connectWithTimeout", e)
                    return@withTimeoutOrNull false
                }
            } ?: false
        }
    }

    private fun onDeviceDisconnected(device: UsbDevice) {
        if (connectedDevice == device) {
            try {
                // Close native device connection
                if (deviceHandle > 0) {
                    WchispNative.safeCloseDevice(deviceHandle)
                    deviceHandle = -1
                }
                
                connectedDevice = null
                setDeviceStatus(getString(R.string.no_device_connected))
                logMessage("Device disconnected: ${device.deviceName}")
                updateFlashButtonState()
            } catch (e: Exception) {
                Log.w(TAG, "Error handling device disconnection: ${e.message}")
            }
        }
    }

    private fun updateFirmwareFileDisplay(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            val displayName = fileName ?: uri.lastPathSegment ?: "Unknown file"
            binding.tvSelectedFile.text = displayName
            
            // Reset text color first
            binding.tvSelectedFile.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface))
            
            // Validate firmware file and update UI accordingly
            val isValid = validateFirmwareFile(uri, displayName)
            
            if (isValid) {
                logMessage("✓ Selected firmware file: $displayName")
            } else {
                logMessage("❌ Invalid firmware file selected: $displayName")
                // Clear the selection if invalid
                selectedFirmwareUri = null
            }
            
            updateFlashButtonState()
        } catch (e: Exception) {
            Log.w(TAG, "Error reading file info: ${e.message}")
            logMessage("❌ Error reading file info: ${e.message}")
        }
    }
    
    private fun validateFirmwareFile(uri: Uri, fileName: String): Boolean {
        try {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val isValidExtension = when (extension) {
                "bin" -> {
                    logMessage("✓ Binary firmware file detected")
                    true
                }
                "hex" -> {
                    logMessage("✓ Intel HEX firmware file detected")
                    true
                }
                "elf" -> {
                    logMessage("✓ ELF firmware file detected")
                    true
                }
                else -> {
                    logMessage("❌ ERROR: Unsupported file type '.$extension'")
                    logMessage("Supported file types: .bin (binary), .hex (Intel HEX), .elf (ELF)")
                    binding.tvSelectedFile.text = "$fileName (UNSUPPORTED FORMAT)"
                    binding.tvSelectedFile.setTextColor(ContextCompat.getColor(this, R.color.status_error))
                    false
                }
            }
            
            if (!isValidExtension) {
                return false
            }
            
            // Check file size for valid files
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = inputStream.available()
                when {
                    size == 0 -> {
                        logMessage("❌ ERROR: File is empty")
                        return false
                    }
                    size > 2 * 1024 * 1024 -> { // 2MB limit
                        logMessage("❌ ERROR: File too large (${size / 1024}KB) - Maximum 2MB supported")
                        return false
                    }
                    size < 32 -> {
                        logMessage("❌ ERROR: File too small (${size} bytes) - Minimum 32 bytes required")
                        return false
                    }
                    size > 1024 * 1024 -> {
                        logMessage("⚠ Warning: Large file (${size / 1024}KB) - This may take longer to flash")
                    }
                    else -> {
                        logMessage("✓ File size: ${if (size > 1024) "${size / 1024}KB" else "${size} bytes"}")
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Error validating firmware file: ${e.message}")
            logMessage("❌ ERROR: File validation failed: ${e.message}")
            return false
        }
    }

    private fun getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) {
                            cursor.getString(nameIndex)
                        } else null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying content resolver: ${e.message}")
                    null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    private fun updateFlashButtonState() {
        try {
            // Use the helper method for consistent button state management
            setOperationInProgress(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating button state: ${e.message}")
        }
    }

    private fun startFlashing() {
        selectedFirmwareUri?.let { uri ->
            lifecycleScope.launch {
                try {
                    binding.tvProgressInfo.text = "Reading firmware file..."
                    
                    val firmwareData = withContext(Dispatchers.IO) {
                        readFirmwareFile(uri)
                    }
                    
                    if (firmwareData != null) {
                        performFlashOperation(firmwareData)
                    } else {
                        logMessage("ERROR: Could not read firmware file")
                        binding.tvProgressInfo.text = "Failed to read firmware file"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting flash operation: ${e.message}")
                    logMessage("ERROR: Failed to start flashing: ${e.message}")
                    binding.tvProgressInfo.text = "Failed to start flashing: ${e.message}"
                }
            }
        } ?: run {
            logMessage("ERROR: No firmware file selected")
            binding.tvProgressInfo.text = "No firmware file selected"
        }
    }
    
    private fun readFirmwareFile(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val data = inputStream.readBytes()
                
                // Validate firmware data
                when {
                    data.isEmpty() -> {
                        logMessage("ERROR: Firmware file is empty")
                        null
                    }
                    data.size > 2 * 1024 * 1024 -> { // 2MB limit
                        logMessage("ERROR: Firmware file too large (${data.size / 1024}KB)")
                        null
                    }
                    data.size < 32 -> { // Minimum reasonable firmware size
                        logMessage("WARNING: Firmware file very small (${data.size} bytes)")
                        data // Still allow it
                    }
                    else -> {
                        logMessage("✓ Firmware file loaded: ${data.size} bytes")
                        data
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading firmware file: ${e.message}")
            logMessage("ERROR: Failed to read firmware file: ${e.message}")
            null
        }
    }

    private fun performFlashOperation(firmwareData: ByteArray) {
        logMessage("Flash operation started...")
        logMessage("Firmware size: ${firmwareData.size} bytes")
        
        // Show progress card and progress bar
        binding.cardProgress.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.tvProgressInfo.text = getString(R.string.preparing_flash)
        setOperationInProgress(true)
        
        // Perform flashing operation with coroutines for better async handling
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    // Perform actual flash operation through native library
                    WchispNative.safeFlashFirmware(deviceHandle, firmwareData)
                }
                
                // Update UI on main thread
                binding.progressBar.progress = 100
                binding.tvProgressInfo.text = getString(R.string.operation_completed)
                
                if (success) {
                    logMessage("✓ Flash operation completed successfully")
                    
                    // Optionally verify firmware
                    binding.tvProgressInfo.text = getString(R.string.verification_in_progress)
                    logMessage("Verifying firmware...")
                    val verified = withContext(Dispatchers.IO) {
                        WchispNative.safeVerifyFirmware(deviceHandle, firmwareData)
                    }
                    
                    if (verified) {
                        logMessage("✓ Firmware verification passed")
                        binding.tvProgressInfo.text = "✓ Firmware verified successfully"
                    } else {
                        logMessage("⚠ Firmware verification failed")
                        binding.tvProgressInfo.text = "⚠ Firmware verification failed"
                    }
                    
                    // Reset chip to run new firmware
                    binding.tvProgressInfo.text = getString(R.string.resetting_chip)
                    val resetSuccess = withContext(Dispatchers.IO) {
                        WchispNative.safeResetChip(deviceHandle)
                    }
                    
                    if (resetSuccess) {
                        logMessage("✓ ${getString(R.string.chip_reset_success)}")
                        binding.tvProgressInfo.text = "✓ ${getString(R.string.chip_reset_success)}"
                    } else {
                        logMessage("⚠ ${getString(R.string.chip_reset_failed)}")
                        binding.tvProgressInfo.text = "⚠ ${getString(R.string.chip_reset_failed)}"
                    }
                } else {
                    val error = WchispNative.safeGetLastError()
                    logMessage("✗ Flash operation failed: $error")
                    binding.tvProgressInfo.text = "✗ ${getString(R.string.operation_failed)}: $error"
                }
                
                // Hide progress after delay
                delay(3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during flash operation", e)
                logMessage("✗ Flash operation failed: ${e.message}")
                binding.tvProgressInfo.text = "✗ ${getString(R.string.operation_failed)}: ${e.message}"
                delay(3000)
            } finally {
                // Always restore UI state
                binding.progressBar.visibility = View.GONE
                binding.cardProgress.visibility = View.GONE
                binding.tvProgressInfo.text = getString(R.string.ready_to_flash)
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        val hasDevice = (connectedDevice != null || deviceHandle > 0)
        val hasFile = selectedFirmwareUri != null
        
        binding.btnFlash.isEnabled = !inProgress && hasDevice && hasFile
        binding.btnErase.isEnabled = !inProgress && hasDevice
        binding.btnSelectFile.isEnabled = !inProgress
    }

    private fun eraseChip() {
        logMessage("Chip erase started...")
        setOperationInProgress(true)
        
        // Perform erase operation with coroutines
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    WchispNative.safeEraseChip(deviceHandle)
                }
                
                if (success) {
                    logMessage("✓ Chip erase completed successfully")
                } else {
                    val error = WchispNative.safeGetLastError()
                    logMessage("✗ Chip erase failed: $error")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during erase operation", e)
                logMessage("✗ Chip erase failed: ${e.message}")
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun logMessage(message: String) {
        Log.d(TAG, message)
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            
            runOnUiThread {
                binding.tvLog.append(logEntry)
                // Auto-scroll to bottom
                binding.tvLog.post {
                    val scrollView = binding.tvLog.parent as? android.widget.ScrollView
                    scrollView?.fullScroll(View.FOCUS_DOWN)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error logging message: ${e.message}")
        }
    }
}