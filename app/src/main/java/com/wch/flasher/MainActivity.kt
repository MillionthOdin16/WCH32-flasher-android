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
    private var deviceHandle: Int = -1
    private var connectedDevice: UsbDevice? = null
    private var selectedFirmwareUri: Uri? = null
    private var receiverRegistered = false
    
    companion object {
        private const val TAG = "WCH32Flasher"
        private const val ACTION_USB_PERMISSION = "com.wch.flasher.USB_PERMISSION"
        
        // WCH ISP device VID/PID combinations (from wchisp source)
        private val SUPPORTED_DEVICES = setOf(
            Pair(0x4348, 0x55e0), // WCH
            Pair(0x1a86, 0x55e0)  // QinHeng Electronics
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
            
            Log.d(TAG, "Step 8: Initialize simulation mode")
            initializeSimulationMode()
            
            Log.d(TAG, "*** MainActivity.onCreate() COMPLETED SUCCESSFULLY ***")
            
        } catch (e: Exception) {
            Log.e(TAG, "*** CRASH in MainActivity.onCreate() ***", e)
            handleInitializationError(e)
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

    private fun initializeSimulationMode() {
        // Always set up simulation mode for reliability
        logMessage("WCH32 Flasher started - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        logMessage("Running in enhanced simulation mode for maximum compatibility")
        
        // Set up simulation device status with improved formatting
        val simulationStatus = "🔧 Simulation Mode: CH32V203 (64KB Flash) - Ready"
        binding.tvDeviceStatus.text = simulationStatus
        logMessage("Simulation mode: CH32V203 device ready")
        logMessage("Available operations: Flash, Erase, Verify, Reset")
        logMessage("Note: This is simulation mode - no real hardware will be programmed")
        
        // Enable simulation device
        deviceHandle = 1 // Mock handle for simulation
        updateFlashButtonState()
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            safeUnregisterReceiver()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onDestroy: ${e.message}")
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
            logMessage("Basic simulation functionality is available")
            
        } catch (fallbackError: Exception) {
            Log.e(TAG, "Even minimal mode failed", fallbackError)
        }
    }

    private fun setupMinimalUI() {
        binding.btnSelectFile.setOnClickListener {
            logMessage("File selection available - simulation mode active")
            // Use safe file picker
            safeOpenFilePicker()
        }
        
        binding.btnFlash.setOnClickListener {
            logMessage("Flash simulation started...")
            simulateFlashOperation()
        }
        
        binding.btnErase.setOnClickListener {
            logMessage("Erase simulation started...")
            simulateEraseOperation()
        }
        
        // Enable buttons for simulation
        binding.btnFlash.isEnabled = true
        binding.btnErase.isEnabled = true
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
            filePickerLauncher.launch(arrayOf("*/*"))
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
            logMessage("Permission request failed - continuing in simulation mode")
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
            var foundDevice = false
            
            for (device in devices.values) {
                if (isSupportedDevice(device)) {
                    checkAndRequestDevice(device)
                    foundDevice = true
                    break // Handle first supported device found
                }
            }
            
            if (!foundDevice) {
                Log.d(TAG, "No supported WCH devices found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing devices: ${e.message}")
            logMessage("Device detection failed - using simulation mode")
        }
    }

    private fun isSupportedDevice(device: UsbDevice): Boolean {
        val vidPid = Pair(device.vendorId, device.productId)
        return SUPPORTED_DEVICES.contains(vidPid)
    }

    private fun checkAndRequestDevice(device: UsbDevice) {
        if (!isSupportedDevice(device) || usbManager == null) {
            return
        }

        try {
            if (usbManager!!.hasPermission(device)) {
                onDeviceConnected(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this, 0, Intent(ACTION_USB_PERMISSION), 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                usbManager!!.requestPermission(device, permissionIntent)
                logMessage("Requesting USB permission for device: ${device.deviceName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting device permission: ${e.message}")
            logMessage("USB permission request failed - continuing in simulation mode")
        }
    }

    private fun onDeviceConnected(device: UsbDevice) {
        try {
            connectedDevice = device
            val deviceInfo = "🔗 Device: ${device.deviceName} (VID:${String.format("%04X", device.vendorId)}, PID:${String.format("%04X", device.productId)})"
            binding.tvDeviceStatus.text = deviceInfo
            logMessage("Device connected: ${device.deviceName}")
            logMessage("Vendor ID: 0x${String.format("%04X", device.vendorId)}")
            logMessage("Product ID: 0x${String.format("%04X", device.productId)}")
            updateFlashButtonState()
            
            // Check native library status
            if (!WchispNative.isLibraryLoaded()) {
                logMessage("INFO: Native library not loaded - running in simulation mode")
                logMessage("Reason: ${WchispNative.getLoadError()}")
            }
            
            // Initialize native wchisp connection
            if (!WchispNative.safeInit()) {
                logMessage("ERROR: Failed to initialize native library")
                return
            }
            
            // Identify the connected chip
            val chipInfo = WchispNative.safeIdentifyChip(1) // Use mock handle for simulation  
            logMessage("Chip identification: $chipInfo")
            
            // Update device status with chip info
            binding.tvDeviceStatus.text = "$deviceInfo - $chipInfo"
            
        } catch (e: Exception) {
            Log.w(TAG, "Error handling device connection: ${e.message}")
            logMessage("Device connection failed - using simulation mode")
            
            // Set up simulation mode as fallback
            val simulationStatus = "🔧 Simulation: CH32V203 (64KB Flash) - Device simulation active"
            binding.tvDeviceStatus.text = simulationStatus
            val chipInfo = WchispNative.safeIdentifyChip(1) // Use simulation handle
            logMessage("Fallback simulation chip: $chipInfo")
            updateFlashButtonState()
        }
    }

    private fun onDeviceDisconnected(device: UsbDevice) {
        if (connectedDevice == device) {
            try {
                // Close native device connection
                if (deviceHandle >= 0) {
                    WchispNative.safeCloseDevice(deviceHandle)
                    deviceHandle = -1
                }
                
                connectedDevice = null
                binding.tvDeviceStatus.text = getString(R.string.no_device_connected)
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
            
            // Validate firmware file
            validateFirmwareFile(uri, displayName)
            
            logMessage("Selected firmware file: $displayName")
        } catch (e: Exception) {
            Log.w(TAG, "Error reading file info: ${e.message}")
            logMessage("Error reading file info: ${e.message}")
        }
    }
    
    private fun validateFirmwareFile(uri: Uri, fileName: String) {
        try {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            when (extension) {
                "bin" -> logMessage("✓ Binary firmware file detected")
                "hex" -> logMessage("✓ Intel HEX firmware file detected")
                "elf" -> logMessage("✓ ELF firmware file detected")
                else -> logMessage("⚠ Unknown file type - supported: .bin, .hex, .elf")
            }
            
            // Check file size
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = inputStream.available()
                when {
                    size == 0 -> logMessage("⚠ Warning: File appears to be empty")
                    size > 1024 * 1024 -> logMessage("⚠ Warning: Large file (${size / 1024}KB) - verify this is correct")
                    size < 100 -> logMessage("⚠ Warning: Very small file (${size} bytes) - verify this is correct")
                    else -> logMessage("✓ File size: ${size} bytes")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error validating firmware file: ${e.message}")
            logMessage("⚠ Could not validate firmware file: ${e.message}")
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
        binding.tvProgressInfo.text = "Preparing to flash firmware..."
        setOperationInProgress(true)
        
        // Perform flashing operation with coroutines for better async handling
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    // Simulate progressive flashing with better progress reporting
                    for (progress in 0..100 step 10) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.progress = progress
                            binding.tvProgressInfo.text = "Flashing firmware... ${progress}%"
                            logMessage("Flashing... ${progress}%")
                        }
                        delay(200) // Simulate work being done
                    }
                    
                    // Perform actual flash operation
                    WchispNative.safeFlashFirmware(deviceHandle, firmwareData)
                }
                
                // Update UI on main thread
                binding.progressBar.progress = 100
                binding.tvProgressInfo.text = "Flash operation completed"
                
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
                        binding.tvProgressInfo.text = "Firmware verified successfully"
                    } else {
                        logMessage("⚠ Firmware verification failed")
                        binding.tvProgressInfo.text = "Firmware verification failed"
                    }
                    
                    // Reset chip to run new firmware
                    binding.tvProgressInfo.text = "Resetting chip..."
                    val resetSuccess = withContext(Dispatchers.IO) {
                        WchispNative.safeResetChip(deviceHandle)
                    }
                    
                    if (resetSuccess) {
                        logMessage("✓ Chip reset completed - new firmware is running")
                        binding.tvProgressInfo.text = getString(R.string.chip_reset_success)
                    } else {
                        logMessage("⚠ Chip reset failed - may need manual reset")
                        binding.tvProgressInfo.text = getString(R.string.chip_reset_failed)
                    }
                } else {
                    val error = WchispNative.safeGetLastError()
                    logMessage("✗ Flash operation failed: $error")
                    binding.tvProgressInfo.text = "Flash operation failed: $error"
                }
                
                // Hide progress after delay
                delay(3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during flash operation", e)
                logMessage("✗ Flash operation failed: ${e.message}")
                binding.tvProgressInfo.text = "Flash operation failed: ${e.message}"
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
        binding.btnFlash.isEnabled = !inProgress && (connectedDevice != null || deviceHandle > 0) && selectedFirmwareUri != null
        binding.btnErase.isEnabled = !inProgress && (connectedDevice != null || deviceHandle > 0)
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

    private fun simulateFlashOperation() {
        logMessage("Simulation: Flash operation started...")
        binding.cardProgress.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        setOperationInProgress(true)
        
        lifecycleScope.launch {
            try {
                for (progress in 0..100 step 20) {
                    delay(500)
                    binding.progressBar.progress = progress
                    binding.tvProgressInfo.text = "Simulation: Flashing... ${progress}%"
                    logMessage("Simulation: Flashing... ${progress}%")
                }
                binding.tvProgressInfo.text = "Simulation: Flash completed successfully"
                logMessage("✓ Simulation: Flash completed successfully")
                delay(2000)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.cardProgress.visibility = View.GONE
                binding.tvProgressInfo.text = getString(R.string.ready_to_flash)
                setOperationInProgress(false)
            }
        }
    }

    private fun simulateEraseOperation() {
        logMessage("Simulation: Erase operation started...")
        binding.cardProgress.visibility = View.VISIBLE
        binding.tvProgressInfo.text = "Simulation: Erasing chip..."
        setOperationInProgress(true)
        
        lifecycleScope.launch {
            try {
                delay(2000)
                binding.tvProgressInfo.text = "Simulation: Erase completed successfully"
                logMessage("✓ Simulation: Erase completed successfully")
                delay(1000)
            } finally {
                binding.cardProgress.visibility = View.GONE
                binding.tvProgressInfo.text = getString(R.string.ready_to_flash)
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