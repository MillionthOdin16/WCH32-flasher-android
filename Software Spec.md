CH32 USB Flasher Android Application Specification (Rust-based wchisp variant)
This specification outlines the requirements for an Android application designed to flash CH32 family microcontrollers via USB. It updates the original specification by selecting Rust and the wchisp tool for the native flashing logic. This approach leverages Rust's memory safety and modern tooling while maintaining the same user-facing functionality.
1. Functional requirements
1.1. Device connection
The application must automatically detect and list supported USB devices (including the CH32's bootloader or a supported USB-to-serial adapter) when connected via an OTG adapter.
The user must be prompted to grant USB access permission when a supported device is first connected.
1.2. Firmware management
The application must provide a file browser for the user to select the firmware file (.hex or .bin) from the device's storage.
The app must display information about the selected firmware file (e.g., file size, format).
1.3. Chip identification
The app should use the wchisp probe functionality to detect and display the target chip's ID in the user interface.
The app must support multiple CH32 chip variants that support either USB ISP or UART ISP flashing. The UI should make it clear which transport is being used.
1.4. Flashing procedure
A "Flash" button must be available to initiate the programming process. The flashing procedure, which will be handled by the native wchisp code, must follow these steps:
Read protection: Check if the chip is read-protected. If so, inform the user and abort or prompt to unlock.
Flash erase: Erase the target flash memory of the chip.
Firmware upload: Write the selected firmware file to the chip's flash memory.
Verification: Read back the programmed flash memory and compare it to the original firmware file.
Status feedback: Provide real-time status updates in the UI (e.g., "Erasing...", "Writing 25%...", "Verification successful").
Reset: Optionally, the app can provide a button to reset the chip after a successful flash.
1.5. Log and error handling
The application must display a log window showing detailed output of the flashing process.
All errors (e.g., USB connection failure, verification mismatch) must be clearly logged and displayed to the user.
2. Technical requirements
2.1. Android compatibility
The minimum supported Android version should be API Level 12 (Android 3.1) or higher.
The app manifest must include the <uses-feature android:name="android.hardware.usb.host"/> tag.
2.2. USB communication
Android USB Host API: This API must be used for communication with the programmer hardware via an OTG cable.
Device filtering: Implement a device_filter.xml file to filter for supported VIDs and PIDs to automatically trigger the USB permission dialog.
Rust integration: The native Rust code must be adapted to use the Android USB Host API instead of libusb for USB access. This will require bridging the gap between the Rust and Android layers using a specialized library or custom code.
2.3. Native code (Rust) integration
Rust crate: The application will use a forked or modified version of the wchisp tool as its native Rust library.
JNI wrapper: A Java Native Interface (JNI) bridge will be implemented to expose the Rust flashing functions to the Android application's Kotlin/Java code.
Cross-compilation: The cargo-ndk tool must be used to cross-compile the Rust library for all target Android architectures (e.g., aarch64, armv7).
Firmware data transfer: The app will use JNI to pass the firmware file data from the Kotlin/Java layer to the native Rust code.
2.4. Hardware compatibility
The application must support CH32 chips that use USB ISP (requiring an OTG connection to the chip's bootloader) and UART ISP (requiring a USB-to-serial adapter).
The app should clearly indicate the type of connection and flashing method to the user.
3. User experience (UX) requirements
3.1. User interface
The UI must be simple and intuitive, with a clear layout for selecting files, connecting devices, and starting the flash process.
The log output should be displayed in a clear, scrollable text box.
3.2. Error messaging
Error messages should be clear and actionable, such as "USB device not found" or "Flash verification failed".
3.3. Application icon
The app must have a unique and recognizable icon.
4. Project considerations
4.1. Open-source contribution
The project should be open-sourced to encourage community contributions and support for a wider range of CH32 chips and features.
4.2. Refactoring wchisp for Android
Challenge: The most significant development task is refactoring the wchisp codebase to remove its libusb dependency and replace it with a new USB communication layer that uses the Android USB Host API via JNI.
Solution: A developer must create a new Rust module or crate that handles Android-specific USB communication and expose it to the core wchisp logic. This will allow the native code to be built for Android while retaining its robust flashing logic.
