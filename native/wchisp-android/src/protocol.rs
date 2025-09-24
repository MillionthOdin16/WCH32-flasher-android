//! WCH ISP Protocol implementation
//! 
//! This module implements the WCH ISP communication protocol

use anyhow::Result;
use scroll::{Pwrite, LE};
use log::{debug, error};
use crate::transport::AndroidUsbTransport;
use jni::JNIEnv;
use std::time::Duration;

/// ISP Command types
#[repr(u8)]
#[derive(Debug, Clone, Copy)]
pub enum CommandType {
    Identify = 0xa1,
    IspEnd = 0xa2,
    IspKey = 0xa3,
    Erase = 0xa4,
    Program = 0xa5,
    Verify = 0xa6,
    ReadConfig = 0xa7,
    WriteConfig = 0xa8,
    DataErase = 0xa9,
    DataProgram = 0xaa,
    DataRead = 0xab,
}

/// ISP Command structure
#[derive(Debug)]
pub struct Command {
    pub cmd_type: CommandType,
    pub payload: Vec<u8>,
}

/// ISP Response structure
#[derive(Debug)]
pub struct Response {
    pub cmd_type: CommandType,
    pub status: u8,
    pub payload: Vec<u8>,
}

impl Command {
    pub fn identify(chip_id: u8, device_type: u8) -> Self {
        let mut payload = vec![0; 6];
        payload[0] = chip_id;
        payload[1] = device_type;
        
        Self {
            cmd_type: CommandType::Identify,
            payload,
        }
    }

    pub fn isp_key(key_seed: Vec<u8>) -> Self {
        Self {
            cmd_type: CommandType::IspKey,
            payload: key_seed,
        }
    }

    pub fn erase(sectors: u32) -> Self {
        let mut payload = vec![0; 4];
        payload.pwrite_with(sectors, 0, LE).unwrap();
        
        Self {
            cmd_type: CommandType::Erase,
            payload,
        }
    }

    pub fn program(address: u32, padding: u8, data: Vec<u8>) -> Self {
        let mut payload = Vec::with_capacity(5 + data.len());
        payload.extend_from_slice(&address.to_le_bytes());
        payload.push(padding);
        payload.extend_from_slice(&data);
        
        Self {
            cmd_type: CommandType::Program,
            payload,
        }
    }

    pub fn verify(address: u32, padding: u8, data: Vec<u8>) -> Self {
        let mut payload = Vec::with_capacity(5 + data.len());
        payload.extend_from_slice(&address.to_le_bytes());
        payload.push(padding);
        payload.extend_from_slice(&data);
        
        Self {
            cmd_type: CommandType::Verify,
            payload,
        }
    }

    pub fn read_config(mask: u32) -> Self {
        let mut payload = vec![0; 4];
        payload.pwrite_with(mask, 0, LE).unwrap();
        
        Self {
            cmd_type: CommandType::ReadConfig,
            payload,
        }
    }

    pub fn write_config(mask: u32, data: Vec<u8>) -> Self {
        let mut payload = Vec::with_capacity(4 + data.len());
        payload.extend_from_slice(&mask.to_le_bytes());
        payload.extend_from_slice(&data);
        
        Self {
            cmd_type: CommandType::WriteConfig,
            payload,
        }
    }

    pub fn isp_end(reset: u8) -> Self {
        Self {
            cmd_type: CommandType::IspEnd,
            payload: vec![reset],
        }
    }

    pub fn data_erase(sectors: u16) -> Self {
        let mut payload = vec![0; 2];
        payload.pwrite_with(sectors, 0, LE).unwrap();
        
        Self {
            cmd_type: CommandType::DataErase,
            payload,
        }
    }

    pub fn data_program(address: u32, padding: u8, data: Vec<u8>) -> Self {
        let mut payload = Vec::with_capacity(5 + data.len());
        payload.extend_from_slice(&address.to_le_bytes());
        payload.push(padding);
        payload.extend_from_slice(&data);
        
        Self {
            cmd_type: CommandType::DataProgram,
            payload,
        }
    }

    pub fn data_read(address: u32, length: u16) -> Self {
        let mut payload = vec![0; 6];
        payload.pwrite_with(address, 0, LE).unwrap();
        payload.pwrite_with(length, 4, LE).unwrap();
        
        Self {
            cmd_type: CommandType::DataRead,
            payload,
        }
    }

    /// Convert command to raw bytes for transmission
    pub fn into_raw(self) -> Result<Vec<u8>> {
        let mut raw = Vec::with_capacity(3 + self.payload.len());
        raw.push(self.cmd_type as u8);
        raw.push(self.payload.len() as u8);
        raw.push(0x00); // Reserved byte
        raw.extend_from_slice(&self.payload);
        
        debug!("Command packet: {} bytes", raw.len());
        Ok(raw)
    }
}

