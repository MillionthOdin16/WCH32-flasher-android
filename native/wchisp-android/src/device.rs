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
    /// Create a placeholder chip for development
    pub fn placeholder() -> Self {
        Self {
            name: "CH32V307VCT6".to_string(),
            chip_id: 0x70,
            device_type: 0x17,
            flash_size: 256 * 1024, // 256KiB
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32V,
        }
    }

    /// Create CH32V307 chip definition
    pub fn ch32v307() -> Self {
        Self {
            name: "CH32V307".to_string(),
            chip_id: 0x70,
            device_type: 0x17,
            flash_size: 256 * 1024,
            eeprom_size: 0,
            config_registers: vec![
                ConfigRegister {
                    name: "RDPR_USER".to_string(),
                    offset: 0,
                    reset: Some(0x9F605AA5),
                    enable_debug: Some(0x9F605AA5),
                    fields: vec![
                        ConfigField {
                            name: "RDPR".to_string(),
                            bit_range: [7, 0],
                            explaination: vec![
                                ("0xA5".to_string(), "Unprotected".to_string()),
                                ("_".to_string(), "Protected".to_string()),
                            ],
                        },
                    ],
                    explaination: vec![
                        ("0x9F605AA5".to_string(), "Default unprotected state".to_string()),
                    ],
                },
            ],
            family: ChipFamily::CH32V,
        }
    }

    /// Create CH32V103 chip definition  
    pub fn ch32v103() -> Self {
        Self {
            name: "CH32V103".to_string(),
            chip_id: 0x10,
            device_type: 0x03,
            flash_size: 64 * 1024, // 64KiB typical
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
            flash_size: 128 * 1024, // 128KiB typical
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
            flash_size: 448 * 1024, // 448KiB
            eeprom_size: 32 * 1024,  // 32KiB
            config_registers: vec![],
            family: ChipFamily::CH582,
        }
    }

    pub fn support_code_flash_protect(&self) -> bool {
        // Most CH32 chips support flash protection
        matches!(self.family, ChipFamily::CH32V | ChipFamily::CH32F)
    }

    pub fn uid_size(&self) -> usize {
        // Standard UID size for most CH32 chips
        match self.family {
            ChipFamily::CH32V | ChipFamily::CH32F => 8,
            ChipFamily::CH582 | ChipFamily::CH579 | ChipFamily::CH592 | ChipFamily::CH573 => 8,
            _ => 4,
        }
    }

    pub fn min_erase_sector_number(&self) -> u32 {
        // Minimum sectors to erase (1KB per sector typically)
        1
    }

    pub fn sector_size(&self) -> u32 {
        // Most WCH chips use 1KB sectors
        1024
    }

    pub fn get_chip_info(&self) -> String {
        if self.eeprom_size > 0 {
            if self.eeprom_size % 1024 != 0 {
                format!("{} (Code Flash: {}KiB, Data EEPROM: {} Bytes)",
                        self.name,
                        self.flash_size / 1024,
                        self.eeprom_size)
            } else {
                format!("{} (Code Flash: {}KiB, Data EEPROM: {}KiB)",
                        self.name,
                        self.flash_size / 1024,
                        self.eeprom_size / 1024)
            }
        } else {
            format!("{} (Code Flash: {}KiB)",
                    self.name,
                    self.flash_size / 1024)
        }
    }
}

/// Chip database for device identification
pub struct ChipDB {
    chips: HashMap<(u8, u8), Chip>,
}

impl ChipDB {
    pub fn load() -> anyhow::Result<Self> {
        let mut chips = HashMap::new();
        
        // Add known chip definitions
        let ch32v307 = Chip::ch32v307();
        chips.insert((ch32v307.chip_id, ch32v307.device_type), ch32v307);
        
        let ch32v103 = Chip::ch32v103();
        chips.insert((ch32v103.chip_id, ch32v103.device_type), ch32v103);
        
        let ch32f103 = Chip::ch32f103();
        chips.insert((ch32f103.chip_id, ch32f103.device_type), ch32f103);
        
        let ch582 = Chip::ch582();
        chips.insert((ch582.chip_id, ch582.device_type), ch582);

        // Add more chip definitions as needed
        // TODO: Load from embedded YAML files or extend this list
        
        Ok(Self { chips })
    }

    pub fn find_chip(&self, chip_id: u8, device_type: u8) -> anyhow::Result<Chip> {
        self.chips
            .get(&(chip_id, device_type))
            .cloned()
            .or_else(|| {
                // Fallback to a generic chip definition if not found
                Some(Chip {
                    name: format!("Unknown[0x{:02X}{:02X}]", chip_id, device_type),
                    chip_id,
                    device_type,
                    flash_size: 64 * 1024, // Default 64KB
                    eeprom_size: 0,
                    config_registers: vec![],
                    family: ChipFamily::Unknown,
                })
            })
            .ok_or_else(|| anyhow::anyhow!("Unknown chip: ID=0x{:02X}, Type=0x{:02X}", chip_id, device_type))
    }

    pub fn list_supported_chips(&self) -> Vec<&Chip> {
        self.chips.values().collect()
    }

    pub fn is_chip_supported(&self, chip_id: u8, device_type: u8) -> bool {
        self.chips.contains_key(&(chip_id, device_type))
    }
}

impl std::fmt::Display for Chip {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}[0x{:04x}]", self.name, (self.chip_id as u16) << 8 | self.device_type as u16)
    }
}

impl std::fmt::Display for ChipFamily {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ChipFamily::CH32V => write!(f, "CH32V"),
            ChipFamily::CH32F => write!(f, "CH32F"),
            ChipFamily::CH549 => write!(f, "CH549"),
            ChipFamily::CH552 => write!(f, "CH552"),
            ChipFamily::CH582 => write!(f, "CH582"),
            ChipFamily::CH579 => write!(f, "CH579"),
            ChipFamily::CH559 => write!(f, "CH559"),
            ChipFamily::CH592 => write!(f, "CH592"),
            ChipFamily::CH573 => write!(f, "CH573"),
            ChipFamily::Unknown => write!(f, "Unknown"),
        }
    }
}