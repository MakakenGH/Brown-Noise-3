# Deep Brown: brown noise generator (PWA)

Brown noise is generated live on the device (an AudioWorklet, off the main thread), so depth can be changed
while it plays and it never repeats. Works offline once loaded. When the app is hidden, a seamlessly looping
90-second render of the same sound takes over so playback can continue with the screen off.

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

index.html, styles.css, app.js        the app
noise-processor.js                    the noise generator (audio thread)
noise-clip.js                         sound mapping and the background WAV render
sw.js, manifest.webmanifest, icons/   offline + installability

When you change any file, bump `CACHE` in sw.js so installed copies pick up the update on next launch.
