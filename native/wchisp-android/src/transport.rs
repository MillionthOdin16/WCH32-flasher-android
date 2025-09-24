//! Android USB Transport Layer
//! 
//! This module replaces the libusb-based transport with Android USB Host API integration

use std::time::Duration;
use anyhow::Result;
use log::{debug, info};
use jni::{JNIEnv, objects::JObject};

/// Android-specific USB transport that uses USB Host API via JNI
pub struct AndroidUsbTransport {
    #[allow(dead_code)]
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
    pub fn initialize(&mut self, env: &mut JNIEnv, usb_connection: JObject) -> Result<()> {
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