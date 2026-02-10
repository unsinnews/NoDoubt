# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NoDoubt is an Android floating window library that provides device-compatible floating window functionality across different Android manufacturers and ROM versions. It handles the complexity of SYSTEM_ALERT_WINDOW permissions and ROM-specific permission managers (Xiaomi, Huawei, Vivo, Oppo, etc.).

The Demo application is an **AI-powered problem solver** that uses the floating window to capture screenshots and get AI-generated answers.

## Build Commands

```bash
# Build the project
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Build specific module
./gradlew :floatserver:build
./gradlew :app:build
```

## Project Structure

```
NoDoubt/
├── floatserver/          # Library module (com.alonsol:floatserver)
└── app/                  # Demo application (AI problem solver)
```

## Library Architecture (floatserver)

The library uses a layered architecture with proxy pattern:

**Public API Layer:**
- `FloatClient` - Builder-pattern entry point for developers. Configure context, custom view, target activity, and permission callbacks.

**Proxy & Manager Layer:**
- `FloatProxy` - Delegates operations to FloatManager
- `FloatManager` - Manages permission checking, service lifecycle, and binds to FloatingServer

**Service Layer:**
- `FloatingServer` - Android Service that manages WindowManager, handles touch events (drag vs click detection), and adapts window type based on Android version/ROM

**Utils:**
- `SettingsCompat` - Permission checking and ROM-specific settings navigation
- `RomUtil` - Device ROM detection (MIUI, EMUI, Flyme, ColorOS, etc.)
- `JumpUtils` - Cross-process activity navigation using PendingIntent

## Key Implementation Details

**Window Type Adaptation:**
- Android 8.0+: Uses `TYPE_APPLICATION_OVERLAY`
- Older versions/special ROMs: Falls back to `TYPE_PHONE` or `TYPE_TOAST`

**Touch Handling:**
- Click detection: < 200ms touch duration
- Drag: Movement beyond touch slop repositions window
- Click: Launches configured target activity

**Permission Polling:**
- Uses CountDownTimer to poll permission status every 1 second after user is sent to settings

## Demo App Architecture (AI Problem Solver)

**UI Layer (`ui/`):**
- `MainActivity` - Main entry with bottom navigation (Home/Profile tabs)
- `HomeFragment` - Floating window controls and screenshot trigger
- `ProfileFragment` - Theme selection, avatar customization, settings access
- `SettingsActivity` - API configuration (keys, endpoints, models)
- `AnswerPopupService` - Floating answer window with streaming response display
- `ReauthorizationActivity` - MediaProjection re-authorization after screen off

**Network Layer (`network/`):**
- `OpenAIClient` - OkHttp client for OpenAI-compatible APIs with streaming support
- `VisionAPI` - OCR recognition using vision models (captures screenshot → extracts question)
- `ChatAPI` - Answer generation with two modes: fast (quick answer) and deep (detailed steps)
- `StreamingCallback` - Interface for real-time streaming response handling
- `BitmapUtils` - Screenshot compression and Base64 encoding

**Data Layer (`data/`):**
- `AISettings` - SharedPreferences wrapper for API configuration
- `AIConfig` - Data class holding API keys, base URLs, and model IDs
- `ThemeManager` - Theme state (Light Green Gray / Light Brown Black), avatar, nickname
- `Question` / `Answer` - Data models for Q&A display

**Key Flows:**
1. Screenshot capture: MainActivity → MediaProjection → VisionAPI (OCR) → Question extraction
2. Answer generation: Question → ChatAPI (streaming) → AnswerPopupService → Real-time UI update
3. Theme switching: ProfileFragment → ThemeManager → All UI components refresh

## Required Permissions

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.SYSTEM_OVERLAY_WINDOW" />
```

## Theme System

Two themes with consistent color schemes:
- **Light Green Gray**: Primary `#10A37F` (green), background `#F7F7F8`
- **Light Brown Black**: Accent `#DA7A5A` (warm orange), background `#FAF9F5`

Theme-aware drawables follow naming convention: `bg_*_light_green_gray.xml` / `bg_*_light_brown_black.xml`

## SDK Configuration

- Compile SDK: 31
- Min SDK: 19 (Android 4.4)
- Target SDK: 31
- Kotlin: 1.6.10

## Android 11-16 Adaptation Guide

When updating this library to support Android 11-16, consider the following:

**Android 11 (API 30):**
- Package visibility restrictions: Add `<queries>` in AndroidManifest.xml for ROM-specific package detection in `RomUtil` and `SettingsCompat`
- `Settings.canDrawOverlays()` behavior unchanged

**Android 12 (API 31):**
- PendingIntent mutability: Must specify `FLAG_IMMUTABLE` or `FLAG_MUTABLE` (already handled in `JumpUtils.kt`)
- Foreground service launch restrictions from background

**Android 13 (API 33):**
- `POST_NOTIFICATIONS` runtime permission required for foreground service notifications
- Update `compileSdkVersion` and `targetSdkVersion` to 33+

**Android 14 (API 34):**
- Foreground service types required: Add `android:foregroundServiceType` to `FloatingServer` in AndroidManifest.xml
- Use `specialUse` type for overlay windows: `android:foregroundServiceType="specialUse"`
- Declare `FOREGROUND_SERVICE_SPECIAL_USE` permission

**Android 15 (API 35):**
- Edge-to-edge display enforcement may affect window positioning calculations
- Review `wmParams.x/y` calculations in `FloatingServer.kt`

**Android 16 (API 36):**
- Monitor for new overlay/window restrictions in developer preview

**Key Files to Modify:**
- `floatserver/build.gradle`: Update `compileSdkVersion` and `targetSdkVersion`
- `floatserver/src/main/AndroidManifest.xml`: Add foreground service type and new permissions
- `FloatingServer.kt`: Implement `startForeground()` with notification for API 26+
- `SettingsCompat.java`: Add `<queries>` for package visibility
- `JumpUtils.kt`: Already handles PendingIntent flags correctly
