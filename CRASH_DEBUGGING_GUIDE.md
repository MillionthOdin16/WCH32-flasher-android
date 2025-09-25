# WCH32 Flasher Android - Crash Debugging Guide

## Current Status
The app has been enhanced with comprehensive crash debugging capabilities to identify the Android 16 crash issue.

## Debug APK Location
After building, the debug APK is located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Debug Features Added

### 1. Global Exception Handler
- Captures ALL uncaught exceptions with detailed stack traces
- Logs to Android's system log (accessible via `adb logcat`)

### 2. Comprehensive Application-Level Testing
- Tests system services (USB Manager, Package Manager)
- Tests WchispNative class functionality
- Tests manifest permissions and layout resources
- Runs BEFORE MainActivity to catch early initialization issues

### 3. Detailed MainActivity Logging
- Step-by-step logging through onCreate() method
- Identifies exactly where crashes occur during activity startup

## How to Debug the Crash

### Method 1: Using ADB (Recommended)
1. Install the debug APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. Clear existing logs and start capturing:
   ```bash
   adb logcat -c
   adb logcat | grep -E "(DebugApplication|WCH32Flasher|AndroidRuntime)"
   ```

3. Launch the app (it will crash)

4. Check the logs for detailed crash information:
   - Look for "DebugApplication" logs to see what passes/fails
   - Look for "WCH32Flasher" logs from MainActivity
   - Look for "AndroidRuntime" FATAL EXCEPTION entries

### Method 2: Using Android Studio
1. Open the project in Android Studio
2. Connect Android device or start emulator
3. Run the app in debug mode
4. Check the Logcat panel for crash details

### Method 3: Using Device Logs (if ADB not available)
1. Install a log viewing app on the device (like "aLogcat")
2. Install and run the WCH32 Flasher app
3. Use the log viewer to find crash details

## Expected Log Output

### Successful Startup Logs:
```
DebugApplication: === DebugApplication.onCreate() starting ===
DebugApplication: Android version: X.X (API XX)
DebugApplication: Device: [Device Info]
DebugApplication: USB Manager: Available
DebugApplication: WchispNative class tests passed
DebugApplication: === DebugApplication.onCreate() completed successfully ===
WCH32Flasher: *** MainActivity.onCreate() STARTING ***
WCH32Flasher: Step 1: super.onCreate()
[... more steps ...]
WCH32Flasher: *** MainActivity.onCreate() COMPLETED SUCCESSFULLY ***
```

### Crash Logs:
```
DebugApplication: Error testing [component]: [error message]
[OR]
WCH32Flasher: *** CRASH in MainActivity.onCreate() ***
AndroidRuntime: FATAL EXCEPTION: [details]
```

## Common Android 16 Issues to Check

### 1. Permission Issues
- New runtime permission model
- USB Host API restrictions
- Storage access changes

### 2. API Level Compatibility
- Deprecated APIs in Android 16
- New security restrictions
- Updated permission requirements

### 3. Library Dependencies
- Material Design compatibility
- AndroidX library versions
- Kotlin compatibility

### 4. View Binding Issues
- Layout inflation problems
- Resource access restrictions
- Theme/style conflicts

## Next Steps Based on Logs

### If DebugApplication crashes:
- The issue is in basic app initialization
- Check Android version compatibility
- Verify manifest configuration

### If MainActivity crashes:
- The issue is in UI setup or activity lifecycle
- Check view binding and layout resources
- Verify activity configuration

### If WchispNative crashes:
- The issue is in native library simulation
- Check object initialization
- Verify Kotlin object syntax

## Additional Debugging Options

### Enable More Verbose Logging:
```bash
adb shell setprop log.tag.DebugApplication VERBOSE
adb shell setprop log.tag.WCH32Flasher VERBOSE
```

### Check System Information:
```bash
adb shell getprop | grep -E "(version|api|sdk)"
adb shell dumpsys package com.wch.flasher
```

### Monitor Resource Usage:
```bash
adb shell top | grep com.wch.flasher
```

## Fallback Testing

If the full app crashes, we can also create a minimal test app with just:
1. Empty Activity
2. Basic logging
3. Minimal WchispNative access

This would help isolate whether the issue is with the core Android framework or our specific implementation.

## Contact for Results

After running these debugging steps, the crash logs will provide definitive information about:
- Exactly where the crash occurs
- What Android 16 specific issue is causing it
- Whether it's a native library, permission, UI, or other system issue

The comprehensive logging should eliminate any guesswork about the crash location and cause.