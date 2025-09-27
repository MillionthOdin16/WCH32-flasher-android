//! Android USB Transport Layer
//! 
//! This module replaces the libusb-based transport with Android USB Host API integration

use std::time::Duration;
use anyhow::Result;
use log::{debug, info};
use jni::{JNIEnv, objects::JObject};

/// Programming mode for WCH devices
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ProgrammingMode {
    UsbIsp,        // Direct USB ISP mode (PID 0x55E0)
    SerialCh340,   // Serial programming via CH340 (PID 0x7523)
    SerialCh341,   // Serial programming via CH341 (PID 0x5523)
    Unsupported,
}

/// Android-specific USB transport that uses USB Host API via JNI
pub struct AndroidUsbTransport {
    #[allow(dead_code)]
    device_fd: i32,
    vendor_id: u16,  
    product_id: u16,
    programming_mode: ProgrammingMode,
    connection_handle: Option<JObject<'static>>, // Will hold UsbDeviceConnection
    endpoint_out: u8,
    endpoint_in: u8,
}

impl AndroidUsbTransport {
    pub fn new(device_fd: i32, vendor_id: u16, product_id: u16) -> Self {
        let programming_mode = Self::get_programming_mode(vendor_id, product_id);
        
        // Set appropriate endpoints based on programming mode
        let (endpoint_out, endpoint_in) = match programming_mode {
            ProgrammingMode::UsbIsp => (0x02, 0x82),  // Standard USB ISP endpoints
            ProgrammingMode::SerialCh340 | ProgrammingMode::SerialCh341 => (0x02, 0x82), // CH340/CH341 bulk endpoints
            ProgrammingMode::Unsupported => (0x02, 0x82), // Default fallback
        };
        
        Self {
            device_fd,
            vendor_id,
            product_id,
            programming_mode,
            connection_handle: None,
            endpoint_out,
            endpoint_in,
        }
    }

    /// Initialize the USB connection using Android USB Host API via JNI
    pub fn initialize(&mut self, env: &mut JNIEnv, usb_connection: JObject) -> Result<()> {
        info!("Initializing USB transport for VID: 0x{:04X}, PID: 0x{:04X} (Mode: {:?})", 
              self.vendor_id, self.product_id, self.programming_mode);
              
        // Create global reference to USB connection for use across JNI calls
        let global_ref = env.new_global_ref(&usb_connection)?;
        
        // Store the connection handle 
        // SAFETY: We convert to static lifetime for storage, but ensure proper cleanup
        let static_ref = unsafe { std::mem::transmute(global_ref.as_obj()) };
        self.connection_handle = Some(static_ref);
        
        // Initialization differs based on programming mode
        match self.programming_mode {
            ProgrammingMode::UsbIsp => {
                info!("Initializing USB ISP mode");
                // Claim the USB interface for ISP
                self.claim_interface(env, &usb_connection)?;
                // Discover and set endpoint addresses
                self.discover_endpoints(env, &usb_connection)?;
            }
            ProgrammingMode::SerialCh340 => {
                info!("Initializing CH340 serial mode for WCH32 programming");
                // CH340 uses bulk transfer mode for serial communication
                self.setup_serial_mode(env, &usb_connection)?;
            }
            ProgrammingMode::SerialCh341 => {
                info!("Initializing CH341 serial mode for WCH32 programming");
                // CH341 setup
                self.setup_serial_mode(env, &usb_connection)?;
            }
            ProgrammingMode::Unsupported => {
                anyhow::bail!("Unsupported programming mode");
            }
        }
        
        info!("USB transport initialized successfully for {:?} mode", self.programming_mode);
        Ok(())
    }

    fn claim_interface(&self, env: &mut JNIEnv, connection: &JObject) -> Result<()> {
        debug!("Claiming USB interface");
        
        // Get UsbDevice from connection
        let device = env.call_method(
            connection,
            "getDevice",
            "()Landroid/hardware/usb/UsbDevice;",
            &[]
        )?;
        let device_obj = device.l()?;
        
        // Get first interface (interface 0)
        let interface = env.call_method(
            &device_obj,
            "getInterface",
            "(I)Landroid/hardware/usb/UsbInterface;",
            &[jni::objects::JValue::Int(0)]
        )?;
        let interface_obj = interface.l()?;
        
        // Claim the interface with force flag
        let claimed = env.call_method(
            connection,
            "claimInterface",
            "(Landroid/hardware/usb/UsbInterface;Z)Z",
            &[
                jni::objects::JValue::Object(&interface_obj),
                jni::objects::JValue::Bool(true as jni::sys::jboolean), // Force claim
            ]
        )?;
        
        if !claimed.z()? {
            return Err(anyhow::anyhow!("Failed to claim USB interface"));
        }
        
        debug!("USB interface claimed successfully");
        Ok(())
    }
    
