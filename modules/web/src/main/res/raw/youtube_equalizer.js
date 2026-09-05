(function() {
  if (window.FermataEqualizer) return;

  const BAND_FREQS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
  const BASS_FREQ = 200;
  const VIRT_MAX_DELAY = 0.03;

  const state = {
    config: {
      eqEnabled: false,
      bands: BAND_FREQS.map(() => 0),
      bassEnabled: false,
      bassGain: 0,
      virtEnabled: false,
      virtStrength: 0,
      reverbEnabled: false,
      reverbStrength: 0
    },
    ctx: null,
    chains: new WeakMap(),
    impulse: null
  };

  function getContext() {
    if (!state.ctx) {
      const Ctor = window.AudioContext || window.webkitAudioContext;
      state.ctx = new Ctor();
    }
    if (state.ctx.state === 'suspended') state.ctx.resume().catch(() => {});
    return state.ctx;
  }

  // "Live Hall" reverb: Web Audio has no built-in hall-reverb node, so this
  // synthesizes a plausible impulse response -- exponentially-decaying
  // stereo white noise, a standard procedural technique for a ConvolverNode
  // -- once per AudioContext and reuses it for every video's chain.
  function getImpulseResponse(ctx) {
    if (state.impulse) return state.impulse;

    const duration = 2.5, decay = 3;
    const length = Math.floor(ctx.sampleRate * duration);
    const impulse = ctx.createBuffer(2, length, ctx.sampleRate);

    for (let ch = 0; ch < 2; ch++) {
      const data = impulse.getChannelData(ch);
      for (let i = 0; i < length; i++) {
        data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
      }
    }

    return state.impulse = impulse;
  }

  function buildChain(video) {
    const ctx = getContext();
    const source = ctx.createMediaElementSource(video);

    const bands = BAND_FREQS.map((freq) => {
      const f = ctx.createBiquadFilter();
      f.type = 'peaking';
      f.frequency.value = freq;
      f.Q.value = 1;
      f.gain.value = 0;
      return f;
    });

    const bass = ctx.createBiquadFilter();
    bass.type = 'lowshelf';
    bass.frequency.value = BASS_FREQ;
    bass.gain.value = 0;

    let node = source;
    for (const b of bands) { node.connect(b); node = b; }
    node.connect(bass);

    // Virtualizer: a Haas-effect stereo widener. The right channel is fed
    // through a short delay and blended back with the dry signal on both
    // channels; strength controls both the delay time and how much of the
    // delayed signal is mixed in, so it degrades gracefully to a plain
    // pass-through at strength 0 instead of a hard bypass switch.
    const splitter = ctx.createChannelSplitter(2);
    const merger = ctx.createChannelMerger(2);
    const dryR = ctx.createGain();
    const delay = ctx.createDelay(VIRT_MAX_DELAY);
    const wetR = ctx.createGain();
    const wetL = ctx.createGain();

    bass.connect(splitter);
    splitter.connect(merger, 0, 0);
    splitter.connect(dryR, 1);
    dryR.connect(merger, 0, 1);
    splitter.connect(delay, 1);
    delay.connect(wetR);
    wetR.connect(merger, 0, 1);
    delay.connect(wetL);
    wetL.connect(merger, 0, 0);

    // Live Hall reverb: a parallel send -- the dry signal always passes
    // through, the convolved "wet" signal layers on top, rather than
    // replacing the direct sound (matching how a real hall effect is used).
    const convolver = ctx.createConvolver();
    convolver.buffer = getImpulseResponse(ctx);
    convolver.normalize = true;
    const reverbDry = ctx.createGain();
    const reverbWet = ctx.createGain();
    merger.connect(reverbDry);
    merger.connect(convolver);
    convolver.connect(reverbWet);
    reverbDry.connect(ctx.destination);
    reverbWet.connect(ctx.destination);

    return {video, bands, bass, dryR, delay, wetR, wetL, reverbDry, reverbWet};
  }

  function applyToChain(chain) {
    const cfg = state.config;

    for (let i = 0; i < chain.bands.length; i++) {
      chain.bands[i].gain.value = cfg.eqEnabled ? (cfg.bands[i] || 0) : 0;
    }

    chain.bass.gain.value = cfg.bassEnabled ? cfg.bassGain : 0;

    const strength = cfg.virtEnabled ? Math.max(0, Math.min(1, cfg.virtStrength)) : 0;
    chain.delay.delayTime.value = 0.005 + VIRT_MAX_DELAY * 0.67 * strength;
    chain.dryR.gain.value = 1 - 0.5 * strength;
    chain.wetR.gain.value = 0.5 * strength;
    chain.wetL.gain.value = 0.3 * strength;

    const reverbStrength = cfg.reverbEnabled ? Math.max(0, Math.min(1, cfg.reverbStrength)) : 0;
    chain.reverbDry.gain.value = 1;
    chain.reverbWet.gain.value = reverbStrength * 0.6;
  }

  function attach(video) {
    if (video.getAttribute('FermataEqAttached') === 'true') return;
    video.setAttribute('FermataEqAttached', 'true');

    let chain;
    try {
      chain = buildChain(video);
    } catch (err) {
      console.debug('FermataEqualizer: failed to attach', err);
      return;
    }

    state.chains.set(video, chain);
    applyToChain(chain);
    video.addEventListener('playing', () => getContext());
  }

  function scan() {
    document.querySelectorAll('video').forEach(attach);
  }

  function startWatching() {
    scan();
    if (!window.__fermataEqObserver) {
      window.__fermataEqObserver = new MutationObserver(scan);
      window.__fermataEqObserver.observe(document.body, {childList: true, subtree: true});
    }
  }

  window.FermataEqualizer = {
    configure(config) {
      state.config = Object.assign({}, state.config, config);
      if (Array.isArray(config.bands)) state.config.bands = config.bands;
      startWatching();

      document.querySelectorAll('video').forEach((v) => {
        const chain = state.chains.get(v);
        if (chain) applyToChain(chain);
      });
    }
  };
})();
