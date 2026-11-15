# MiRear

Cyberpunk-style Android companion for the secondary rear display on Xiaomi Mi 11 Ultra (M2102K1G).

## Current status
This is the initial buildable Android scaffold. It does **not yet mirror the main display**. The first milestone establishes the app, diagnostics screen, and reproducible GitHub Actions APK build. Device-specific display routing will be implemented only after validating the MIUI 1.0.5.0.UKAMIXM / Android 14 behavior.

## Build
Push to `main` or run the **Build MiRear APK** workflow manually. Download the `MiRear-debug-apk` artifact from the completed Actions run.

## Access strategy
- Standard mode: public Android APIs, no root.
- Extended mode: optional Shizuku/ADB bridge, only where required and supported.
- Root is not required for the initial app or build.

## Device target
- Xiaomi Mi 11 Ultra (M2102K1G)
- MIUI/HyperOS 1.0.5.0.UKAMIXM
- Android 14 / API 34
- Compile SDK 35; min SDK 26

## License
The application scaffold is provided under GPL-3.0-or-later. Any third-party code will retain its original license and attribution.
