# Colors of Noise: noise generator (Android)

A native Android app that generates white, brown, pink and green noise live on
the device. Nothing is streamed or pre-recorded, so sounds never repeat and can
be shaped while they play.

## What it does

* Colors of Noise: tap White, Brown, Pink or Green to start a generic noise of
  that colour. It plays immediately and is not added to your collection.
* My sounds: save your own sounds with a name, colour, deepness, rumble, width
  and swell. Tap a saved sound to play or stop it; its three-dot menu opens edit,
  rename, reorder and delete. Add a sound from the button next to the header.
* A now-playing card above the bottom bar always shows the loaded sound and lets
  you play or pause it. Tap its body to open the sound in the editor.
* Volume and a sleep timer (15 min, 30 min, 1 h, 2 h) live in Playback settings.
* The Noise Generator editor previews changes live while it plays.
* Playback runs in a foreground media service, so it keeps going when the app is
  minimized, the screen is off, or the activity is gone. Lock-screen and headset
  controls work, including over Bluetooth.
* No `INTERNET` permission: everything is generated on the phone.

The persistent playback notification is required by Android for continuous
background audio; use its **Stop** action to end playback.

## Install

Install the APK on the phone (allow “install unknown apps” for the file
manager), or connect it with USB debugging enabled and run:

```
adb install -r dist/colors-of-noise.apk
```

If your phone has aggressive battery management, set Colors of Noise to
**Unrestricted** (Settings → Apps → Colors of Noise → App battery usage).

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
  MainActivity.kt   UI entry point, service binding and library actions
  App.kt            Home/editor navigation and bottom sheets
  HomeScreen.kt     Quick Noise, the sound collection and bottom bar
  EditorScreen.kt   Noise Generator editor with the deepness control
  Sheets.kt         Sound settings, rename/naming and playback sheets
  Components.kt     Rows, sliders, colour picker, icons and small parts
  Theme.kt          Palette and typography
  Model.kt          Noise colours, saved sounds and editor drafts
  SoundStore.kt     Local persistence for the collection and volume
  NoiseService.kt   foreground media service, notification, media session
  NoiseEngine.kt    AudioTrack playback and gain/fade handling
  NoiseDsp.kt       noise generators and filter chain
  Sound.kt          parameter mapping and loudness model
android/app/src/main/res/font/   Fraunces and DM Sans (SIL Open Font License)
android/app/src/test/           JVM tests for the DSP
licenses/                       Font licences
```
