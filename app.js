/* Deep Brown: generator UI, audio engine and background-playback handling. */
'use strict';

(() => {
  const STORE_KEY = 'deepbrown:v1';
  const HINT_KEY = 'deepbrown:hint-seen';
  const $ = (s) => document.querySelector(s);
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  // Media and worklet promises can occasionally never settle on Android (a
  // stalled MediaStream, a hung fetch). Without a deadline that leaves the
  // engine stuck in its "busy" state and the watchdog turned off for good.
  const withTimeout = (p, ms) => new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms);
    Promise.resolve(p).then(
      (v) => { clearTimeout(t); resolve(v); },
      (e) => { clearTimeout(t); reject(e); }
    );
  });

  /* ------------------------------------------------------------------ *
   * Presets and state
   * ------------------------------------------------------------------ */
  const PRESETS = [
    { id: 'velvet',      name: 'Velvet',      deep: 0.50, rumble: 0.35, width: 0.60, swell: 0.10 },
    { id: 'deep-sleep',  name: 'Deep sleep',  deep: 0.78, rumble: 0.50, width: 0.70, swell: 0.15 },
    { id: 'abyss',       name: 'Abyss',       deep: 0.97, rumble: 0.75, width: 0.45, swell: 0.05 },
    { id: 'engine-room', name: 'Engine room', deep: 0.30, rumble: 0.60, width: 0.30, swell: 0.00 },
    { id: 'tide',        name: 'Tide',        deep: 0.62, rumble: 0.40, width: 0.90, swell: 0.65 },
    { id: 'focus',       name: 'Focus',       deep: 0.20, rumble: 0.15, width: 0.55, swell: 0.00 },
  ];
  const DEFAULTS = { deep: 0.50, rumble: 0.35, width: 0.60, swell: 0.10, volume: 0.55 };
  const KEYS = Object.keys(DEFAULTS);

  function loadState() {
    const s = { ...DEFAULTS };
    try {
      const o = JSON.parse(localStorage.getItem(STORE_KEY) || '{}');
      for (const k of KEYS) if (typeof o[k] === 'number' && isFinite(o[k])) s[k] = clamp(o[k]);
    } catch (_) { /* private mode etc. */ }
    return s;
  }
  const S = loadState();
  let saveTimer = 0;
  const save = () => {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => { try { localStorage.setItem(STORE_KEY, JSON.stringify(S)); } catch (_) {} }, 300);
  };

  /* ------------------------------------------------------------------ *
   * Sound mapping (shared with the offline renderer in noise-clip.js)
   * ------------------------------------------------------------------ */
  const { cutoffHz, shelfDb, swellCents, levelGain } = window.DeepBrownClip;
  const volumeGain = (v) => v * v;                          // audio taper
  const FADE_MS = 30000;

  /* ------------------------------------------------------------------ *
   * Audio engine
   *
   * Foreground: the worklet (generator, filters, limiter on the audio thread)
   *   -> gate (play/pause fade) -> sleep fade -> output. Live and immediate.
   *
   * Background: Android suspends Web Audio, and MediaStreams fed from it, as
   * soon as the PWA is hidden or the screen turns off. It does keep a real
   * media file playing. So the same worklet is rendered offline to a seamless
   * WAV (noise-clip.js) and handed to the <audio> element the moment the app
   * goes to the background; that file is what keeps playing with the screen
   * off, and it is also what the lock-screen player drives.
   * ------------------------------------------------------------------ */
  class Engine {
    constructor() {
      this.ctx = null; this.initP = null; this.busy = false; this.token = 0;
      this.el = $('#sink');
      this.clip = null;              // { key, url } rendered background file
      this.clipP = null;             // render in flight
      this.clipTimer = 0;
      this.bgP = null;               // background hand-off in flight
      this.bg = false;               // the file is the current output
      this.bgSleep = 0;
      this.bgFade = 0;
      this.bgFadeTimer = 0;
      this.fadeTimer = 0;
    }

    init() {
      if (!this.initP) this.initP = this._init().catch((e) => { this.initP = null; throw e; });
      return this.initP;
    }

    async _init() {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC || !window.AudioWorkletNode) throw new Error('unsupported');
      const ctx = new AC({ latencyHint: 'playback' });
      try { await withTimeout(ctx.audioWorklet.addModule('noise-processor.js'), 15000); } catch (e) { ctx.close().catch(() => {}); throw e; }

      const node = new AudioWorkletNode(ctx, 'brown-noise', { numberOfInputs: 0, outputChannelCount: [2] });
      const gate = new GainNode(ctx, { gain: 0 });
      const sleepG = new GainNode(ctx, { gain: 1 });
      const analyser = new AnalyserNode(ctx, { fftSize: 2048, smoothingTimeConstant: 0 });
      node.connect(gate).connect(sleepG).connect(ctx.destination);
      sleepG.connect(analyser);                          // tap for the scope only

      Object.assign(this, { ctx, node, gate, sleepG, analyser });
      this.apply(S);
      ctx.addEventListener('statechange', () => app.onContextState());
    }

    apply(s) {
      if (this.bg) this.el.volume = volumeGain(s.volume);
      if (!this.ctx) return;
      const t = this.ctx.currentTime, P = this.node.parameters;
      const cut = cutoffHz(s.deep), sDb = shelfDb(s.rumble);
      P.get('cutoff').setValueAtTime(cut, t);
      P.get('shelfDb').setValueAtTime(sDb, t);
      P.get('makeup').setValueAtTime(levelGain(cut, sDb, this.ctx.sampleRate), t);
      P.get('width').setValueAtTime(s.width, t);
      P.get('swell').setValueAtTime(swellCents(s.swell), t);
      P.get('volume').setValueAtTime(volumeGain(s.volume), t);   // smoothed per sample inside the worklet
    }

    /* ---- background file ------------------------------------------- */
    clipKey(s) { return [s.deep, s.rumble, s.width, s.swell].map((v) => v.toFixed(3)).join('|'); }

    prepareClip() {
      const key = this.clipKey(S);
      if (this.clip && this.clip.key === key) return Promise.resolve(this.clip);
      if (this.clipP && this.clipP.key === key) return this.clipP.promise;
      const snap = { deep: S.deep, rumble: S.rumble, width: S.width, swell: S.swell };
      const entry = { key };
      entry.promise = window.DeepBrownClip.renderWav(snap).then((url) => {
        const old = this.clip;
        this.clip = { key, url };
        if (!this.bg) this.loadElement(this.clip);       // warm it up for a fast hand-off
        if (old && old.url && old.url !== url && this.el.src !== old.url) URL.revokeObjectURL(old.url);
        if (this.clipP === entry) this.clipP = null;
        return this.clip;
      }, (e) => {
        if (this.clipP === entry) this.clipP = null;
        throw e;
      });
      entry.promise.catch(() => {});                     // a caller may have timed out and walked away
      this.clipP = entry;
      return entry.promise;
    }

    /* Re-render the background file shortly after the sound changes, so that
     * hiding the app is instant instead of waiting for a render. */
    queueClip() {
      clearTimeout(this.clipTimer); this.clipTimer = 0;
      if (!app.wanted || document.hidden) return;
      if (this.clip && this.clip.key === this.clipKey(S)) return;
      this.clipTimer = setTimeout(() => {
        this.clipTimer = 0;
        if (!app.wanted || document.hidden) return;
        this.prepareClip().catch((e) => console.warn('Deep Brown: could not render the background clip', e));
      }, 700);
    }

    loadElement(clip) {
      if (this.el.src === clip.url) return;
      this.el.src = clip.url;
      try { this.el.load(); } catch (_) {}
    }

    async _startBackground() {
      if (!app.wanted || !document.hidden) return false;
      const key = this.clipKey(S);
      let clip = this.clip;
      if (!clip) {
        try { clip = await withTimeout(this.prepareClip(), 3000); }
        catch (e) { clip = this.clip; }                   // a slightly older file beats silence
      }
      if (!clip) return false;
      if (!app.wanted || !document.hidden) return false;

      const el = this.el;
      this.bg = true;
      this.stopElementFade();
      el.loop = true;
      el.volume = volumeGain(S.volume);
      const prev = el.src;
      this.loadElement(clip);
      if (prev.startsWith('blob:') && prev !== clip.url) URL.revokeObjectURL(prev);
      const dur = el.duration;
      if (Number.isFinite(dur) && dur > 0) el.currentTime = Math.random() * dur;
      try { await withTimeout(el.play(), 6000); }
      catch (e) {
        console.warn('Deep Brown: the background media element did not start', e);
        this.bg = false;
        return false;
      }
      if (!app.wanted || !document.hidden) {   // paused or shown while play() was pending
        this.bg = false;
        try { el.pause(); } catch (_) {}
        if (app.wanted) this.enterForeground().catch(() => {});
        return false;
      }
      this.muteForeground(0.2);
      this.armBgSleep();
      app.updateMediaSession();
      // The current sound changed a moment ago: start with the file at hand,
      // then swap in the correct one as soon as it has rendered.
      if (clip.key !== key) {
        this.prepareClip().then(() => this.refreshBackgroundClip()).catch(() => {});
      }
      return true;
    }

    startBackground() {
      if (this.bg && this.el && !this.el.paused) return Promise.resolve(true);
      if (this.bgP) return this.bgP;
      this.bgP = this._startBackground().finally(() => { this.bgP = null; });
      return this.bgP;
    }

    /* A preset from the lock screen can change the sound while the app is
     * hidden. Render the new file and swap it in; playback resumes as soon as
     * the new blob is ready, without waiting for JS timers. */
    async refreshBackgroundClip() {
      if (!this.bg) return;
      let clip;
      try { clip = await this.prepareClip(); } catch (_) { return; }
      const el = this.el;
      if (!this.bg || el.src === clip.url) return;
      const old = el.src;
      this.loadElement(clip);
      try {
        el.volume = volumeGain(S.volume);
        await el.play();
      } catch (_) { /* recover() restarts playback */ }
      if (old.startsWith('blob:') && old !== clip.url) URL.revokeObjectURL(old);
      this.armBgSleep();
      app.updateMediaSession();
    }

    /* The file starts in the same instant; the live engine fades out under it.
     * Suspending the context a moment later keeps the phone from running the
     * worklet for a sound nobody hears. */
    muteForeground(sec) {
      const ctx = this.ctx;
      if (!ctx || ctx.state === 'closed') return;
      const t = ctx.currentTime, g = this.gate.gain;
      g.cancelScheduledValues(t);
      g.setValueAtTime(g.value, t);
      g.linearRampToValueAtTime(0, t + sec);
      setTimeout(() => {
        if (this.bg && this.ctx === ctx && ctx.state === 'running') ctx.suspend().catch(() => {});
      }, 1400);
    }

    armBgSleep() {
      this.clearBgSleep();
      const rem = app.timerEnd - Date.now();
      if (!app.timerEnd || rem <= 0) return;
      const fadeFrom = () => {
        if (!this.bg) return;
        const t0 = Date.now(), from = this.el.volume, fadeMs = Math.min(FADE_MS, rem);
        this.bgFade = setInterval(() => {
          if (!this.bg) { this.clearBgSleep(); return; }
          const k = Math.min(1, (Date.now() - t0) / fadeMs);
          this.el.volume = Math.max(0, from * (1 - k));
          if (k >= 1) { clearInterval(this.bgFade); this.bgFade = 0; }
        }, 1000);                                  // background timers are throttled anyway
      };
      if (rem > FADE_MS) this.bgFadeTimer = setTimeout(fadeFrom, rem - FADE_MS);
      else fadeFrom();
      this.bgSleep = setTimeout(() => {
        this.clearBgSleep();
        if (this.bg && app.wanted) app.pause();
      }, rem);
    }

    clearBgSleep() {
      clearTimeout(this.bgSleep); this.bgSleep = 0;
      clearTimeout(this.bgFadeTimer); this.bgFadeTimer = 0;
      clearInterval(this.bgFade); this.bgFade = 0;
    }

    stopElementFade() {
      clearInterval(this.fadeTimer); this.fadeTimer = 0;
    }

    /* Fade the file out before the live engine takes over again. */
    fadeElementOut() {
      this.stopElementFade();
      const el = this.el, target = volumeGain(S.volume);
      if (el.paused) { el.volume = target; return; }
      const from = el.volume || target, t0 = performance.now();
      this.fadeTimer = setInterval(() => {
        const k = Math.min(1, (performance.now() - t0) / 250);
        el.volume = Math.max(0, from * (1 - k));
        if (k >= 1) {
          this.stopElementFade();
          try { el.pause(); } catch (_) {}
          el.volume = target;
        }
      }, 25);
    }

    /* ---- foreground engine ----------------------------------------- */
    stopElement() {
      if (this.fadeTimer) return;                        // already on its way out
      const el = this.el;
      if (!el.paused) { try { el.pause(); } catch (_) {} }
      el.volume = volumeGain(S.volume);
    }

    async startForeground(token) {
      if (this.ctx && this.ctx.state === 'closed') await this.teardown();
      await this.init();
      if (token !== undefined && token !== this.token) return;
      const ctx = this.ctx;
      this.bg = false;
      this.clearBgSleep();
      this.stopElement();
      this.apply(S);
      if (ctx.state !== 'running') await withTimeout(ctx.resume(), 8000);
      if (token !== undefined && token !== this.token) return;   // paused while resuming
      const t = ctx.currentTime, g = this.gate.gain;
      g.cancelScheduledValues(t);
      g.setValueAtTime(0, t);
      g.linearRampToValueAtTime(1, t + 0.7);             // fade in: no click
      if (this.clip && this.clip.key === this.clipKey(S)) return;   // already warm
      this.prepareClip().catch((e) => console.warn('Deep Brown: could not render the background clip', e));
    }

    async start() {
      if (this.ctx && this.ctx.state === 'closed') await this.teardown();
      const token = ++this.token;
      this.busy = true;
      try {
        if (document.hidden && await this.startBackground()) return;
        await this.startForeground(token);
      } finally { this.busy = false; }
    }

    async enterForeground() {
      if (!app.wanted) { this.bg = false; this.stopElementFade(); this.stopElement(); return; }
      this.bg = false;
      this.clearBgSleep();
      this.fadeElementOut();
      await this.startForeground();                      // resumes or rebuilds the live engine
      app.scheduleTimer();
    }

    async pause() {
      this.bg = false;
      this.clearBgSleep();
      this.stopElementFade();
      const el = this.el;
      try { el.pause(); } catch (_) {}
      el.volume = volumeGain(S.volume);
      if (!this.ctx) { this.resetSleepFade(); return; }
      const token = ++this.token;
      const { ctx, gate } = this;
      const t = ctx.currentTime;
      gate.gain.cancelScheduledValues(t);
      gate.gain.setValueAtTime(gate.gain.value || 0, t);
      gate.gain.linearRampToValueAtTime(0, t + 0.25);
      await sleep(320);
      if (token !== this.token || ctx !== this.ctx) return;   // user pressed play again / engine rebuilt
      try { await ctx.suspend(); } catch (_) {}
      this.resetSleepFade();
    }

    async teardown() {
      const ctx = this.ctx;
      this.token++;
      this.ctx = null; this.initP = null;
      this.node = this.gate = this.sleepG = this.analyser = null;
      this.bg = false;
      this.clearBgSleep();
      this.stopElementFade();
      try { this.el.pause(); } catch (_) {}
      this.el.removeAttribute('src');
      try { this.el.load(); } catch (_) {}       // drop the old media session / source
      if (ctx) { try { await ctx.close(); } catch (_) {} }
    }

    /* Schedules the whole sleep timer on the audio thread, so it still fades
     * and stops when Android throttles or freezes the page's JavaScript. */
    scheduleSleepStop(remainingMs) {
      if (!this.ctx) return;
      const t = this.ctx.currentTime, g = this.sleepG.gain;
      const rem = Math.max(0, remainingMs) / 1000, fade = FADE_MS / 1000;
      g.cancelScheduledValues(t);
      g.setValueAtTime(1, t);
      if (rem > fade) g.setValueAtTime(1, t + rem - fade);
      g.linearRampToValueAtTime(0, t + rem);
    }
    resetSleepFade() {
      if (!this.ctx) return;
      const t = this.ctx.currentTime, g = this.sleepG.gain;
      g.cancelScheduledValues(t);
      g.setValueAtTime(1, t);
    }
  }
  const engine = new Engine();

  /* ------------------------------------------------------------------ *
   * Scope: the live waveform IS the depth line
   * ------------------------------------------------------------------ */
  const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const Y0 = 0.32, YSPAN = 0.52;               // depth line travels between 32% and 84% of the field
  const yOf = (d) => Y0 + YSPAN * d;

  class Scope {
    constructor(canvas) {
      this.c = canvas; this.g = canvas.getContext('2d');
      this.N = 72; this.pts = new Float32Array(this.N); this.hist = [];
      this.buf = new Float32Array(2048); this.peak = 0.05; this.lastHist = 0; this.last = 0; this.raf = 0; this.t0 = performance.now();
      this.resize();
      new ResizeObserver(() => { this.resize(); this.draw(); }).observe(canvas);
    }
    resize() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      this.w = this.c.clientWidth; this.h = this.c.clientHeight;
      this.c.width = Math.max(1, Math.round(this.w * dpr)); this.c.height = Math.max(1, Math.round(this.h * dpr));
      this.g.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    run() { if (!this.raf && !reduceMotion) this.raf = requestAnimationFrame((t) => this.tick(t)); }
    halt() { if (this.raf) cancelAnimationFrame(this.raf); this.raf = 0; this.draw(); }
    tick(ts) {
      this.raf = requestAnimationFrame((t) => this.tick(t));
      if (ts - this.last < 33) return;                   // ~30 fps is plenty and kind to batteries
      this.last = ts; this.draw(ts);
    }
    sample(ts) {
      const N = this.N, live = app.wanted && engine.analyser && engine.ctx.state === 'running';
      if (live) {
        engine.analyser.getFloatTimeDomainData(this.buf);
        const step = this.buf.length / N; let mx = 0;
        const next = new Float32Array(N);
        for (let i = 0; i < N; i++) {
          let s = 0; for (let j = 0; j < step; j++) s += this.buf[(i * step + j) | 0];
          next[i] = s / step; mx = Math.max(mx, Math.abs(next[i]));
        }
        this.peak = Math.max(this.peak * 0.995, mx, 0.02);
        for (let i = 0; i < N; i++) this.pts[i] = lerp(this.pts[i], clamp(next[i] / this.peak, -1, 1), 0.55);
      } else {
        const t = (ts || this.t0) / 1000;
        for (let i = 0; i < N; i++) {
          const x = i / N;
          this.pts[i] = lerp(this.pts[i], 0.18 * Math.sin(x * 6.28 * 1.3 + t * 0.4) + 0.08 * Math.sin(x * 6.28 * 3.1 - t * 0.25), 0.1);
        }
      }
    }
    path(g, y0, amp, dy = 0) {
      const { N, pts, w } = this, dx = w / (N - 1);
      g.beginPath(); g.moveTo(0, y0 + dy + pts[0] * amp);
      for (let i = 1; i < N; i++) {
        const x = i * dx, px = (i - 1) * dx, mx = (x + px) / 2, my = y0 + dy + ((pts[i] + pts[i - 1]) / 2) * amp;
        g.quadraticCurveTo(px, y0 + dy + pts[i - 1] * amp, mx, my);
      }
      g.lineTo(w, y0 + dy + pts[N - 1] * amp);
    }
    draw(ts) {
      const g = this.g, { w, h } = this;
      if (!w || !h) return;
      g.clearRect(0, 0, w, h);
      this.sample(ts);
      const y0 = yOf(S.deep) * h, amp = h * 0.07;

      if (ts && ts - this.lastHist > 380) {              // ghost layers: recent past, drifting down
        this.hist.unshift(Float32Array.from(this.pts)); this.hist.length = Math.min(this.hist.length, 3); this.lastHist = ts;
      }
      // below the line: the dark
      this.path(g, y0, amp); g.lineTo(w, h); g.lineTo(0, h); g.closePath();
      g.fillStyle = 'rgba(6,4,3,0.42)'; g.fill();

      const live = this.pts; g.lineJoin = 'round'; g.lineCap = 'round';
      for (let k = this.hist.length - 1; k >= 0; k--) {
        this.pts = this.hist[k];
        this.path(g, y0, amp, (k + 1) * 9);
        g.strokeStyle = `rgba(240,226,207,${0.20 - k * 0.055})`; g.lineWidth = 1.5; g.stroke();
      }
      this.pts = live;
      this.path(g, y0, amp);
      g.strokeStyle = '#86d1c0'; g.lineWidth = 2.5; g.stroke();

      // scale on the right edge, with the handle at the current depth
      g.font = '12px system-ui, sans-serif'; g.textAlign = 'right'; g.textBaseline = 'middle';
      for (const p of [0, 0.25, 0.5, 0.75, 1]) {
        const y = yOf(p) * h; g.fillStyle = 'rgba(240,226,207,0.38)'; g.fillRect(w - 20, y - 0.5, 10, 1);
      }
      g.fillStyle = 'rgba(240,226,207,0.62)';
      g.fillText('Shallow', w - 28, yOf(0) * h); g.fillText('Deep', w - 28, yOf(1) * h);
      g.fillStyle = '#f0e2cf'; g.beginPath();
      if (g.roundRect) g.roundRect(w - 30, yOf(S.deep) * h - 2, 22, 4, 2); else g.rect(w - 30, yOf(S.deep) * h - 2, 22, 4);
      g.fill();
    }
  }
  const scope = new Scope($('#scope'));

  /* ------------------------------------------------------------------ *
   * App: state, rendering, playback control
   * ------------------------------------------------------------------ */
  const el = {
    field: $('#field'), deepNum: $('#deepNum'), deepHz: $('#deepHz'), hint: $('#hint'),
    play: $('#play'), playIcon: $('#playIcon'), status: $('#status'),
    presets: $('#presets'), timerBtns: $('#timerBtns'), timerLeft: $('#timerLeft'), install: $('#install'),
  };
  const PLAY_PATH = 'M8 5v14l11-7z';
  const PAUSE_PATH = 'M7 5h4v14H7zM13 5h4v14h-4z';
  const SLIDERS = ['rumble', 'width', 'swell', 'volume'];
  const mix = (a, b, t) => a.map((v, i) => Math.round(lerp(v, b[i], t)));
  const rgb = (c) => `rgb(${c[0]},${c[1]},${c[2]})`;

  const app = {
    wanted: false,
    lockRelease: null,
    holdLock() {
      if (this.lockRelease || !navigator.locks) return;
      navigator.locks.request('deepbrown-playing', () => new Promise((res) => { this.lockRelease = res; })).catch(() => {});
    },
    releaseLock() { if (this.lockRelease) { this.lockRelease(); this.lockRelease = null; } },
    timerEnd: 0,
    timerMin: 0,
    errorText: '',
    failures: 0,
    nextAttempt: 0,
    retryTimer: 0,
    pauseRetries: 0,
    recovering: false,
    mediaMeta: null,

    /* Android hides the page both when the PWA is minimized and when the
     * screen turns off. Web Audio does not survive that, so a file rendered
     * from the same sound takes over; coming back hands the output to the
     * live engine again. */
    handleVisibilityChange() {
      if (document.hidden) {
        if (this.wanted) engine.startBackground().catch((e) => console.warn('Deep Brown: background playback did not start', e));
        return;
      }
      this.cancelRetry();
      this.nextAttempt = 0;           // coming back to the foreground is a good moment to retry
      engine.enterForeground().catch((e) => this.noteFailure(e));
      this.renderPlay();
      this.renderTimer();
      scope.draw(performance.now());
    },

    presetId() {
      const p = PRESETS.find((p) => ['deep', 'rumble', 'width', 'swell'].every((k) => Math.abs(S[k] - p[k]) < 0.006));
      return p ? p.id : null;
    },
    presetName() { const p = PRESETS.find((p) => p.id === this.presetId()); return p ? p.name : 'Custom'; },

    set(key, v) {
      S[key] = clamp(v);
      engine.apply(S);
      engine.queueClip();                     // keep the background file in step
      this.render(); save();
      if (key === 'deep') this.dismissHint();
      this.updateMediaSession();
    },
    applyPreset(id) {
      const p = PRESETS.find((p) => p.id === id); if (!p) return;
      for (const k of ['deep', 'rumble', 'width', 'swell']) S[k] = p[k];
      engine.apply(S); engine.queueClip(); this.render(); save(); this.updateMediaSession();
      if (engine.bg) engine.refreshBackgroundClip().catch(() => {});
    },
    stepPreset(dir) {
      const i = PRESETS.findIndex((p) => p.id === this.presetId());
      const n = i < 0 ? (dir > 0 ? 0 : PRESETS.length - 1) : (i + dir + PRESETS.length) % PRESETS.length;
      this.applyPreset(PRESETS[n].id);
    },
    dismissHint() { if (!el.hint.classList.contains('gone')) { el.hint.classList.add('gone'); try { localStorage.setItem(HINT_KEY, '1'); } catch (_) {} } },

    /* rendering */
    render() {
      const d = S.deep, cut = cutoffHz(d);
      el.field.style.setProperty('--top', rgb(mix([122, 77, 44], [38, 24, 16], d)));
      el.field.style.setProperty('--bottom', rgb(mix([32, 20, 13], [7, 4, 3], d)));
      el.deepNum.textContent = Math.round(d * 100);
      el.deepHz.textContent = `Rolls off above ${Math.round(cut / 5) * 5} Hz`;
      el.field.setAttribute('aria-valuenow', Math.round(d * 100));
      el.field.setAttribute('aria-valuetext', `${Math.round(d * 100)} percent, rolls off above ${Math.round(cut)} hertz`);
      for (const k of SLIDERS) {
        const input = $('#' + k), pct = Math.round(S[k] * 100);
        input.value = pct; input.style.setProperty('--p', pct + '%'); $('#' + k + 'Out').textContent = pct + '%';
      }
      const cur = this.presetId();
      for (const b of el.presets.children) b.setAttribute('aria-pressed', String(b.dataset.id === cur));
      this.renderPlay();
      scope.draw(performance.now());
    },
    renderPlay() {
      el.playIcon.firstElementChild.setAttribute('d', this.wanted ? PAUSE_PATH : PLAY_PATH);
      el.play.setAttribute('aria-label', this.wanted ? 'Pause' : 'Play');
      el.status.classList.toggle('err', !!this.errorText);
      el.status.textContent = this.errorText || (this.wanted ? `Playing ${this.presetName()}` : 'Paused');
    },
    renderTimer() {
      for (const b of el.timerBtns.children) b.setAttribute('aria-pressed', String(Number(b.dataset.min) === this.timerMin));
      if (!this.timerEnd) { el.timerLeft.textContent = ''; return; }
      const rem = Math.max(0, this.timerEnd - Date.now()), m = Math.floor(rem / 60000), s = Math.floor((rem % 60000) / 1000);
      el.timerLeft.textContent = rem >= 120000 ? `Stops in ${Math.ceil(rem / 60000)} min` : `Stops in ${m}:${String(s).padStart(2, '0')}`;
    },

    /* playback */
    play() {
      if (engine.busy) return;
      // "wanted" can be true while playback is actually dead (a failed
      // background recovery). Only skip when the engine really is running.
      const running = engine.bg ? (engine.el && !engine.el.paused)
                                : (engine.ctx && engine.ctx.state === 'running');
      if (this.wanted && running) return;
      this.wanted = true; this.errorText = ''; this.failures = 0; this.nextAttempt = 0;
      this.cancelRetry(); this.holdLock(); this.renderPlay(); scope.run();
      this.updateMediaSession();
      this.startEngine();
    },
    async startEngine() {
      try { await engine.start(); this.scheduleTimer(); }
      catch (e) {
        this.wanted = false; this.releaseLock();
        this.errorText = e && e.message === 'unsupported'
          ? "This browser can't run the audio engine. Try Chrome." : 'Audio could not start. Tap play to try again.';
        this.renderPlay(); scope.halt(); this.updateMediaSession();
      }
    },
    pause() {
      if (!this.wanted) return;
      this.wanted = false; this.releaseLock(); this.clearTimer();
      this.cancelRetry(); this.failures = 0; this.nextAttempt = 0;
      this.renderPlay(); scope.halt();
      engine.pause(); this.updateMediaSession();
    },
    toggle() { this.wanted ? this.pause() : this.play(); },

    /* keep alive */
    cancelRetry() {
      if (this.retryTimer) { clearTimeout(this.retryTimer); this.retryTimer = 0; }
      this.pauseRetries = 0;
    },
    giveUp() {
      this.wanted = false;
      this.releaseLock();
      this.cancelRetry();
      this.errorText = 'Playback stopped. Tap play to restart.';
      this.renderPlay();
      this.updateMediaSession();
      scope.halt();
      engine.pause();
    },
    noteFailure(e) {
      console.warn('Deep Brown: playback did not recover', e);
      const running = engine.bg ? (engine.el && !engine.el.paused)
                                : (engine.ctx && engine.ctx.state === 'running');
      if (running) { this.failures = 0; this.nextAttempt = 0; return; }   // it came back on its own
      this.failures++;
      this.nextAttempt = Date.now() + Math.min(30000, 1000 * Math.pow(2, this.failures));
      if (this.failures >= 5) this.giveUp();               // stop fighting the system forever
    },
    async recover() {
      if (!this.wanted || engine.busy || this.recovering) return;
      if (Date.now() < this.nextAttempt) return;
      this.recovering = true;
      try {
        if (document.hidden) {
          if (!engine.bg || engine.el.paused) {
            if (!(await engine.startBackground())) throw new Error('the background file is not playing');
          }
        } else if (engine.bg || !engine.ctx || engine.ctx.state !== 'running') {
          await engine.enterForeground();
        }
        this.failures = 0; this.nextAttempt = 0;
        if (this.errorText) { this.errorText = ''; this.renderPlay(); }
      } catch (e) {
        this.noteFailure(e);
      } finally { this.recovering = false; }
    },
    onContextState() {                 // a hidden context is expected to sleep while the file plays
      if (!document.hidden && this.wanted && engine.ctx && engine.ctx.state !== 'running') this.recover();
    },
    onElementPause() {          // Android pauses the file for audio focus changes, calls and Bluetooth swaps
      if (!this.wanted || engine.busy || !engine.bg) return;
      if (this.retryTimer) return;
      if (this.pauseRetries >= 6) { this.giveUp(); return; }   // the system keeps stopping us
      const delay = Math.min(8000, 500 * Math.pow(2, this.pauseRetries));
      this.retryTimer = setTimeout(() => {
        this.retryTimer = 0;
        if (!this.wanted) return;
        this.pauseRetries++;                   // back off, don't fight audio focus
        this.recover();
      }, delay);
    },

    /* sleep timer */
    scheduleTimer() {
      const rem = this.timerEnd - Date.now();
      if (!this.timerEnd || rem <= 0) { this.clearTimer(); engine.resetSleepFade(); return; }
      engine.scheduleSleepStop(rem);           // exact fade while the live engine plays
      engine.armBgSleep();                     // and a deadline for the background file
    },
    setTimer(min) {
      this.timerMin = min; this.timerEnd = min ? Date.now() + min * 60000 : 0;
      this.scheduleTimer(); this.renderTimer();
    },
    clearTimer() { this.timerMin = 0; this.timerEnd = 0; this.renderTimer(); },   // engine.pause() resets the fade after the output is silent
    tick() {
      // Foreground bookkeeping only. The fade itself is scheduled on the audio
      // thread, so a throttled or frozen timer cannot cut it short.
      if (this.timerEnd) {
        if (this.timerEnd - Date.now() <= 0) { this.pause(); return; }
        this.renderTimer();
      }
      this.recover();                                               // watchdog
    },

    /* lock-screen / notification player */
    updateMediaSession() {
      if (!('mediaSession' in navigator)) return;
      const ms = navigator.mediaSession;
      const name = this.presetName();
      try {
        if (!this.mediaMeta || this.mediaMeta.artist !== name) {   // metadata only when it changes
          this.mediaMeta = new MediaMetadata({
            title: 'Deep brown noise', artist: name, album: 'Deep Brown',
            artwork: [{ src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' }],
          });
          ms.metadata = this.mediaMeta;
        }
        ms.playbackState = this.wanted ? 'playing' : 'paused';
      } catch (_) {}
    },
    initMediaSession() {
      if (!('mediaSession' in navigator)) return;
      const h = (a, f) => { try { navigator.mediaSession.setActionHandler(a, f); } catch (_) {} };
      h('play', () => this.play()); h('pause', () => this.pause()); h('stop', () => this.pause());
      h('previoustrack', () => this.stepPreset(-1)); h('nexttrack', () => this.stepPreset(1));
    },
  };

  /* ------------------------------------------------------------------ *
   * Wiring
   * ------------------------------------------------------------------ */
  // presets
  for (const p of PRESETS) {
    const b = document.createElement('button');
    b.className = 'chip'; b.type = 'button'; b.dataset.id = p.id; b.textContent = p.name;
    b.addEventListener('click', () => app.applyPreset(p.id));
    el.presets.appendChild(b);
  }
  // timer
  for (const [min, label] of [[0, 'Off'], [15, '15 min'], [30, '30 min'], [60, '60 min'], [90, '90 min']]) {
    const b = document.createElement('button');
    b.type = 'button'; b.dataset.min = min; b.textContent = label;
    b.addEventListener('click', () => app.setTimer(min));
    el.timerBtns.appendChild(b);
  }
  // output route no longer exists: the foreground uses Web Audio, the background a rendered file
  // sliders
  for (const k of SLIDERS) $('#' + k).addEventListener('input', (e) => app.set(k, e.target.value / 100));
  // transport
  el.play.addEventListener('click', () => app.toggle());
  $('#prev').addEventListener('click', () => app.stepPreset(-1));
  $('#next').addEventListener('click', () => app.stepPreset(1));

  // depth field: the line follows the finger 1:1
  let drag = null;
  el.field.addEventListener('pointerdown', (e) => {
    el.field.setPointerCapture(e.pointerId);
    drag = { y: e.clientY, v: S.deep, h: el.field.clientHeight };
  });
  el.field.addEventListener('pointermove', (e) => {
    if (drag) app.set('deep', drag.v + (e.clientY - drag.y) / (drag.h * YSPAN));
  });
  for (const ev of ['pointerup', 'pointercancel']) el.field.addEventListener(ev, () => { drag = null; });
  el.field.addEventListener('wheel', (e) => { e.preventDefault(); app.set('deep', S.deep + e.deltaY * 0.0006); }, { passive: false });
  el.field.addEventListener('keydown', (e) => {
    const big = e.shiftKey ? 0.05 : 0.01;
    const map = { ArrowDown: big, ArrowRight: big, ArrowUp: -big, ArrowLeft: -big, PageDown: 0.1, PageUp: -0.1 };
    if (e.key in map) { e.preventDefault(); app.set('deep', S.deep + map[e.key]); }
    else if (e.key === 'Home') { e.preventDefault(); app.set('deep', 0); }
    else if (e.key === 'End') { e.preventDefault(); app.set('deep', 1); }
  });

  // keep playing: recover when the page comes back, and when the system or browser interferes
  document.addEventListener('visibilitychange', () => app.handleVisibilityChange());
  document.addEventListener('resume', () => app.handleVisibilityChange());   // page was frozen and thawed
  window.addEventListener('pageshow', () => app.handleVisibilityChange());
  $('#sink').addEventListener('pause', () => app.onElementPause());
  $('#sink').addEventListener('error', () => app.recover());
  setInterval(() => app.tick(), 1000);

  // install prompt
  let deferred = null;
  window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); deferred = e; el.install.hidden = false; });
  el.install.addEventListener('click', async () => {
    if (!deferred) return; deferred.prompt(); await deferred.userChoice.catch(() => {}); deferred = null; el.install.hidden = true;
  });
  window.addEventListener('appinstalled', () => { el.install.hidden = true; });

  // offline shell
  if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));

  try { if (localStorage.getItem(HINT_KEY)) el.hint.classList.add('gone'); } catch (_) {}
  app.initMediaSession();
  app.render(); app.renderTimer();
})();
