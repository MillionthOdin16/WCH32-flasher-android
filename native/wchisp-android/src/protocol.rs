//! WCH ISP Protocol implementation
//! 
//! This module implements the WCH ISP communication protocol

use anyhow::Result;
use scroll::{Pwrite, LE};

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
        
        Ok(raw)
    }
}

impl Response {
    /// Parse response from raw bytes
    pub fn from_raw(raw: &[u8]) -> Result<Self> {
        if raw.len() < 4 {
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
            _ => return Err(anyhow::anyhow!("Unknown command type: 0x{:02x}", raw[0])),
        };

        let payload_len = raw[1] as usize;
        let status = raw[2];
        
        if raw.len() < 4 + payload_len {
            return Err(anyhow::anyhow!("Incomplete response payload"));
        }

        let payload = raw[4..4 + payload_len].to_vec();

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