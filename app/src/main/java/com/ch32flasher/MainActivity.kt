package com.ch32flasher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var deviceInfoText: TextView
    private lateinit var selectFileButton: Button
    private lateinit var firmwareInfoText: TextView
    private lateinit var flashButton: Button
    private lateinit var logText: TextView

    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var firmware: ByteArray? = null

    private val ACTION_USB_PERMISSION = "com.ch32flasher.USB_PERMISSION"
    private val FILE_SELECT_CODE = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        deviceInfoText = findViewById(R.id.device_info_text)
        selectFileButton = findViewById(R.id.select_file_button)
        firmwareInfoText = findViewById(R.id.firmware_info_text)
        flashButton = findViewById(R.id.flash_button)
        logText = findViewById(R.id.log_text)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // Register a broadcast receiver to handle USB permission requests.
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbPermissionReceiver, permissionFilter)

        // Set up the click listener for the "Select Firmware File" button.
        selectFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            startActivityForResult(intent, FILE_SELECT_CODE)
        }

        flashButton.setOnClickListener {
            firmware?.let {
                val error = nativeFlash(this, it)
                if (error != null) {
                    logMessage("Flashing failed: $error")
                    Toast.makeText(this, "Flashing failed: $error", Toast.LENGTH_SHORT).show()
                } else {
                    logMessage("Flashing successful!")
                    Toast.makeText(this, "Flashing successful!", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Please select a firmware file first.", Toast.LENGTH_SHORT).show()
            }
        }

        checkForUsbDevice()
    }

    /**
     * Handles the result of the file selection activity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_SELECT_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    firmware = inputStream.readBytes()
                    firmwareInfoText.text = "File selected: ${uri.path}, size: ${firmware?.size} bytes"
                }
            }
        }
    }

    /**
     * Checks for connected USB devices and requests permission if a device is found.
     */
    private fun checkForUsbDevice() {
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            // For now, just take the first device found.
            // In a real app, you might want to let the user choose from a list.
            val device = deviceList.values.first()
            this.usbDevice = device
            requestUsbPermission(device)
        } else {
            deviceInfoText.text = "No USB device connected."
        }
    }

    /**
     * Requests permission to access the given USB device.
     */
    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent)
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted. You can now access the device.
                            deviceInfoText.text = "Device connected: ${device.deviceName}"
                            usbConnection = usbManager.openDevice(device)
                            usbConnection?.claimInterface(device.getInterface(0), true)
                        }
                    } else {
                        // Permission denied.
                        deviceInfoText.text = "USB permission denied for device ${device?.deviceName}"
                    }
                }
            }
        }
    }

    /**
     * Sends raw data to the USB device. This method is called from the native Rust code.
     */
    fun sendRaw(data: ByteArray): Int {
        val endpoint = usbDevice?.getInterface(0)?.getEndpoint(1)
        return usbConnection?.bulkTransfer(endpoint, data, data.size, 5000) ?: -1
    }

    /**
     * Receives raw data from the USB device. This method is called from the native Rust code.
     */
    fun recvRaw(size: Int): ByteArray {
        val endpoint = usbDevice?.getInterface(0)?.getEndpoint(0)
        val buffer = ByteArray(size)
        val bytesRead = usbConnection?.bulkTransfer(endpoint, buffer, buffer.size, 5000) ?: -1
        return if (bytesRead > 0) {
            buffer.copyOf(bytesRead)
        } else {
            ByteArray(0)
        }
    }

    /**
     * Calls the native Rust code to flash the firmware to the device.
     *
     * @param activity The MainActivity instance.
     * @param firmware The firmware to flash.
     * @return An error message if flashing fails, or null if it succeeds.
     */
    private external fun nativeFlash(activity: MainActivity, firmware: ByteArray): String?

    /**
     * Logs a message to the on-screen log view. This method is called from the native Rust code.
     *
     * @param message The message to log.
     */
    fun logMessage(message: String) {
        runOnUiThread {
            logText.append(message + "\n")
        }
    }

    companion object {
        // Used to load the 'ch32flasher_lib' library on application startup.
        init {
            System.loadLibrary("ch32flasher_lib")
        }
    }
}
