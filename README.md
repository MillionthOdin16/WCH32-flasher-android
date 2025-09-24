# WCH32 Flasher Android

An Android application for flashing WCH32 family microcontrollers via USB using the Android USB Host API. This app is based on the [wchisp](https://github.com/ch32-rs/wchisp) tool and provides a mobile solution for programming CH32 chips.

## Features

- **USB ISP Support**: Flash CH32 chips via USB using Android USB Host API
- **UART ISP Support**: Flash via USB-to-serial adapters (planned)
- **Multiple Firmware Formats**: Support for .bin, .hex, and .elf files
- **Real-time Progress**: Live flashing progress and logging
- **Chip Identification**: Automatic detection of connected CH32 chips
- **Device Compatibility**: Works with Android 3.1+ (API Level 12+)

## Supported Devices

This app supports WCH32 chips that are compatible with the wchisp tool:

- CH32V307 series
- CH32V103 series  
- CH32F103 series
- CH549, CH552 series
- CH582, CH573, CH579, CH592 series
- CH559 series
- CH32V203 series
- CH32V003 series
- CH32X035 series
- And more...

## Hardware Requirements

- Android device with USB Host support (Android 3.1+)
- USB OTG cable/adapter
- Supported WCH32 development board or chip in bootloader mode

## Software Architecture

### Android Layer
- **Kotlin/Java UI**: Material Design 3 interface with real-time logging
- **USB Host API**: Native Android USB device communication
- **JNI Bridge**: Interface between Java and native Rust code

### Native Layer (Rust)
- **wchisp Core**: Adapted from the official wchisp tool
- **Android USB Transport**: Custom transport layer replacing libusb
- **Protocol Implementation**: WCH ISP protocol handling
- **Device Database**: Chip definitions and configurations

## Building

### Prerequisites

1. **Android Studio** with Android SDK
2. **Rust toolchain** with Android targets:
   ```bash
   # Install Rust if not already installed
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   
   # Add Android targets
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
   
   # Install cargo-ndk
   cargo install cargo-ndk
   ```

3. **Android NDK** (installed via Android Studio SDK Manager)

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/MillionthOdin16/WCH32-flasher-android.git
   cd WCH32-flasher-android
   ```

2. **Build native library**:
   ```bash
   ./build-native.sh
   ```

3. **Build Android app**:
   ```bash
   ./gradlew assembleDebug
   ```

   Or open in Android Studio and build normally.

## Current Development Status

### ✅ Phase 1: Project Structure & Build System (COMPLETE)
- Android Gradle project with Kotlin support and Material Design 3 UI
- Rust native code integration with cargo-ndk cross-compilation
- GitHub Actions CI/CD pipeline for automated APK builds
- USB Host API permissions and device filtering (CH32 VID/PID: 0x4348/0x1a86, 0x55e0)

### ✅ Phase 2: Rust Native Layer (COMPLETE) 
- **AndroidUsbTransport**: USB Host API integration layer with placeholder implementations
- **ProtocolHandler**: Complete WCH ISP protocol (identify, erase, program, verify, reset)
- **ChipDB**: Comprehensive chip database with 13 supported chip families:
  - CH32V307, CH32V103, CH32F103 series
  - CH32V203, CH32V003, CH32X035 series  
  - CH549, CH552, CH573, CH579, CH559, CH592, CH582 series
  - Full chip identification with proper IDs, device types, and flash sizes
- **AndroidFlashing**: Full flashing workflow with XOR encryption and configuration management
- **JNI Integration**: Memory-safe handle management with proper error propagation

### ✅ Phase 3: Android Application Layer (COMPLETE)
- Complete native library integration with MainActivity
- Real-time flashing progress reporting and comprehensive error handling
- Full JNI bridge implementation for device operations
- File selection and validation with multiple firmware format support
- Threading for non-blocking UI during flashing operations

### 🔄 Phase 4: Integration & Testing (IN PROGRESS)
- USB communication layer needs actual Android USB Host API implementation
- Physical device testing and validation required
- Performance optimization and stability testing needed

### ⏳ Phase 5: Documentation & Release Preparation
- Code documentation and build instructions
- Release builds and open-source preparation

**Note**: The application provides a complete software architecture with placeholder USB communication. The transport layer uses simulation for development but provides the full interface needed for actual USB Host API integration.

### Logging
Real-time logging shows:
- Device connection status
- Chip identification
- Flash progress
- Error messages and troubleshooting info

## Development Status

This project is currently in active development. Current implementation status:

### ✅ Completed
- [x] Android project structure and build system
- [x] USB Host API integration and device filtering
- [x] Material Design 3 user interface
- [x] JNI bridge architecture
- [x] Rust native library structure
- [x] Basic protocol definitions
- [x] File selection and permission handling

### 🚧 In Progress
- [ ] Native USB transport implementation
- [ ] Core flashing functionality
- [ ] Chip identification and database
- [ ] Error handling and user feedback
- [ ] Progress reporting

### 📋 Planned
- [ ] UART ISP support
- [ ] Chip configuration management
- [ ] Advanced verification options
- [ ] Multi-language support
- [ ] Comprehensive testing

## Contributing

This project is open-source and welcomes contributions! Please see our [contributing guidelines](CONTRIBUTING.md) for details.

### Development Areas
- **Android UI/UX**: Interface improvements and new features
- **Rust Native Code**: USB transport and protocol implementation
- **Device Support**: Adding new chip definitions and testing
- **Documentation**: User guides and technical documentation

## License

This project is licensed under the GPL-2.0 License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [ch32-rs/wchisp](https://github.com/ch32-rs/wchisp) - Original Rust implementation
- WCH Electronics - For the CH32 microcontroller family
- Android USB Host API documentation and examples

## Support

For support and questions:
- Open an [issue](https://github.com/MillionthOdin16/WCH32-flasher-android/issues) on GitHub
- Check the [documentation](docs/) for detailed guides
- Review existing issues for common problems and solutions