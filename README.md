# Telokuh Manager

Android file manager with dual panels, archive browsing, APK inspection, media tools, and optional root-assisted operations.

> Root operations can modify or delete system and application files. Use them only on devices you control and keep a backup.

## Requirements

- Android Studio with JDK 17
- Android API level 30 or newer
- A device/emulator for instrumentation tests

## Build

```bash
bash ./gradlew assembleDebug
bash ./gradlew testDebugUnitTest lintDebug
```

A release build must be signed with credentials supplied outside the repository:

```bash
export KEYSTORE_PATH=/secure/path/upload-keystore.jks
export STORE_PASSWORD='…'
export KEY_ALIAS='…'
export KEY_PASSWORD='…'
bash ./gradlew assembleRelease
```

The project intentionally does not include signing keys or generated APKs. Never commit either of them.

## Storage and package access

The application uses Android's storage access mechanisms for file management. Full-file access and package visibility are sensitive permissions; users should grant them only when the relevant feature needs them. Where possible, prefer the Storage Access Framework to select a folder instead of granting broad access.

## Contribution checks

Before opening a pull request, run the build checks above. New filesystem, archive, and root-command behavior should include focused unit tests, including hostile file names and malformed archives.
