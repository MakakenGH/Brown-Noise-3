# Deep Brown: brown noise generator (PWA)

Brown noise is generated live on the device (an AudioWorklet, off the main thread), so depth can be changed
while it plays and it never loops or repeats. Works offline once loaded.

## Run it

A PWA needs HTTPS (or `localhost`). Opening `index.html` from the file system will not work.

* Quick test on your computer:  `python3 -m http.server 8080` in this folder, then open http://localhost:8080
* To use it on your Android phone: upload this folder to any static HTTPS host
  (GitHub Pages, Cloudflare Pages, Netlify Drop, your own server), open the URL in Chrome, then
  menu > "Install app" (or tap the Install button in the app).

## Background playback on Android

1. Install the app (so it runs as its own window), leave Output set to **Stream**, and press play once.
   Stream routes the generated audio through Android's media playback path and provides a player in the
   notification shade / lock screen.
2. Set battery use to **Unrestricted** for Chrome and the installed Deep Brown app, if Android lists it separately:
   Settings > Apps > Chrome (or Deep Brown) > App battery usage. Menu names vary by Android version.
3. Don't force-close the app from the recents screen while it plays. Battery savers and Android may still stop
   background playback; a web app cannot override the phone's system-level power or audio-focus controls.

## Output mode

* **Stream** (default on Android): sound goes through a media element, so Android keeps it running in the
  background and with the screen off, with a lock-screen player.
* **Direct** (default elsewhere): straight to the audio output. Cleanest, but Android may stop it as soon as the app
  leaves the foreground.
Switch under "Output" in the app; the choice is remembered.

## Files

index.html, styles.css, app.js        the app
noise-processor.js                    the noise generator (audio thread)
sw.js, manifest.webmanifest, icons/   offline + installability

When you change any file, bump `CACHE` in sw.js so installed copies pick up the update on next launch.
