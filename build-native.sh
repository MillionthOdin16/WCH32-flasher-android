#!/bin/bash

# Build script for WCH32 Flasher Android native library
# This script compiles the Rust library for Android targets using cargo-ndk

set -e

echo "Building WCH32 Flasher native library..."

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "cargo-ndk not found. Installing..."
    cargo install cargo-ndk
fi

# Create output directories
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}

# Build for different Android architectures
echo "Building for arm64-v8a..."
cd native/wchisp-android
cargo ndk --target aarch64-linux-android --android-platform 21 -- build --release
cp target/aarch64-linux-android/release/libwchisp_android.so ../../app/src/main/jniLibs/arm64-v8a/

echo "Building for armeabi-v7a..."
cargo ndk --target armv7-linux-androideabi --android-platform 21 -- build --release  
cp target/armv7-linux-androideabi/release/libwchisp_android.so ../../app/src/main/jniLibs/armeabi-v7a/

echo "Building for x86..."
cargo ndk --target i686-linux-android --android-platform 21 -- build --release
cp target/i686-linux-android/release/libwchisp_android.so ../../app/src/main/jniLibs/x86/

echo "Building for x86_64..."
cargo ndk --target x86_64-linux-android --android-platform 21 -- build --release
cp target/x86_64-linux-android/release/libwchisp_android.so ../../app/src/main/jniLibs/x86_64/

cd ../..

echo "Native library build completed successfully!"
echo "Libraries copied to app/src/main/jniLibs/"