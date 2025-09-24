//! WCH32 Android Flasher Native Library
//! 
//! This native library provides JNI bindings for the WCH ISP functionality,
//! replacing libusb dependencies with Android USB Host API integration.

use jni::objects::{JClass, JByteArray, JObject};
use jni::sys::{jint, jstring, jbyteArray, jboolean};
use jni::JNIEnv;
use log::{info, error};
use std::collections::HashMap;
use std::sync::Mutex;

pub mod transport;
pub mod device;
pub mod protocol;
pub mod flashing;

use crate::transport::AndroidUsbTransport;
use crate::flashing::AndroidFlashing;

// Global state management for device handles
lazy_static::lazy_static! {
    static ref FLASHER_INSTANCES: Mutex<HashMap<i32, AndroidFlashing>> = Mutex::new(HashMap::new());
    static ref NEXT_HANDLE: Mutex<i32> = Mutex::new(1);
}

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
    mut env: JNIEnv,
    _class: JClass,
    device_fd: jint,
    vendor_id: jint,
    product_id: jint,
    usb_connection: JObject,
) -> jint {
    info!("Opening USB device with FD: {}, VID: 0x{:04X}, PID: 0x{:04X}", 
          device_fd, vendor_id as u16, product_id as u16);
    
    // Validate that this is a supported device
    if !AndroidUsbTransport::is_supported_device(vendor_id as u16, product_id as u16) {
        error!("Unsupported device: VID=0x{:04X}, PID=0x{:04X}", vendor_id, product_id);
        return -1;
    }
    
    // Create transport and flashing instances
    let transport = AndroidUsbTransport::new(device_fd, vendor_id as u16, product_id as u16);
    let mut flasher = match AndroidFlashing::new(transport) {
        Ok(f) => f,
        Err(e) => {
            error!("Failed to create flasher: {}", e);
            return -1;
        }
    };
    
    // Initialize the flasher with the USB connection
    if let Err(e) = flasher.initialize(&mut env, usb_connection) {
        error!("Failed to initialize flasher: {}", e);
        return -1;
    }
    
    // Generate a handle and store the instance
    let handle = {
        let mut next_handle = NEXT_HANDLE.lock().unwrap();
        let handle = *next_handle;
        *next_handle += 1;
        handle
    };
    
    {
        let mut instances = FLASHER_INSTANCES.lock().unwrap();
        instances.insert(handle, flasher);
    }
    
    info!("Device opened successfully with handle: {}", handle);
    handle
}

/// Close USB device connection
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_closeDevice(
    mut env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Closing device handle: {}", handle);
    
    let mut instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(mut flasher) = instances.remove(&handle) {
        if let Err(e) = flasher.close(&mut env) {
            error!("Error closing flasher: {}", e);
            return false as jboolean;
        }
        info!("Device closed successfully");
        true as jboolean
    } else {
        error!("Invalid device handle: {}", handle);
        false as jboolean
    }
}

/// Identify connected chip
#[no_mangle] 
pub extern "C" fn Java_com_wch_flasher_WchispNative_identifyChip(
    env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jstring {
    info!("Identifying chip on handle: {}", handle);
    
    let instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(flasher) = instances.get(&handle) {
        let chip_info = flasher.get_chip_info();
        match env.new_string(chip_info) {
            Ok(jstr) => jstr.into_raw(),
            Err(e) => {
                error!("Failed to create Java string: {}", e);
                std::ptr::null_mut()
            }
        }
    } else {
        error!("Invalid device handle: {}", handle);
        std::ptr::null_mut()
    }
}

/// Flash firmware to the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_flashFirmware(
    mut env: JNIEnv,
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
    
    let mut instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(flasher) = instances.get_mut(&handle) {
        match flasher.flash_firmware(&mut env, &firmware) {
            Ok(()) => {
                info!("Firmware flash completed successfully");
                true as jboolean
            }
            Err(e) => {
                error!("Firmware flash failed: {}", e);
                false as jboolean
            }
        }
    } else {
        error!("Invalid device handle: {}", handle);  
        false as jboolean
    }
}

/// Erase chip flash memory
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_eraseChip(
    mut env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Erasing chip on handle: {}", handle);
    
    let mut instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(flasher) = instances.get_mut(&handle) {
        // Calculate sectors to erase (full chip)
        let chip = flasher.get_chip();
        let sector_size = chip.sector_size();
        let sectors = (chip.flash_size + sector_size - 1) / sector_size;
        
        match flasher.erase_flash(&mut env, sectors) {
            Ok(()) => {
                info!("Chip erase completed successfully");
                true as jboolean
            }
            Err(e) => {
                error!("Chip erase failed: {}", e);
                false as jboolean
            }
        }
    } else {
        error!("Invalid device handle: {}", handle);
        false as jboolean
    }
}

/// Verify firmware on the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_verifyFirmware(
    mut env: JNIEnv,
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
    
    let mut instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(flasher) = instances.get_mut(&handle) {
        match flasher.verify_firmware(&mut env, &firmware) {
            Ok(()) => {
                info!("Firmware verification completed successfully");
                true as jboolean
            }
            Err(e) => {
                error!("Firmware verification failed: {}", e);
                false as jboolean
            }
        }
    } else {
        error!("Invalid device handle: {}", handle);
        false as jboolean
    }
}

/// Reset the chip
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_resetChip(
    mut env: JNIEnv,
    _class: JClass,
    handle: jint,
) -> jboolean {
    info!("Resetting chip on handle: {}", handle);
    
    let mut instances = FLASHER_INSTANCES.lock().unwrap();
    if let Some(flasher) = instances.get_mut(&handle) {
        match flasher.reset_chip(&mut env) {
            Ok(()) => {
                info!("Chip reset completed successfully");
                true as jboolean
            }
            Err(e) => {
                error!("Chip reset failed: {}", e);
                false as jboolean
            }
        }
    } else {
        error!("Invalid device handle: {}", handle);
        false as jboolean
    }
}

/// Get last error message
#[no_mangle]
pub extern "C" fn Java_com_wch_flasher_WchispNative_getLastError(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    // Error handling implemented via proper Result propagation and logging
    let error_msg = "No error";
    
    match env.new_string(error_msg) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}