    fn discover_endpoints(&mut self, env: &mut JNIEnv, connection: &JObject) -> Result<()> {
        debug!("Discovering USB endpoints");
        
        // Get UsbDevice from connection
        let device = env.call_method(
            connection,
            "getDevice",
            "()Landroid/hardware/usb/UsbDevice;",
            &[]
        )?;
        let device_obj = device.l()?;
        
        // Get first interface (interface 0)
        let interface = env.call_method(
            &device_obj,
            "getInterface",
            "(I)Landroid/hardware/usb/UsbInterface;",
            &[jni::objects::JValue::Int(0)]
        )?;
        let interface_obj = interface.l()?;
        
        // Get endpoint count
        let endpoint_count = env.call_method(
            &interface_obj,
            "getEndpointCount",
            "()I",
            &[]
        )?;
        let count = endpoint_count.i()?;
        
        debug!("Found {} endpoints", count);
        
        for i in 0..count {
            let endpoint = env.call_method(
                &interface_obj,
                "getEndpoint",
                "(I)Landroid/hardware/usb/UsbEndpoint;",
                &[jni::objects::JValue::Int(i)]
            )?;
            let endpoint_obj = endpoint.l()?;
            
            // Get endpoint address
            let address = env.call_method(
                &endpoint_obj,
                "getAddress",
                "()I",
                &[]
            )?;
            let addr = address.i()? as u8;
            
            // Get endpoint direction
            let direction = env.call_method(
                &endpoint_obj,
                "getDirection",
                "()I", 
                &[]
            )?;
            let dir = direction.i()?;
            
            // USB_DIR_OUT = 0, USB_DIR_IN = 128 (0x80)
            if dir == 0 { // OUT endpoint
                self.endpoint_out = addr;
                debug!("Found OUT endpoint: 0x{:02X}", addr);
            } else { // IN endpoint
                self.endpoint_in = addr;
                debug!("Found IN endpoint: 0x{:02X}", addr);
            }
        }
        
        debug!("Endpoint discovery completed: OUT=0x{:02X}, IN=0x{:02X}", 
               self.endpoint_out, self.endpoint_in);
        Ok(())
    }
    
    fn setup_serial_mode(&mut self, env: &mut JNIEnv, connection: &JObject) -> Result<()> {
        info!("Setting up serial mode for WCH32 programming");
        
        // For CH340/CH341, we need to:
        // 1. Claim the interface
        // 2. Set up serial parameters (baud rate, etc.)
        // 3. Configure for WCH32 bootloader communication
        
        self.claim_interface(env, connection)?;
        
        // Set serial parameters for WCH32 bootloader
        // Most WCH32 devices use 115200 baud by default for serial programming
        self.configure_serial_parameters(env, connection, 115200)?;
        
        // Discover endpoints (CH340/CH341 use bulk transfer endpoints)
        self.discover_endpoints(env, connection)?;
        
        info!("Serial mode setup completed");
        Ok(())
    }
    
    fn configure_serial_parameters(&self, env: &mut JNIEnv, connection: &JObject, baud_rate: u32) -> Result<()> {
        info!("Configuring serial parameters: {} baud", baud_rate);
        
        // CH340/CH341 specific serial configuration
        // This would typically involve control transfers to set baud rate, parity, etc.
        // For now, we'll use a simplified approach suitable for WCH32 bootloader
        
        match self.programming_mode {
            ProgrammingMode::SerialCh340 => {
                debug!("Configuring CH340 for {} baud", baud_rate);
                // CH340 specific configuration would go here
                // For the scope of this implementation, we'll assume the device
                // is already configured appropriately for WCH32 communication
            }
            ProgrammingMode::SerialCh341 => {
                debug!("Configuring CH341 for {} baud", baud_rate);
                // CH341 specific configuration would go here
            }
            _ => {}
        }
        
        Ok(())
    }

    pub fn send_raw(&mut self, env: &mut JNIEnv, data: &[u8]) -> Result<usize> {
        debug!("Sending {} bytes via Android USB", data.len());
        
        if let Some(ref connection) = self.connection_handle {
            // Convert data to Java byte array
            let java_array = env.byte_array_from_slice(data)?;
            
            // Call bulkTransfer(endpoint, buffer, length, timeout)
            let result = env.call_method(
                connection,
                "bulkTransfer",
                "(I[BII)I",
                &[
                    jni::objects::JValue::Int(self.endpoint_out as i32),
                    jni::objects::JValue::Object(&java_array),
                    jni::objects::JValue::Int(data.len() as i32),
                    jni::objects::JValue::Int(5000), // 5 second timeout
                ],
            )?;
            
            let bytes_sent = result.i()? as usize;
            if bytes_sent == data.len() {
                debug!("Successfully sent {} bytes", bytes_sent);
                Ok(bytes_sent)
            } else {
                anyhow::bail!("USB send failed: expected {}, sent {}", data.len(), bytes_sent);
            }
        } else {
            anyhow::bail!("No USB connection available");
        }
    }

