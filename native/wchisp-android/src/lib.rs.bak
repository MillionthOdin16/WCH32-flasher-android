//! WCH32 Android Flasher Native Library
//! 
//! This native library provides JNI bindings for the WCH ISP functionality,
//! replacing libusb dependencies with Android USB Host API integration.

use jni::objects::{JClass, JByteArray};
use jni::sys::{jint, jstring, jbyteArray, jboolean};
use jni::JNIEnv;
use log::{info, error};

pub mod transport;
pub mod device;
pub mod protocol;
pub mod flashing;

// TODO: Uncomment when modules are fully implemented
// use crate::transport::AndroidUsbTransport;
// use crate::flashing::AndroidFlashing;

/// Initialize the native library and logging
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_init(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    // Initialize Android logger
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("wchisp-native")
    );

    info!("WCH ISP native library initialized");
    true as jboolean
}

/// Open USB device connection using Android USB Host API
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_openDevice(
    _env: JNIEnv,
    _class: JClass,
    device_fd: jint,
    vendor_id: jint,
    product_id: jint,
) -> jint {
    info!("Opening USB device with FD: {}, VID: 0x{:04X}, PID: 0x{:04X}", 
          device_fd, vendor_id as u16, product_id as u16);
    
    // TODO: Create AndroidUsbTransport instance and store handle
    // For now, return a placeholder handle
    1 // Success, return handle ID
}

/// Close USB device connection
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_closeDevice(
    _env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Closing device handle: {}", handle);
    
    // TODO: Close transport and clean up resources
    true as jboolean
}

/// Identify connected chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_identifyChip(
    env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jstring {
    info!("Identifying chip on handle: {}", handle);
    
    // TODO: Implement chip identification via AndroidFlashing
    // For now, return placeholder chip info
    let chip_info = "CH32V307VCT6[0x7017] (Code Flash: 256KiB)";
    
    match env.new_string(chip_info) {
        Ok(jstr) => jstr.into_raw(),
        Err(e) => {
            error!("Failed to create Java string: {}", e);
            std::ptr::null_mut()
        }
    }
}

/// Flash firmware to the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_flashFirmware(
    env: JNIEnv,
    _class: JClass,
    handle: jint,
    firmware_data: jbyteArray,
) -> jboolean {
    info!("Starting firmware flash on handle: {}", handle);
    
    // Convert Java byte array to Rust Vec<u8>
    let firmware = {
        // Create JByteArray from raw pointer
        let array = unsafe { JByteArray::from_raw(firmware_data) };
        match env.convert_byte_array(&array) {
            Ok(data) => data,
            Err(e) => {
                error!("Failed to convert firmware data: {}", e);
                return false as jboolean;
            }
        }
    };
    
    info!("Firmware size: {} bytes", firmware.len());
    
    // TODO: Implement actual flashing via AndroidFlashing
    // This is a placeholder implementation
    
    true as jboolean
}

/// Erase chip flash memory
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_eraseChip(
    _env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Erasing chip on handle: {}", handle);
    
    // TODO: Implement chip erase via AndroidFlashing
    
    true as jboolean
}

/// Verify firmware on the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_verifyFirmware(
    env: JNIEnv,
    _class: JClass,
    handle: jint,
    firmware_data: jbyteArray,
) -> jboolean {
    info!("Verifying firmware on handle: {}", handle);
    
    let firmware = {
        // Create JByteArray from raw pointer
        let array = unsafe { JByteArray::from_raw(firmware_data) };
        match env.convert_byte_array(&array) {
            Ok(data) => data,
            Err(e) => {
                error!("Failed to convert firmware data: {}", e);
                return false as jboolean;
            }
        }
    };
    
    // TODO: Implement verification via AndroidFlashing
    
    true as jboolean
}

/// Reset the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_resetChip(
    env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Resetting chip on handle: {}", handle);
    
    // TODO: Implement chip reset via AndroidFlashing
    
    true as jboolean
}

/// Get last error message
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_getLastError(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    // TODO: Implement proper error handling
    let error_msg = "No error";
    
    match env.new_string(error_msg) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}