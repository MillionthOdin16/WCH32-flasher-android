//! Android Flashing implementation
//! 
//! This module provides the main flashing functionality for Android

use anyhow::Result;
use log::{info, debug, warn};
use jni::JNIEnv;
use std::time::Duration;

use crate::device::{Chip, ChipDB};
use crate::transport::AndroidUsbTransport;
use crate::protocol::{ProtocolHandler, Command, CFG_MASK_ALL, CFG_MASK_RDPR_USER_DATA_WPR};

/// Android-specific flashing implementation
pub struct AndroidFlashing {
    transport: AndroidUsbTransport,
    protocol: ProtocolHandler,
    chip: Chip,
    chip_uid: Vec<u8>,
    bootloader_version: [u8; 4],
    code_flash_protected: bool,
}

impl AndroidFlashing {
    pub fn new(transport: AndroidUsbTransport) -> Result<Self> {
        Ok(Self {
            transport,
            protocol: ProtocolHandler::new(),
            chip: Chip::placeholder(), // Will be updated after identification
            chip_uid: vec![],
            bootloader_version: [0; 4],
            code_flash_protected: false,
        })
    }

    pub fn initialize(&mut self, env: &JNIEnv, usb_connection: jni::objects::JObject) -> Result<()> {
        info!("Initializing flashing interface");
        
        // Initialize the USB transport
        self.transport.initialize(env, usb_connection)?;
        
        // Identify the connected chip
        self.identify_chip(env)?;
        
        // Read chip configuration
        self.read_chip_config(env)?;
        
        info!("Flashing interface initialized successfully");
        Ok(())
    }

    fn identify_chip(&mut self, env: &JNIEnv) -> Result<()> {
        debug!("Identifying chip...");
        
        let (chip_id, device_type) = self.protocol.identify_chip(&mut self.transport, env)?;
        
        // Load chip database and find the chip
        let chip_db = ChipDB::load()?;
        self.chip = chip_db.find_chip(chip_id, device_type)?;
        
        info!("Identified chip: {}", self.chip);
        Ok(())
    }

    fn read_chip_config(&mut self, env: &JNIEnv) -> Result<()> {
        debug!("Reading chip configuration");
        
        let read_conf = Command::read_config(CFG_MASK_ALL);
        let resp = self.protocol.transfer(&mut self.transport, env, read_conf)?;
        
        if !resp.is_ok() {
            warn!("Failed to read chip configuration: status=0x{:02x}", resp.status);
            return Ok(()); // Non-fatal error
        }
        
        let config_data = resp.payload();
        if config_data.len() >= 18 {
            // Extract bootloader version
            self.bootloader_version.copy_from_slice(&config_data[14..18]);
            
            // Check if code flash is protected
            if self.chip.support_code_flash_protect() && config_data.len() >= 3 {
                self.code_flash_protected = config_data[2] != 0xa5;
            }
            
            // Extract chip UID (remaining bytes after config)
            if config_data.len() > 18 {
                self.chip_uid = config_data[18..].to_vec();
            }
            
            debug!("Config read: BTVER={:02x}.{:02x}.{:02x}.{:02x}, Protected={}", 
                   self.bootloader_version[0], self.bootloader_version[1],
                   self.bootloader_version[2], self.bootloader_version[3],
                   self.code_flash_protected);
        }
        
        Ok(())
    }

    pub fn get_chip_info(&self) -> String {
        let mut info = self.chip.get_chip_info();
        
        if !self.chip_uid.is_empty() {
            let uid_str = self.chip_uid
                .iter()
                .map(|x| format!("{:02X}", x))
                .collect::<Vec<_>>()
                .join("-");
            info.push_str(&format!("\nChip UID: {}", uid_str));
        }
        
        info.push_str(&format!("\nBTVER: {:02x}.{:02x}.{:02x}.{:02x}",
                              self.bootloader_version[0], self.bootloader_version[1],
                              self.bootloader_version[2], self.bootloader_version[3]));
        
        if self.chip.support_code_flash_protect() {
            info.push_str(&format!("\nCode Flash Protected: {}", self.code_flash_protected));
        }
        
        info
    }

