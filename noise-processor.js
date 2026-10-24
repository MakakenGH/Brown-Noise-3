/*
 * Brown noise generator, runs on the audio rendering thread.
 *
 * Brown noise is white noise integrated once (-6 dB per octave). A pure
 * integrator random-walks away from zero, so this one is "leaky": below ~10 Hz
 * it stops integrating, which keeps the signal bounded without any audible
 * change. The gain is derived from the leak so the output level is a constant
 * ~0.5 RMS whatever the sample rate.
 *
 * Two independent noise channels are produced; "width" crossfades the right
 * channel from a copy of the left (mono) to fully independent (wide) while
 * keeping the power constant.
 *
 * Nothing is allocated inside process(): no garbage collection, no glitches.
 */
class BrownNoiseProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [{ name: 'width', defaultValue: 0.6, minValue: 0, maxValue: 1, automationRate: 'k-rate' }];
  }

  constructor() {
    super();
    // xorshift32 states, seeded differently for every instance and channel
    const seed = (Math.random() * 0xffffffff) >>> 0;
    this.a = ((seed ^ 0x9e3779b9) >>> 0) || 0x1234567;
    this.b = ((Math.imul(seed, 1664525) + 1013904223) ^ 0x85ebca6b) >>> 0 || 0x7654321;
    this.yL = 0;
    this.yR = 0;

    const leakCornerHz = 10;
    this.p = Math.exp((-2 * Math.PI * leakCornerHz) / sampleRate);
    const sigmaUniform = Math.sqrt(1 / 3); // std-dev of uniform noise in [-1, 1)
    this.g = (0.5 * Math.sqrt(1 - this.p * this.p)) / sigmaUniform;
  }

  process(inputs, outputs, parameters) {
    const out = outputs[0];
    const L = out[0];
    const R = out.length > 1 ? out[1] : null;
    const w = parameters.width[0];
    const sInd = w;
    const sCor = Math.sqrt(1 - w * w);
    const p = this.p;
    const g = this.g;
    let a = this.a | 0;
    let b = this.b | 0;
    let yL = this.yL;
    let yR = this.yR;

    for (let i = 0; i < L.length; i++) {
      a ^= a << 13; a ^= a >>> 17; a ^= a << 5;
      b ^= b << 13; b ^= b >>> 17; b ^= b << 5;
      yL = p * yL + g * ((a >>> 0) / 2147483648 - 1);
      yR = p * yR + g * ((b >>> 0) / 2147483648 - 1);
      L[i] = yL;
      if (R) R[i] = sCor * yL + sInd * yR;
    }

    this.a = a;
    this.b = b;
    this.yL = yL;
    this.yR = yR;
    return true; // never stop
  }
}

registerProcessor('brown-noise', BrownNoiseProcessor);
