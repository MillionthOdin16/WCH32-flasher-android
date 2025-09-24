//! WCH Device definitions and chip database
//! 
//! This module contains chip definitions extracted from the wchisp device database

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chip {
    pub name: String,
    pub chip_id: u8,
    pub device_type: u8,
    pub flash_size: u32,
    pub eeprom_size: u32,
    pub config_registers: Vec<ConfigRegister>,
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
    /// Create a placeholder chip for development
    pub fn placeholder() -> Self {
        Self {
            name: "CH32V307VCT6".to_string(),
            chip_id: 0x70,
            device_type: 0x17,
            flash_size: 256 * 1024, // 256KiB
            eeprom_size: 0,
            config_registers: vec![],
        }
    }

    pub fn support_code_flash_protect(&self) -> bool {
        // Most CH32 chips support flash protection
        true
    }

    pub fn uid_size(&self) -> usize {
        // Standard UID size for most CH32 chips
        8
    }

    pub fn min_erase_sector_number(&self) -> u32 {
        // Minimum sectors to erase (1KB per sector typically)
        1
    }
}

/// Chip database for device identification
pub struct ChipDB {
    chips: Vec<Chip>,
}

impl ChipDB {
    pub fn load() -> anyhow::Result<Self> {
        // TODO: Load actual chip database from embedded YAML files
        // For now, return a database with placeholder chips
        
        let chips = vec![
            Chip::placeholder(),
        ];

        Ok(Self { chips })
    }

    pub fn find_chip(&self, chip_id: u8, device_type: u8) -> anyhow::Result<Chip> {
        self.chips
            .iter()
            .find(|chip| chip.chip_id == chip_id && chip.device_type == device_type)
            .cloned()
            .ok_or_else(|| anyhow::anyhow!("Unknown chip: ID=0x{:02X}, Type=0x{:02X}", chip_id, device_type))
    }
}

impl std::fmt::Display for Chip {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}[0x{:04x}]", self.name, (self.chip_id as u16) << 8 | self.device_type as u16)
    }
}