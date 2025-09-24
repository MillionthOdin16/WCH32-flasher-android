package com.wch.flasher

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
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
import com.wch.flasher.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbManager: UsbManager
    private var connectedDevice: UsbDevice? = null
    private var selectedFirmwareUri: Uri? = null
    
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        setupUI()
        requestPermissions()
        registerUsbReceiver()
        checkExistingDevices()
        
        logMessage("WCH32 Flasher started")
        logMessage("Ready. Connect a WCH device to begin.")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    private fun setupUI() {
        binding.btnSelectFile.setOnClickListener {
            openFilePicker()
        }

        binding.btnFlash.setOnClickListener {
            startFlashing()
        }

        binding.btnErase.setOnClickListener {
            eraseChip()
        }

        updateFlashButtonState()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    private fun checkExistingDevices() {
        val devices = usbManager.deviceList
        for (device in devices.values) {
            if (isSupportedDevice(device)) {
                checkAndRequestDevice(device)
                break // Handle first supported device found
            }
        }
    }

    private fun isSupportedDevice(device: UsbDevice): Boolean {
        val vidPid = Pair(device.vendorId, device.productId)
        return SUPPORTED_DEVICES.contains(vidPid)
    }

    private fun checkAndRequestDevice(device: UsbDevice) {
        if (!isSupportedDevice(device)) {
            return
        }

        if (usbManager.hasPermission(device)) {
            onDeviceConnected(device)
        } else {
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            logMessage("Requesting USB permission for device: ${device.deviceName}")
        }
    }

    private fun onDeviceConnected(device: UsbDevice) {
        connectedDevice = device
        val deviceInfo = "Device connected: ${device.deviceName} (VID:${String.format("%04X", device.vendorId)}, PID:${String.format("%04X", device.productId)})"
        binding.tvDeviceStatus.text = deviceInfo
        logMessage(deviceInfo)
        updateFlashButtonState()
        
        // TODO: Initialize native wchisp connection
        // This is where we'll call into the native Rust layer
    }

    private fun onDeviceDisconnected(device: UsbDevice) {
        if (connectedDevice == device) {
            connectedDevice = null
            binding.tvDeviceStatus.text = getString(R.string.no_device_connected)
            logMessage("Device disconnected: ${device.deviceName}")
            updateFlashButtonState()
        }
    }

    private fun openFilePicker() {
        try {
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            logMessage("Error opening file picker: ${e.message}")
        }
    }

    private fun updateFirmwareFileDisplay(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            binding.tvSelectedFile.text = fileName ?: uri.lastPathSegment ?: "Unknown file"
            logMessage("Selected firmware file: ${binding.tvSelectedFile.text}")
        } catch (e: Exception) {
            logMessage("Error reading file info: ${e.message}")
        }
    }

    private fun getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    private fun updateFlashButtonState() {
        val hasDevice = connectedDevice != null
        val hasFile = selectedFirmwareUri != null
        
        binding.btnFlash.isEnabled = hasDevice && hasFile
        binding.btnErase.isEnabled = hasDevice
    }

    private fun startFlashing() {
        // TODO: Implement native flashing via JNI
        logMessage("Flash operation started...")
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        
        // Disable buttons during operation
        binding.btnFlash.isEnabled = false
        binding.btnErase.isEnabled = false
        
        // This is a placeholder - will be replaced with actual JNI calls
        simulateFlashProgress()
    }

    private fun eraseChip() {
        // TODO: Implement native chip erase via JNI
        logMessage("Chip erase started...")
        binding.btnFlash.isEnabled = false
        binding.btnErase.isEnabled = false
        
        // Placeholder implementation
        logMessage("Chip erase completed (placeholder)")
        updateFlashButtonState()
    }

    private fun simulateFlashProgress() {
        // Temporary simulation - will be replaced with actual JNI implementation
        Thread {
            for (progress in 0..100 step 10) {
                Thread.sleep(200)
                runOnUiThread {
                    binding.progressBar.progress = progress
                    logMessage("Flashing... ${progress}%")
                }
            }
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                logMessage("Flash completed successfully (placeholder)")
                updateFlashButtonState()
            }
        }.start()
    }

    private fun logMessage(message: String) {
        Log.d(TAG, message)
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
    }
}