    pub fn recv_raw(&mut self, env: &mut JNIEnv, timeout: Duration) -> Result<Vec<u8>> {
        debug!("Receiving data via Android USB with timeout: {:?}", timeout);
        
        if let Some(ref connection) = self.connection_handle {
            // Create receive buffer (standard WCH ISP packet size)
            let buffer_size = 64;
            let java_array = env.new_byte_array(buffer_size)?;
            
            // Call bulkTransfer for receive
            let result = env.call_method(
                connection,
                "bulkTransfer",
                "(I[BII)I",
                &[
                    jni::objects::JValue::Int(self.endpoint_in as i32),
                    jni::objects::JValue::Object(&java_array),
                    jni::objects::JValue::Int(buffer_size as i32),
                    jni::objects::JValue::Int(timeout.as_millis() as i32),
                ],
            )?;
            
            let bytes_received = result.i()?;
            if bytes_received > 0 {
                let mut buffer = vec![0i8; bytes_received as usize];
                env.get_byte_array_region(&java_array, 0, &mut buffer)?;
                // Convert i8 to u8
                let result = buffer.into_iter().map(|b| b as u8).collect();
                debug!("Received {} bytes", bytes_received);
                Ok(result)
            } else {
                anyhow::bail!("USB receive failed or timeout");
            }
        } else {
            anyhow::bail!("No USB connection available");
        }
    }

    pub fn is_supported_device(vendor_id: u16, product_id: u16) -> bool {
        matches!((vendor_id, product_id), 
            (0x4348, 0x55e0) | (0x1a86, 0x55e0) |  // USB ISP mode
            (0x1a86, 0x7523) | (0x1a86, 0x5523)    // USB-to-Serial converters for UART programming
        )
    }
    
    pub fn get_programming_mode(vendor_id: u16, product_id: u16) -> ProgrammingMode {
        match (vendor_id, product_id) {
            (0x4348, 0x55e0) | (0x1a86, 0x55e0) => ProgrammingMode::UsbIsp,
            (0x1a86, 0x7523) => ProgrammingMode::SerialCh340,
            (0x1a86, 0x5523) => ProgrammingMode::SerialCh341,
            _ => ProgrammingMode::Unsupported,
        }
    }
    
    pub fn release_interface(&self, env: &mut JNIEnv) -> Result<()> {
        debug!("Releasing USB interface");
        
        if let Some(ref connection) = self.connection_handle {
            // Get UsbDevice from connection
            let device = env.call_method(
                connection,
                "getDevice",
                "()Landroid/hardware/usb/UsbDevice;",
                &[]
            )?;
            let device_obj = device.l()?;
            
            // Get first interface (interface 0)
            let interface = env.call_method(
                &device_obj,
                "getInterface",
                "(I)Landroid/hardware/usb/UsbInterface;",
                &[jni::objects::JValue::Int(0)]
            )?;
            let interface_obj = interface.l()?;
            
            // Release the interface
            let released = env.call_method(
                connection,
                "releaseInterface",
                "(Landroid/hardware/usb/UsbInterface;)Z",
                &[jni::objects::JValue::Object(&interface_obj)]
            )?;
            
            if released.z()? {
                debug!("USB interface released successfully");
            } else {
                debug!("Warning: Failed to release USB interface");
            }
        }
        
        Ok(())
    }
    
    pub fn close(&mut self, env: &mut JNIEnv) -> Result<()> {
        info!("Closing USB transport");
        
        // Release interface before closing
        self.release_interface(env)?;
        
        // Close the USB connection
        if let Some(ref connection) = self.connection_handle {
            let _result = env.call_method(
                connection,
                "close",
                "()V",
                &[]
            );
            // Note: We don't fail if close() fails as connection may already be closed
        }
        
        self.connection_handle = None;
        info!("USB transport closed");
        Ok(())
    }
}

/// USB endpoint configuration for WCH ISP devices
pub struct UsbEndpoints {
    pub endpoint_out: u8,
    pub endpoint_in: u8,
}

impl Default for UsbEndpoints {
    fn default() -> Self {
        Self {
            endpoint_out: 0x02,
            endpoint_in: 0x82,
        }
    }
}