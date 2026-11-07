# Deep Brown: brown noise generator (Android)

A native Android app that generates brown noise live on the device. Nothing is
streamed or pre-recorded, so depth can be changed while it plays and the sound
never repeats.

## What it does

* The noise is generated natively and played by an `AudioTrack` from a
  foreground media service.
* It keeps playing when the app is minimized, the screen is off, or the
  activity is gone.
* It works over Bluetooth with lock-screen/headset controls.
* A sleep timer fades out and stops the service.
* Six presets (Velvet, Deep sleep, Abyss, Engine room, Tide, Focus) and the last
  selected one is remembered.
* It has no `INTERNET` permission, so it is fully offline.

The persistent playback notification is required by Android for continuous
background audio; use its **Stop** action to end playback.

## Install

Install the APK on the phone (allow “install unknown apps” for the file
manager), or connect it with USB debugging enabled and run:

```
/tmp/opencode/toolchain/android-sdk/platform-tools/adb install -r dist/deep-brown.apk
```

If your phone has aggressive battery management, set Deep Brown to
**Unrestricted** (Settings → Apps → Deep Brown → App battery usage).

## Build

Build with JDK 17 and Android SDK 35:

```
cd android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

(`assembleDebug` also works for development.)

The project is a single-module Kotlin/Compose app:

```
android/app/src/main/java/com/deepbrown/noise/
  MainActivity.kt   UI entry point, service binding and permissions
  Ui.kt             Compose screen, theme and controls
  NoiseService.kt   foreground media service, notification, media session
  NoiseEngine.kt    AudioTrack playback and gain/fade handling
  NoiseDsp.kt       brown-noise generator and filter chain
  Sound.kt          parameter mapping, presets and loudness model
android/app/src/test/  JVM tests for the DSP
```
