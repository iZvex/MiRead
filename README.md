# MiRear

Cyberpunk-style Android app for mirroring the main display to the rear display on Xiaomi Mi 11 Ultra (M2102K1G).

## Current status
- Main-screen capture through Android MediaProjection.
- Rear display output through a VirtualDisplay and SurfaceView.
- Foreground notification with a Stop action.
- Display diagnostics and no-root standard mode.
- GitHub Actions builds the debug APK and uploads it as `MiRear-debug-apk`.
- Current performance tuning targets 15 FPS to reduce GPU/compositor load. Actual smoothness depends on MIUI/HyperOS and device thermal/power state.

## Build
Push to `main` or run **Build MiRear APK** manually. Download the `MiRear-debug-apk` artifact from the completed Actions run.

## Install updates without uninstalling
Android permits an APK to update an installed app only when the package name is unchanged and the signing certificate matches. The GitHub-hosted runner creates a new debug signing key on each run by default, so APKs cannot reliably update one another.

To enable in-place updates, create one persistent keystore and add it to the repository's **Settings → Secrets and variables → Actions**. Never commit the keystore or passwords to the repository.

Create the keystore once in Termux (install a JDK first if `keytool` is missing):

```sh
keytool -genkeypair -v -keystore mirear-release.jks -alias mirear -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 mirear-release.jks
```

Add these four Actions repository secrets:
- `MIREAR_KEYSTORE_BASE64`: the complete one-line Base64 output.
- `MIREAR_STORE_PASSWORD`: the keystore password.
- `MIREAR_KEY_ALIAS`: `mirear`.
- `MIREAR_KEY_PASSWORD`: the key password (it can be the same as the keystore password).

After secrets are configured, every workflow build is signed with the same key. **One-time migration:** the currently installed APK was signed with the runner's temporary debug key, so uninstall that old copy once and install the first persistently signed APK. Subsequent versions can be installed directly over the existing app and retain app data. Keep a secure backup of `mirear-release.jks`; losing it prevents future updates signed with that key.

## Access strategy
- Standard mode: Android public APIs, no root.
- Extended mode: optional Shizuku/ADB bridge, only where required and supported.
- Root is not required for MediaProjection capture.

## Device target
- Xiaomi Mi 11 Ultra (M2102K1G)
- MIUI/HyperOS 1.0.5.0.UKAMIXM
- Android 14 / API 34
- Compile SDK 35; min SDK 26; target SDK 35

## License
The application scaffold is provided under GPL-3.0-or-later. Any third-party code retains its original license and attribution.
