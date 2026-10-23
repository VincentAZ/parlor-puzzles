#!/usr/bin/env python3
"""Deterministic original short plastic/chime cues, synthesized for Parlor.

PCM source is generated here, with no sampled third-party audio. SoundPool loads
these assets before interaction; physical output latency still needs measurement.
"""
import math
from pathlib import Path
import random
import struct
import wave

RATE = 44100
DEST = Path(__file__).resolve().parents[1] / 'core/platform/src/main/res/raw'
DEST.mkdir(parents=True, exist_ok=True)
specs = {
    'pickup': (0.075, [720, 1160], 0.12),
    'place': (0.10, [430, 890], 0.18),
    'slide': (0.14, [280, 650], 0.30),
    'illegal': (0.11, [180, 235], 0.02),
    'undo': (0.09, [590, 410], 0.08),
    'hint': (0.24, [659.25, 987.77], 0.0),
    'star': (0.28, [783.99, 1174.66], 0.0),
    'complete': (0.65, [523.25, 659.25, 783.99], 0.0),
    'tier': (0.85, [523.25, 659.25, 783.99, 1046.5], 0.0),
}
for material, base, noise in [('wood', 360, .22), ('metal', 1320, .03), ('water', 760, .10)]:
    for cue, duration, factor in [('pickup', .07, 1.3), ('place', .11, 1.0), ('slide', .18, .8), ('undo', .12, 1.15), ('illegal', .09, .55)]:
        specs[f'{material}_{cue}'] = (duration, [base * factor, base * factor * 2.73], noise)
for seed, (name, (duration, frequencies, noise)) in enumerate(specs.items()):
    rng = random.Random(seed)
    frames = bytearray()
    for i in range(int(RATE * duration)):
        t = i / RATE
        attack = min(1.0, t / 0.003)
        release = min(1.0, (duration - t) / 0.015)
        envelope = attack * release * math.exp(-t / (duration / 4))
        phase_time = t + (.6 * t * t / duration if name.startswith('water_') else 0)
        tone = sum(math.sin(2*math.pi*f*phase_time) for f in frequencies) / len(frequencies)
        sample = 0.42 * envelope * ((1-noise)*tone + noise*rng.uniform(-1,1))
        frames.extend(struct.pack('<h', round(sample * 32767)))
    with wave.open(str(DEST / f'{name}.wav'), 'wb') as out:
        out.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        out.writeframes(frames)
print(f'Generated {len(specs)} original cues in {DEST}')
