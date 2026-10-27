/*
 * Deep Brown: sound mapping and the offline render of the background clip.
 *
 * Android suspends Web Audio (and MediaStreams fed from it) as soon as a PWA is
 * hidden or the screen turns off, but it keeps a real media file playing. So the
 * exact same AudioWorklet is rendered offline to a seamless WAV here; app.js
 * hands that file to the <audio> element for background playback.
 */
'use strict';

window.DeepBrownClip = (() => {
  const SHELF_HZ = 90, HP_HZ = 18, TARGET_RMS = 0.14;
  const CLIP_RATE = 24000;        // plenty for noise that rolls off below 900 Hz
  const CLIP_SECONDS = 90;        // loop length (the seam is crossfaded)
  const CLIP_FADE = 2;

  const cutoffHz   = (d) => 900 * Math.pow(40 / 900, d);   // 900 Hz (shallow) to 40 Hz (abyss)
  const shelfDb    = (r) => r * 12;
  const swellCents = (s) => s * 700;

  /* Loudness model. Computes the RMS of the filtered noise from the filters'
   * exact frequency responses, so the output level stays constant while depth
   * and rumble change (instead of getting quieter as you go deeper). */
  function coeffs(type, f0, qDb, gDb, fs) {
    const w0 = (2 * Math.PI * f0) / fs, cos = Math.cos(w0), sin = Math.sin(w0);
    let b0, b1, b2, a0, a1, a2;
    if (type === 'lowpass' || type === 'highpass') {
      const alpha = sin / (2 * Math.pow(10, qDb / 20));
      a0 = 1 + alpha; a1 = -2 * cos; a2 = 1 - alpha;
      if (type === 'lowpass') { b0 = (1 - cos) / 2; b1 = 1 - cos; b2 = b0; }
      else { b0 = (1 + cos) / 2; b1 = -(1 + cos); b2 = b0; }
    } else { // lowshelf, slope 1
      const A = Math.pow(10, gDb / 40);
      const k = 2 * Math.sqrt(A) * (sin / 2) * Math.SQRT2;
      b0 = A * ((A + 1) - (A - 1) * cos + k);
      b1 = 2 * A * ((A - 1) - (A + 1) * cos);
      b2 = A * ((A + 1) - (A - 1) * cos - k);
      a0 = (A + 1) + (A - 1) * cos + k;
      a1 = -2 * ((A - 1) + (A + 1) * cos);
      a2 = (A + 1) + (A - 1) * cos - k;
    }
    return [b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0];
  }
  function mag2(c, w) {
    const cw = Math.cos(w), sw = Math.sin(w), c2 = Math.cos(2 * w), s2 = Math.sin(2 * w);
    const nr = c[0] + c[1] * cw + c[2] * c2, ni = -(c[1] * sw + c[2] * s2);
    const dr = 1 + c[3] * cw + c[4] * c2, di = -(c[3] * sw + c[4] * s2);
    return (nr * nr + ni * ni) / (dr * dr + di * di);
  }
  const Q_BUTTER_DB = -3.0103;   // Web Audio lowpass/highpass Q is in dB
  const Q_LP2_DB = 1.5;
  function filterSet(cut, sDb, fs) {
    return [
      coeffs('highpass', HP_HZ, Q_BUTTER_DB, 0, fs),
      coeffs('lowpass', cut, Q_BUTTER_DB, 0, fs),
      coeffs('lowpass', cut, Q_LP2_DB, 0, fs),
      coeffs('lowshelf', SHELF_HZ, 0, sDb, fs),
    ];
  }
  function levelGain(cut, sDb, fs = 48000) {
    const set = filterSet(cut, sDb, fs);
    const a = Math.exp((-2 * Math.PI * 10) / fs);        // same leak as the worklet
    const N = 360, f0 = 0.1, f1 = fs / 2 * 0.999, dl = Math.log(f1 / f0) / (N - 1);
    let num = 0, den = 0;
    for (let i = 0; i < N; i++) {
      const f = f0 * Math.exp(i * dl), w = (2 * Math.PI * f) / fs;
      const psd = 1 / (1 - 2 * a * Math.cos(w) + a * a);
      let h = 1; for (const c of set) h *= mag2(c, w);
      num += psd * h * f; den += psd * f;
    }
    const rmsPre = 0.5 * Math.sqrt(num / den);
    return Math.min(14, Math.max(0.05, TARGET_RMS / rmsPre));
  }

  /* 16-bit mono/stereo WAV of the first CLIP_SECONDS of the render. The excess
   * CLIP_FADE seconds at the end are mixed into the beginning with an
   * equal-power crossfade, so looping the file is inaudible. */
  function wavFrom(buffer) {
    const L = Math.round(CLIP_RATE * CLIP_SECONDS);
    const X = Math.round(CLIP_RATE * CLIP_FADE);
    const chans = Math.min(2, buffer.numberOfChannels);
    const bytes = 44 + L * chans * 2;
    const ab = new ArrayBuffer(bytes), v = new DataView(ab);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, bytes - 8, true); w(8, 'WAVE'); w(12, 'fmt ');
    v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, chans, true);
    v.setUint32(24, CLIP_RATE, true); v.setUint32(28, CLIP_RATE * chans * 2, true);
    v.setUint16(32, chans * 2, true); v.setUint16(34, 16, true);
    w(36, 'data'); v.setUint32(40, L * chans * 2, true);

    const d0 = buffer.getChannelData(0);
    const d1 = chans > 1 ? buffer.getChannelData(1) : d0;
    let o = 44;
    for (let i = 0; i < L; i++) {
      for (let c = 0; c < chans; c++) {
        const d = c ? d1 : d0;
        let x;
        if (i < X) {
          const k = i / X;
          x = d[i] * Math.sqrt(k) + d[L + i] * Math.sqrt(1 - k);
        } else {
          x = d[i];
        }
        if (x > 1) x = 1; else if (x < -1) x = -1;
        v.setInt16(o, x < 0 ? x * 0x8000 : x * 0x7fff, true); o += 2;
      }
    }
    return URL.createObjectURL(new Blob([ab], { type: 'audio/wav' }));
  }

  async function renderWav(s) {
    const AC = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    if (!AC || !window.AudioWorkletNode) throw new Error('unsupported');
    const frames = Math.round(CLIP_RATE * (CLIP_SECONDS + CLIP_FADE));
    const off = new AC(2, frames, CLIP_RATE);
    try {
      await off.audioWorklet.addModule('noise-processor.js');
      const node = new AudioWorkletNode(off, 'brown-noise', { numberOfInputs: 0, outputChannelCount: [2] });
      const cut = cutoffHz(s.deep), sDb = shelfDb(s.rumble);
      const P = node.parameters;
      P.get('cutoff').value = cut;
      P.get('shelfDb').value = sDb;
      P.get('makeup').value = levelGain(cut, sDb, CLIP_RATE);
      P.get('width').value = s.width;
      P.get('swell').value = swellCents(s.swell);
      P.get('volume').value = 1;                    // the media element applies volume
      node.connect(off.destination);
      const buffer = await off.startRendering();
      return wavFrom(buffer);
    } finally {
      try { if (off.close) await off.close(); } catch (_) { /* not every browser */ }
    }
  }

  return { cutoffHz, shelfDb, swellCents, levelGain, renderWav, CLIP_SECONDS, CLIP_RATE };
})();
