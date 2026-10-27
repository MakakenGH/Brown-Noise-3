# Deep Brown: brown noise generator (PWA)

Brown noise is generated live on the device (an AudioWorklet, off the main thread), so depth can be changed
while it plays and it never repeats. Works offline once loaded. When the app is hidden, a seamlessly looping
90-second render of the same sound takes over so playback can continue with the screen off.

## Native Android app (recommended for overnight use)

A web app cannot guarantee audio through a whole night: Chrome may freeze or kill the page, and a PWA has no
foreground service. The `android/` folder contains a small native Android app that fixes that:

* the noise is generated natively and played by an `AudioTrack` from a foreground media service,
* it keeps playing when the app is minimized, the screen is off, or the activity is gone,
* it works over Bluetooth with lock-screen/headset controls,
* a sleep timer fades out and stops the service,
* it has no `INTERNET` permission, so it is fully offline.

Install `dist/deep-brown.apk` on the phone (allow “install unknown apps” for the file manager), or connect it with
USB debugging enabled and run:

```
/tmp/opencode/toolchain/android-sdk/platform-tools/adb install -r dist/deep-brown.apk
```

The app keeps the same six presets and remembers the last selected one. The persistent playback notification is
required by Android for continuous background audio; use its **Stop** action to end playback. If your phone has
aggressive battery management, set Deep Brown to **Unrestricted** (Settings → Apps → Deep Brown → App battery
usage).

Build it yourself with JDK 17 and Android SDK 35:

```
cd android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

(`assembleDebug` also works for development.)

## Run it

A PWA needs HTTPS (or `localhost`). Opening `index.html` from the file system will not work.

* Quick test on your computer:  `python3 -m http.server 8080` in this folder, then open http://localhost:8080
* To use it on your Android phone: upload this folder to any static HTTPS host
  (GitHub Pages, Cloudflare Pages, Netlify Drop, your own server), open the URL in Chrome, then
  menu > "Install app" (or tap the Install button in the app).

## Background playback on Android

Android stops Web Audio and MediaStreams as soon as the app is hidden or the screen turns off. To keep playing,
the app renders the current sound to a short, seamless WAV file (with the same worklet), and hands that file to
its `<audio>` element the moment it goes to the background. Nothing to configure; the lock-screen player controls
the same file.

Battery settings can still stop any app in the background:

1. Set battery use to **Unrestricted** for Chrome and the installed Deep Brown app, if Android lists it separately:
   Settings > Apps > Chrome (or Deep Brown) > App battery usage. Menu names vary by Android version.
2. Don't force-close the app from the recents screen while it plays. A web app cannot override the phone's
   system-level power or audio-focus controls.

The file is rendered while the app is visible, shortly after playback starts or the sound changes, so hiding the
app hands over instantly.

## Files

index.html, styles.css, app.js        the PWA
noise-processor.js                    the PWA's noise generator (audio thread)
noise-clip.js                         PWA sound mapping and the background WAV render
android/                              native Android app for reliable background playback
sw.js, manifest.webmanifest, icons/   PWA offline + installability

When you change any file, bump `CACHE` in sw.js so installed copies pick up the update on next launch.