    pub fn get_chip(&self) -> &Chip {
        &self.chip
    }

    pub fn flash_firmware(&mut self, env: &JNIEnv, firmware_data: &[u8]) -> Result<()> {
        info!("Starting firmware flash, size: {} bytes", firmware_data.len());
        
        // Unprotect flash if needed
        if self.code_flash_protected {
            self.unprotect_flash(env)?;
        }
        
        // Calculate number of sectors to erase
        let sector_size = self.chip.sector_size();
        let sectors_needed = ((firmware_data.len() as u32 + sector_size - 1) / sector_size).max(self.chip.min_erase_sector_number());
        
        // Erase flash
        self.erase_flash(env, sectors_needed)?;
        
        // Set up ISP key for encryption
        self.setup_isp_key(env)?;
        
        // Program firmware
        self.program_flash(env, firmware_data)?;
        
        info!("Firmware flash completed successfully");
        Ok(())
    }

    fn unprotect_flash(&mut self, env: &JNIEnv) -> Result<()> {
        info!("Unprotecting code flash");
        
        let read_conf = Command::read_config(CFG_MASK_RDPR_USER_DATA_WPR);
        let resp = self.protocol.transfer(&mut self.transport, env, read_conf)?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("Failed to read config for unprotect"));
        }
        
        let mut config = resp.payload()[2..14].to_vec(); // 4 x u32
        config[0] = 0xa5; // Unprotect code flash
        config[1] = 0x5a;
        config[8..12].copy_from_slice(&[0xff; 4]); // Clear WPR register
        
        let write_conf = Command::write_config(CFG_MASK_RDPR_USER_DATA_WPR, config);
        let resp = self.protocol.transfer(&mut self.transport, env, write_conf)?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("Failed to unprotect flash"));
        }
        
        self.code_flash_protected = false;
        info!("Code flash unprotected");
        Ok(())
    }

    pub fn erase_flash(&mut self, env: &JNIEnv, sectors: u32) -> Result<()> {
        info!("Erasing {} flash sectors", sectors);
        
        let erase_cmd = Command::erase(sectors);
        let resp = self.protocol.transfer_with_timeout(
            &mut self.transport, 
            env, 
            erase_cmd, 
            Duration::from_millis(5000)
        )?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("Flash erase failed: status=0x{:02x}", resp.status));
        }
        
        info!("Flash erase completed");
        Ok(())
    }

    fn setup_isp_key(&mut self, env: &JNIEnv) -> Result<()> {
        debug!("Setting up ISP key");
        
        // Use all-zero key seed (standard approach)
        let key_seed = vec![0u8; 0x1e];
        let isp_key_cmd = Command::isp_key(key_seed);
        let resp = self.protocol.transfer(&mut self.transport, env, isp_key_cmd)?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("ISP key setup failed"));
        }
        
        // Verify key checksum
        let expected_checksum = self.generate_key_checksum();
        if resp.payload().len() > 0 && resp.payload()[0] != expected_checksum {
            warn!("ISP key checksum mismatch: expected 0x{:02x}, got 0x{:02x}", 
                  expected_checksum, resp.payload()[0]);
        }
        
        debug!("ISP key setup completed");
        Ok(())
    }

    fn program_flash(&mut self, env: &JNIEnv, data: &[u8]) -> Result<()> {
        info!("Programming flash...");
        
        const CHUNK_SIZE: usize = 56; // Standard WCH ISP chunk size
        let mut address = 0u32;
        let total_chunks = (data.len() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        
        for (chunk_idx, chunk) in data.chunks(CHUNK_SIZE).enumerate() {
            // Generate XOR encrypted data
            let xor_key = self.generate_xor_key();
            let encrypted_data: Vec<u8> = chunk
                .iter()
                .enumerate()
                .map(|(i, &byte)| byte ^ xor_key[i % 8])
                .collect();
            
            let padding = rand::random::<u8>();
            let program_cmd = Command::program(address, padding, encrypted_data);
            let resp = self.protocol.transfer_with_timeout(
                &mut self.transport,
                env,
                program_cmd,
                Duration::from_millis(300)
            )?;
            
            if !resp.is_ok() {
                return Err(anyhow::anyhow!("Programming failed at address 0x{:08x}", address));
            }
            
            address += chunk.len() as u32;
            
            // Log progress every 10 chunks
            if chunk_idx % 10 == 0 {
                debug!("Programming progress: {}/{} chunks", chunk_idx + 1, total_chunks);
            }
        }
        
        // Send final empty chunk to complete programming
        let program_cmd = Command::program(address, 0, vec![]);
        let resp = self.protocol.transfer(&mut self.transport, env, program_cmd)?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("Failed to complete programming sequence"));
        }
        
        info!("Flash programming completed: {} bytes written", data.len());
        Ok(())
    }

    pub fn verify_firmware(&mut self, env: &JNIEnv, expected_data: &[u8]) -> Result<()> {
        info!("Verifying firmware...");
        
        const CHUNK_SIZE: usize = 56;
        let mut address = 0u32;
        
        for chunk in expected_data.chunks(CHUNK_SIZE) {
            // Generate XOR encrypted data for verification
            let xor_key = self.generate_xor_key();
            let encrypted_data: Vec<u8> = chunk
                .iter()
                .enumerate()
                .map(|(i, &byte)| byte ^ xor_key[i % 8])
                .collect();
            
            let padding = rand::random::<u8>();
            let verify_cmd = Command::verify(address, padding, encrypted_data);
            let resp = self.protocol.transfer(&mut self.transport, env, verify_cmd)?;
            
            if !resp.is_ok() {
                return Err(anyhow::anyhow!("Verification failed at address 0x{:08x}", address));
            }
            
            if resp.payload().len() > 0 && resp.payload()[0] != 0x00 {
                return Err(anyhow::anyhow!("Verification mismatch at address 0x{:08x}", address));
            }
            
            address += chunk.len() as u32;
        }
        
        info!("Firmware verification completed successfully");
        Ok(())
    }

    pub fn reset_chip(&mut self, env: &JNIEnv) -> Result<()> {
        info!("Resetting chip...");
        
        let isp_end = Command::isp_end(1);
        let resp = self.protocol.transfer(&mut self.transport, env, isp_end)?;
        
        if !resp.is_ok() {
            warn!("Reset command returned status: 0x{:02x}", resp.status);
        }
        
        info!("Chip reset completed");
        Ok(())
    }

    pub fn erase_eeprom(&mut self, env: &JNIEnv) -> Result<()> {
        if self.chip.eeprom_size == 0 {
            return Err(anyhow::anyhow!("Chip does not support EEPROM"));
        }
        
        info!("Erasing EEPROM");
        
        let sectors = ((self.chip.eeprom_size / 1024).max(1)) as u16;
        let erase_cmd = Command::data_erase(sectors);
        let resp = self.protocol.transfer_with_timeout(
            &mut self.transport,
            env,
            erase_cmd,
            Duration::from_millis(1000)
        )?;
        
        if !resp.is_ok() {
            return Err(anyhow::anyhow!("EEPROM erase failed"));
        }
        
        info!("EEPROM erase completed");
        Ok(())
    }

    fn generate_xor_key(&self) -> [u8; 8] {
        let checksum = self.chip_uid
            .iter()
            .fold(0u8, |acc, &x| acc.overflowing_add(x).0);
        let mut key = [checksum; 8];
        if let Some(last) = key.last_mut() {
            *last = last.overflowing_add(self.chip.chip_id).0;
        }
        key
    }

    fn generate_key_checksum(&self) -> u8 {
        self.generate_xor_key()
            .iter()
            .fold(0u8, |acc, &x| acc.overflowing_add(x).0)
    }

    pub fn close(&mut self, env: &JNIEnv) -> Result<()> {
        info!("Closing flashing interface");
        self.transport.close(env)?;
        info!("Flashing interface closed");
        Ok(())
    }
}