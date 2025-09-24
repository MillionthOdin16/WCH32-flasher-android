//! WCH Device definitions and chip database
//! 
//! This module contains chip definitions extracted from the wchisp device database

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chip {
    pub name: String,
    pub chip_id: u8,
    pub device_type: u8,
    pub flash_size: u32,
    pub eeprom_size: u32,
    pub config_registers: Vec<ConfigRegister>,
    pub family: ChipFamily,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ChipFamily {
    CH32V,
    CH32F,
    CH549,
    CH552,
    CH582,
    CH579,
    CH559,
    CH592,
    CH573,
    Unknown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConfigRegister {
    pub name: String,
    pub offset: usize,
    pub reset: Option<u32>,
    pub enable_debug: Option<u32>,
    pub fields: Vec<ConfigField>,
    pub explaination: Vec<(String, String)>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConfigField {
    pub name: String,
    pub bit_range: [u8; 2],
    pub explaination: Vec<(String, String)>,
}

impl Chip {
    /// Create CH32V307 chip definition
    pub fn ch32v307() -> Self {
        Self {
            name: "CH32V307".to_string(),
            chip_id: 0x70,
            device_type: 0x17,
            flash_size: 256 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32V,
        }
    }

    /// Create CH32V103 chip definition  
    pub fn ch32v103() -> Self {
        Self {
            name: "CH32V103".to_string(),
            chip_id: 0x30,
            device_type: 0x30,
            flash_size: 64 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32V,
        }
    }

    /// Create CH32F103 chip definition
    pub fn ch32f103() -> Self {
        Self {
            name: "CH32F103".to_string(),
            chip_id: 0x10,
            device_type: 0x30,
            flash_size: 128 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32F,
        }
    }

    /// Create CH582 chip definition
    pub fn ch582() -> Self {
        Self {
            name: "CH582".to_string(),
            chip_id: 0x82,
            device_type: 0x82,
            flash_size: 448 * 1024,
            eeprom_size: 32 * 1024,
            config_registers: vec![],
            family: ChipFamily::CH582,
        }
    }

    pub fn support_code_flash_protect(&self) -> bool {
        matches!(self.family, ChipFamily::CH32V | ChipFamily::CH32F)
    }

    pub fn min_erase_sector_number(&self) -> u32 {
        1
    }

    pub fn sector_size(&self) -> u32 {
        1024
    }

    pub fn get_chip_info(&self) -> String {
        if self.eeprom_size > 0 {
            format!("{} (Code Flash: {}KiB, Data EEPROM: {}KiB)",
                    self.name,
                    self.flash_size / 1024,
                    self.eeprom_size / 1024)
        } else {
            format!("{} (Code Flash: {}KiB)",
                    self.name,
                    self.flash_size / 1024)
        }
    }
    
    pub fn encryption_supported(&self) -> bool {
        matches!(self.family, 
                 ChipFamily::CH32V | ChipFamily::CH32F | 
                 ChipFamily::CH582 | ChipFamily::CH579)
    }
}

/// Chip database for device identification
pub struct ChipDB {
    chips: HashMap<(u8, u8), Chip>,
}

impl ChipDB {
    pub fn load() -> anyhow::Result<Self> {
        let mut chips = HashMap::new();
        
        let ch32v307 = Chip::ch32v307();
        chips.insert((ch32v307.chip_id, ch32v307.device_type), ch32v307);
        
        let ch32v103 = Chip::ch32v103();
        chips.insert((ch32v103.chip_id, ch32v103.device_type), ch32v103);
        
        let ch32f103 = Chip::ch32f103();
        chips.insert((ch32f103.chip_id, ch32f103.device_type), ch32f103);
        
        let ch582 = Chip::ch582();
        chips.insert((ch582.chip_id, ch582.device_type), ch582);
        
        Ok(Self { chips })
    }

    pub fn find_chip(&self, chip_id: u8, device_type: u8) -> anyhow::Result<Chip> {
        self.chips
            .get(&(chip_id, device_type))
            .cloned()
            .or_else(|| {
                Some(Chip {
                    name: format!("Unknown[0x{:02X}{:02X}]", chip_id, device_type),
                    chip_id,
                    device_type,
                    flash_size: 64 * 1024,
                    eeprom_size: 0,
                    config_registers: vec![],
                    family: ChipFamily::Unknown,
                })
            })
            .ok_or_else(|| anyhow::anyhow!("Unknown chip: ID=0x{:02X}, Type=0x{:02X}", chip_id, device_type))
    }
}

impl std::fmt::Display for Chip {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}[0x{:04x}]", self.name, (self.chip_id as u16) << 8 | self.device_type as u16)
    }
}