# Foniebox

Foniebox is a small Android app that shuffles MP3 files from a folder, plays
them for a chosen duration, and then gradually fades out before stopping.

![Foniebox mascot](foniebox_head.png)

## Features

- Choose a folder using Android's system folder picker.
- Find MP3 files in the selected folder and its subfolders.
- Shuffle tracks and start a new shuffled queue when the queue finishes.
- Set playback and fade-out durations independently.
- Keep playing in the background with a foreground service and notification.
- Stop playback from the app or its notification.
- Save the folder selection and duration settings on the device.

The app uses local audio files and does not request internet access. Audio files
are supplied by the user and are not included in this repository.

## Requirements

- Android 8.0 (API 26) or newer to run the app.
- JDK 21 to build it.
- Android SDK with the Android 15 (API 35) platform installed.

The project includes the Gradle 8.13 wrapper, so a separate Gradle installation
is unnecessary. The first build needs internet access to download Gradle and
build dependencies. Set `JAVA_HOME` to your JDK 21 installation if needed.

## Set up the Android SDK

Open the project in Android Studio and let it configure the SDK, or create a
local `local.properties` file containing your SDK location:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

On Windows, use forward slashes in the SDK path or escape backslashes as
required by Java properties files. `local.properties` is ignored by Git because
it contains machine-specific paths.

## Build and check

From the project root, on macOS or Linux:

```sh
./gradlew assembleDebug lintDebug
```

On Windows, in PowerShell:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The lint report is written to `app/build/reports/lint-results-debug.html`.

To install the debug APK on an emulator or device connected through ADB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The application ID is `com.example.foniebox`. Builds with a different
application ID install as separate apps and have separate saved settings.

## Use the app

1. Choose a folder containing MP3 files.
2. Set the playback time and fade-out time in minutes.
3. Press **Start**. Press **Stop** to end playback early.

The default is 30 minutes of playback followed by an additional 10 minutes of
fade-out. On Android 13 or newer, allow notifications to see playback controls
in the notification area.

## Project layout

- `app/src/main/java/com/example/foniebox/MainActivity.kt`: UI, folder selection,
  settings, and recursive MP3 scanning.
- `app/src/main/java/com/example/foniebox/PlaybackService.kt`: playback queue,
  foreground notification, timing, and volume fade-out.
- `app/src/main/AndroidManifest.xml`: components and permissions.
- `app/src/main/res/`: app strings, theme, and images.
- `app/build.gradle.kts`: application ID, SDK levels, and Java/Kotlin targets.
- `gradle/wrapper/`: Gradle wrapper files.

There are currently no automated unit or instrumentation tests. Build and lint
checks cover compilation and static analysis; playback should also be checked
on an emulator or device.

## Publication hygiene

Git ignores local SDK settings, IDE state, build output, logs, environment
files, and common signing/credential files. Keep signing material and private
configuration outside version control, and review the file list before pushing.

No project license has been selected. The bundled Gradle scripts retain their
existing license notices.
