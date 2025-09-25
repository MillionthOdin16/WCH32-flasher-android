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
    CH32V003,
    CH32X035,
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

    /// Create CH32V203 chip definition
    pub fn ch32v203() -> Self {
        Self {
            name: "CH32V203".to_string(),
            chip_id: 0x30,  // CH32V203C8U6 chip_id
            device_type: 0x19,  // CH32V20x series device_type
            flash_size: 64 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32V,
        }
    }

    /// Create CH32V003 chip definition
    pub fn ch32v003() -> Self {
        Self {
            name: "CH32V003".to_string(),
            chip_id: 0x30,  // CH32V003F4P6 chip_id
            device_type: 0x21,  // CH32V00x series device_type
            flash_size: 16 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32V003,
        }
    }

    /// Create CH32X035 chip definition
    pub fn ch32x035() -> Self {
        Self {
            name: "CH32X035".to_string(),
            chip_id: 0x50,  // CH32X035R8T6 chip_id (80 in decimal = 0x50)
            device_type: 0x23,  // CH32X03x series device_type
            flash_size: 62 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH32X035,
        }
    }

    /// Create CH549 chip definition
    pub fn ch549() -> Self {
        Self {
            name: "CH549".to_string(),
            chip_id: 0x49,
            device_type: 0x11,  // CH55x series device_type
            flash_size: 62 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH549,
        }
    }

    /// Create CH552 chip definition
    pub fn ch552() -> Self {
        Self {
            name: "CH552".to_string(),
            chip_id: 0x52,
            device_type: 0x11,  // CH55x series device_type
            flash_size: 16 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH552,
        }
    }

    /// Create CH573 chip definition
    pub fn ch573() -> Self {
        Self {
            name: "CH573".to_string(),
            chip_id: 0x73,
            device_type: 0x13,  // CH57x series device_type
            flash_size: 448 * 1024,
            eeprom_size: 32 * 1024,
            config_registers: vec![],
            family: ChipFamily::CH573,
        }
    }

    /// Create CH579 chip definition
    pub fn ch579() -> Self {
        Self {
            name: "CH579".to_string(),
            chip_id: 0x79,
            device_type: 0x13,  // CH57x series device_type
            flash_size: 250 * 1024,
            eeprom_size: 2 * 1024,
            config_registers: vec![],
            family: ChipFamily::CH579,
        }
    }

    /// Create CH559 chip definition
    pub fn ch559() -> Self {
        Self {
            name: "CH559".to_string(),
            chip_id: 0x59,
            device_type: 0x22,  // CH59x series device_type
            flash_size: 62 * 1024,
            eeprom_size: 0,
            config_registers: vec![],
            family: ChipFamily::CH559,
        }
    }

    /// Create CH592 chip definition
    pub fn ch592() -> Self {
        Self {
            name: "CH592".to_string(),
            chip_id: 0x92,
            device_type: 0x13,  // CH57x series device_type (CH592 is in BLE family like CH57x)
            flash_size: 250 * 1024,
            eeprom_size: 2 * 1024,
            config_registers: vec![],
            family: ChipFamily::CH592,
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
                 ChipFamily::CH582 | ChipFamily::CH579 |
                 ChipFamily::CH573 | ChipFamily::CH592 |
                 ChipFamily::CH32V003 | ChipFamily::CH32X035)
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
        
        // Add CH32V203 support
        let ch32v203 = Chip::ch32v203();
        chips.insert((ch32v203.chip_id, ch32v203.device_type), ch32v203);
        
        // Add CH32V003 support
        let ch32v003 = Chip::ch32v003();
        chips.insert((ch32v003.chip_id, ch32v003.device_type), ch32v003);
        
        // Add CH32X035 support
        let ch32x035 = Chip::ch32x035();
        chips.insert((ch32x035.chip_id, ch32x035.device_type), ch32x035);
        
        // Add CH549 support
        let ch549 = Chip::ch549();
        chips.insert((ch549.chip_id, ch549.device_type), ch549);
        
        // Add CH552 support
        let ch552 = Chip::ch552();
        chips.insert((ch552.chip_id, ch552.device_type), ch552);
        
        // Add CH573 support
        let ch573 = Chip::ch573();
        chips.insert((ch573.chip_id, ch573.device_type), ch573);
        
        // Add CH579 support
        let ch579 = Chip::ch579();
        chips.insert((ch579.chip_id, ch579.device_type), ch579);
        
        // Add CH559 support
        let ch559 = Chip::ch559();
        chips.insert((ch559.chip_id, ch559.device_type), ch559);
        
        // Add CH592 support
        let ch592 = Chip::ch592();
        chips.insert((ch592.chip_id, ch592.device_type), ch592);
        
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_chip_database_load() {
        let chip_db = ChipDB::load().expect("Failed to load chip database");
        
        // Test that all expected chips are loaded
        assert!(chip_db.find_chip(0x70, 0x17).is_ok()); // CH32V307
        assert!(chip_db.find_chip(0x30, 0x30).is_ok()); // CH32V103
        assert!(chip_db.find_chip(0x10, 0x30).is_ok()); // CH32F103
        assert!(chip_db.find_chip(0x82, 0x82).is_ok()); // CH582
        assert!(chip_db.find_chip(0x30, 0x19).is_ok()); // CH32V203
        assert!(chip_db.find_chip(0x30, 0x21).is_ok()); // CH32V003
        assert!(chip_db.find_chip(0x50, 0x23).is_ok()); // CH32X035
        assert!(chip_db.find_chip(0x49, 0x11).is_ok()); // CH549
        assert!(chip_db.find_chip(0x52, 0x11).is_ok()); // CH552
        assert!(chip_db.find_chip(0x73, 0x13).is_ok()); // CH573
        assert!(chip_db.find_chip(0x79, 0x13).is_ok()); // CH579
        assert!(chip_db.find_chip(0x59, 0x22).is_ok()); // CH559
        assert!(chip_db.find_chip(0x92, 0x13).is_ok()); // CH592
    }

    #[test]
    fn test_ch32v203_chip_definition() {
        let chip = Chip::ch32v203();
        assert_eq!(chip.name, "CH32V203");
        assert_eq!(chip.chip_id, 0x30);
        assert_eq!(chip.device_type, 0x19);
        assert_eq!(chip.flash_size, 64 * 1024);
        assert_eq!(chip.eeprom_size, 0);
        assert!(matches!(chip.family, ChipFamily::CH32V));
        assert!(chip.encryption_supported());
        assert!(chip.support_code_flash_protect());
    }

    #[test]
    fn test_ch32v003_chip_definition() {
        let chip = Chip::ch32v003();
        assert_eq!(chip.name, "CH32V003");
        assert_eq!(chip.chip_id, 0x30);
        assert_eq!(chip.device_type, 0x21);
        assert_eq!(chip.flash_size, 16 * 1024);
        assert_eq!(chip.eeprom_size, 0);
        assert!(matches!(chip.family, ChipFamily::CH32V003));
        assert!(chip.encryption_supported());
    }

    #[test]
    fn test_ch32x035_chip_definition() {
        let chip = Chip::ch32x035();
        assert_eq!(chip.name, "CH32X035");
        assert_eq!(chip.chip_id, 0x50);
        assert_eq!(chip.device_type, 0x23);
        assert_eq!(chip.flash_size, 62 * 1024);
        assert_eq!(chip.eeprom_size, 0);
        assert!(matches!(chip.family, ChipFamily::CH32X035));
        assert!(chip.encryption_supported());
    }

    #[test]
    fn test_chip_info_display() {
        let ch32v203 = Chip::ch32v203();
        let info = ch32v203.get_chip_info();
        assert!(info.contains("CH32V203"));
        assert!(info.contains("64KiB"));

        let ch582 = Chip::ch582();
        let info = ch582.get_chip_info();
        assert!(info.contains("CH582"));
        assert!(info.contains("448KiB"));
        assert!(info.contains("32KiB")); // EEPROM
    }

    #[test]
    fn test_encryption_support() {
        assert!(Chip::ch32v203().encryption_supported());
        assert!(Chip::ch32v003().encryption_supported());
        assert!(Chip::ch32x035().encryption_supported());
        assert!(Chip::ch32v307().encryption_supported());
        assert!(Chip::ch32f103().encryption_supported());
        assert!(Chip::ch582().encryption_supported());
        assert!(Chip::ch573().encryption_supported());
        assert!(Chip::ch579().encryption_supported());
        assert!(Chip::ch592().encryption_supported());
        
        // CH55x and CH59x series typically don't support encryption in the same way
        assert!(!Chip::ch549().encryption_supported());
        assert!(!Chip::ch552().encryption_supported());
        assert!(!Chip::ch559().encryption_supported());
    }

    #[test]
    fn test_unknown_chip_fallback() {
        let chip_db = ChipDB::load().expect("Failed to load chip database");
        let unknown_chip = chip_db.find_chip(0xFF, 0xFF).expect("Should create unknown chip");
        
        assert!(unknown_chip.name.contains("Unknown"));
        assert_eq!(unknown_chip.chip_id, 0xFF);
        assert_eq!(unknown_chip.device_type, 0xFF);
        assert!(matches!(unknown_chip.family, ChipFamily::Unknown));
        assert!(!unknown_chip.encryption_supported());
    }

    #[test]
    fn test_chip_display_format() {
        let chip = Chip::ch32v203();
        let display = format!("{}", chip);
        assert!(display.contains("CH32V203"));
        assert!(display.contains("0x")); // Contains hex formatting
    }
}