impl Response {
    /// Parse response from raw bytes
    pub fn from_raw(raw: &[u8]) -> Result<Self> {
        if raw.len() < 4 {
            error!("Response too short: {} bytes", raw.len());
            return Err(anyhow::anyhow!("Response too short"));
        }

        let cmd_type = match raw[0] {
            0xa1 => CommandType::Identify,
            0xa2 => CommandType::IspEnd,
            0xa3 => CommandType::IspKey,
            0xa4 => CommandType::Erase,
            0xa5 => CommandType::Program,
            0xa6 => CommandType::Verify,
            0xa7 => CommandType::ReadConfig,
            0xa8 => CommandType::WriteConfig,
            0xa9 => CommandType::DataErase,
            0xaa => CommandType::DataProgram,
            0xab => CommandType::DataRead,
            _ => {
                error!("Unknown command type: 0x{:02x}", raw[0]);
                return Err(anyhow::anyhow!("Unknown command type: 0x{:02x}", raw[0]));
            }
        };

        let payload_len = raw[1] as usize;
        let status = raw[2];
        
        if raw.len() < 4 + payload_len {
            error!("Incomplete response payload: expected {}, got {}", 
                   4 + payload_len, raw.len());
            return Err(anyhow::anyhow!("Incomplete response payload"));
        }

        let payload = raw[4..4 + payload_len].to_vec();

        debug!("Response parsed: type=0x{:02x}, status=0x{:02x}, payload_len={}", 
               raw[0], status, payload_len);

        Ok(Self {
            cmd_type,
            status,
            payload,
        })
    }

    pub fn is_ok(&self) -> bool {
        self.status == 0x00
    }

    pub fn payload(&self) -> &[u8] {
        &self.payload
    }
}

/// Protocol handler for WCH ISP communication
pub struct ProtocolHandler;

impl ProtocolHandler {
    pub fn new() -> Self {
        Self
    }
    
    /// Send a command and receive response through transport layer
    pub fn transfer(
        &self,
        transport: &mut AndroidUsbTransport,
        env: &JNIEnv,
        cmd: Command
    ) -> Result<Response> {
        self.transfer_with_timeout(transport, env, cmd, Duration::from_millis(1000))
    }
    
    /// Send a command with custom timeout
    pub fn transfer_with_timeout(
        &self,
        transport: &mut AndroidUsbTransport,
        env: &JNIEnv,
        cmd: Command,
        timeout: Duration
    ) -> Result<Response> {
        let cmd_type = cmd.cmd_type;
        let req = cmd.into_raw()?;
        
        debug!("Sending command: type=0x{:02x}, len={}", cmd_type as u8, req.len());
        
        // Send command
        let bytes_sent = transport.send_raw(env, &req)?;
        if bytes_sent != req.len() {
            error!("Incomplete send: sent {} of {} bytes", bytes_sent, req.len());
            return Err(anyhow::anyhow!("Incomplete command send"));
        }
        
        // Small delay to ensure command is processed
        std::thread::sleep(Duration::from_micros(100));
        
        // Receive response
        let resp_data = transport.recv_raw(env, timeout)?;
        if resp_data.is_empty() {
            error!("No response received");
            return Err(anyhow::anyhow!("No response received"));
        }
        
        let response = Response::from_raw(&resp_data)?;
        
        // Verify response matches command
        if std::mem::discriminant(&response.cmd_type) != std::mem::discriminant(&cmd_type) {
            error!("Response command type mismatch: expected {:?}, got {:?}", 
                   cmd_type, response.cmd_type);
            return Err(anyhow::anyhow!("Response command type mismatch"));
        }
        
        debug!("Command completed successfully");
        Ok(response)
    }
    
    /// Perform chip identification
    pub fn identify_chip(
        &self,
        transport: &mut AndroidUsbTransport,
        env: &JNIEnv
    ) -> Result<(u8, u8)> {
        debug!("Identifying chip");
        
        let identify_cmd = Command::identify(0, 0);
        let response = self.transfer(transport, env, identify_cmd)?;
        
        if !response.is_ok() {
            error!("Chip identification failed with status: 0x{:02x}", response.status);
            return Err(anyhow::anyhow!("Chip identification failed"));
        }
        
        if response.payload().len() < 2 {
            error!("Invalid identification response length: {}", response.payload().len());
            return Err(anyhow::anyhow!("Invalid identification response"));
        }
        
        let chip_id = response.payload()[0];
        let device_type = response.payload()[1];
        
        debug!("Chip identified: ID=0x{:02x}, Type=0x{:02x}", chip_id, device_type);
        Ok((chip_id, device_type))
    }
}

/// Constants for configuration register masks
pub const CFG_MASK_ALL: u32 = 0x1F;
pub const CFG_MASK_RDPR_USER_DATA_WPR: u32 = 0x07;