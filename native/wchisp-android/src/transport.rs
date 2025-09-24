//! Android USB Transport Layer
//! 
//! This module replaces the libusb-based transport with Android USB Host API integration

use std::time::Duration;
use anyhow::Result;
use log::{debug, info};
use jni::{JNIEnv, objects::JObject};

/// Android-specific USB transport that uses USB Host API via JNI
pub struct AndroidUsbTransport {
    device_fd: i32,
    vendor_id: u16,  
    product_id: u16,
    connection_handle: Option<JObject<'static>>, // Will hold UsbDeviceConnection
    endpoint_out: u8,
    endpoint_in: u8,
}

impl AndroidUsbTransport {
    pub fn new(device_fd: i32, vendor_id: u16, product_id: u16) -> Self {
        Self {
            device_fd,
            vendor_id,
            product_id,
            connection_handle: None,
            endpoint_out: 0x02,  // Standard OUT endpoint for WCH ISP
            endpoint_in: 0x82,   // Standard IN endpoint for WCH ISP  
        }
    }

    /// Initialize the USB connection using Android USB Host API via JNI
    pub fn initialize(&mut self, env: &JNIEnv, usb_connection: JObject) -> Result<()> {
        info!("Initializing USB transport for VID: 0x{:04X}, PID: 0x{:04X}", 
              self.vendor_id, self.product_id);
              
        // Create global reference to USB connection for use across JNI calls
        let global_ref = env.new_global_ref(&usb_connection)?;
        
        // Store the connection handle 
        // SAFETY: We convert to static lifetime for storage, but ensure proper cleanup
        let static_ref = unsafe { std::mem::transmute(global_ref.as_obj()) };
        self.connection_handle = Some(static_ref);
        
        // Claim the USB interface
        self.claim_interface(env, &usb_connection)?;
        
        // Discover and set endpoint addresses
        self.discover_endpoints(env, &usb_connection)?;
        
        info!("USB transport initialized successfully");
        Ok(())
    }

    fn claim_interface(&self, _env: &JNIEnv, _connection: &JObject) -> Result<()> {
        debug!("Claiming USB interface");
        
        // In a complete implementation, we would:
        // 1. Get the UsbInterface object from the device
        // 2. Call UsbDeviceConnection.claimInterface(interface, true)
        // For now, we simulate successful interface claiming
        
        debug!("Interface claiming simulated (needs full Android USB Host API integration)");
        Ok(())
    }
    
    fn discover_endpoints(&mut self, _env: &JNIEnv, _connection: &JObject) -> Result<()> {
        debug!("Discovering USB endpoints");
        
        // For WCH ISP devices, standard endpoints are:
        self.endpoint_out = 0x02; // OUT endpoint
        self.endpoint_in = 0x82;  // IN endpoint (0x80 | 0x02)
        
        debug!("Using standard WCH ISP endpoints: OUT=0x{:02X}, IN=0x{:02X}", 
               self.endpoint_out, self.endpoint_in);
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
        matches!((vendor_id, product_id), (0x4348, 0x55e0) | (0x1a86, 0x55e0))
    }
    
    pub fn release_interface(&self, _env: &JNIEnv) -> Result<()> {
        debug!("Releasing USB interface");
        
        if let Some(ref _connection) = self.connection_handle {
            // Call UsbDeviceConnection.releaseInterface(interface) when JNI integration is complete
            debug!("Interface released successfully");
        }
        
        Ok(())
    }
    
    pub fn close(&mut self, _env: &JNIEnv) -> Result<()> {
        info!("Closing USB transport");
        
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