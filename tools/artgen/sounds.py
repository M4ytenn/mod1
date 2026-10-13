"""Synthesised sounds: hiss, rumble, mains hum, arc and typewriter. Written as mono 44.1 kHz Ogg Vorbis via ffmpeg."""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100


def env(n, attack, release_k):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-t * release_k)


def bandpass(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = ((f >= lo) & (f <= hi)).astype(float)
    # soft edges
    edge = 0.15
    mask = np.where(f < lo, np.exp(-((lo - f) / (lo * edge + 1)) ** 2), mask)
    mask = np.where(f > hi, np.exp(-((f - hi) / (hi * edge + 1)) ** 2), mask)
    return np.fft.irfft(spec * mask, len(x))


def norm(x, peak=0.85):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def unseal(rng):
    n = int(SR * 2.6)
    noise = rng.standard_normal(n)
    hiss = bandpass(noise, 900, 7500)
    flutter = 1 + 0.25 * np.sin(np.arange(n) / SR * 2 * np.pi * 7.3) * np.exp(-np.arange(n) / SR * 1.5)
    hiss *= env(n, 0.03, 1.1) * flutter
    thunk_n = int(SR * 0.35)
    t = np.arange(thunk_n) / SR
    thunk = np.sin(2 * np.pi * 58 * t) * np.exp(-t * 14) + 0.5 * np.sin(2 * np.pi * 131 * t) * np.exp(-t * 22)
    out = hiss * 0.7
    out[:thunk_n] += thunk * 0.9
    return norm(out)


def rumble(rng):
    n = int(SR * 1.1)
    noise = rng.standard_normal(n)
    low = bandpass(noise, 25, 220)
    grind = bandpass(rng.standard_normal(n), 400, 1800) * (0.5 + 0.5 * np.abs(np.sin(np.arange(n) / SR * 2 * np.pi * 3.1)))
    t = np.arange(n) / SR
    squeal = 0.08 * np.sin(2 * np.pi * (731 + 15 * np.sin(2 * np.pi * 0.7 * t)) * t) * np.exp(-((t - 0.55) / 0.25) ** 2)
    shape = np.minimum(1, t / 0.08) * np.minimum(1, (1.1 - t) / 0.25)
    return norm((low * 1.0 + grind * 0.18 + squeal) * shape, 0.8)


def hiss(rng):
    n = int(SR * 0.95)
    x = bandpass(rng.standard_normal(n), 2200, 9500)
    return norm(x * env(n, 0.006, 3.6), 0.8)


def hum(rng):
    n = int(SR * 1.3)
    t = np.arange(n) / SR
    x = (np.sin(2 * np.pi * 100 * t) * 0.6 + np.sin(2 * np.pi * 200 * t) * 0.3 + np.sin(2 * np.pi * 300.7 * t) * 0.18
         + np.sin(2 * np.pi * 400 * t) * 0.08)
    x *= 1 + 0.15 * np.sin(2 * np.pi * 1.7 * t)
    shape = np.minimum(1, t / 0.06) * np.minimum(1, (1.3 - t) / 0.5)
    return norm(x * shape, 0.7)


def arc(rng):
    n = int(SR * 0.55)
    t = np.arange(n) / SR
    out = np.zeros(n)
    # crackle: sparse sharp impulses with tiny resonances
    k = 0
    while k < n - 400:
        k += int(rng.exponential(SR * 0.012))
        if k >= n - 400:
            break
        amp = rng.uniform(0.4, 1.0) * np.exp(-t[k] * 4)
        burst = rng.standard_normal(300) * np.exp(-np.arange(300) / 40)
        out[k:k + 300] += burst * amp
    buzz = np.sign(np.sin(2 * np.pi * 100 * t)) * 0.25 * np.exp(-t * 5)
    out = bandpass(out, 1500, 12000) + bandpass(buzz, 90, 2500)
    pop_n = int(SR * 0.04)
    out[:pop_n] += rng.standard_normal(pop_n) * np.exp(-np.arange(pop_n) / 300) * 1.2
    return norm(out, 0.85)


def typewriter(rng, variant):
    n = int(SR * 0.16)
    t = np.arange(n) / SR
    click = bandpass(rng.standard_normal(n), 2500, 9000) * np.exp(-t * 900)
    clack = np.sin(2 * np.pi * (2300 + variant * 140) * t) * np.exp(-t * 90) * 0.35
    thud = np.sin(2 * np.pi * (180 + variant * 15) * t) * np.exp(-t * 60) * 0.6
    delay = int(SR * 0.012)
    out = click * 1.0 + thud
    out[delay:] += clack[:-delay]
    return norm(out, 0.8)


def write_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.unlink(wav_path)


def write(res_root):
    rng = np.random.default_rng(1974)
    base = os.path.join(res_root, 'assets/hermodoors/sounds')
    write_ogg(unseal(rng), f'{base}/door/unseal.ogg')
    write_ogg(rumble(rng), f'{base}/door/rumble1.ogg')
    write_ogg(rumble(rng), f'{base}/door/rumble2.ogg')
    write_ogg(hiss(rng), f'{base}/panel/hiss.ogg')
    write_ogg(arc(rng), f'{base}/panel/arc1.ogg')
    write_ogg(arc(rng), f'{base}/panel/arc2.ogg')
    write_ogg(hum(rng), f'{base}/panel/hum.ogg')
    for v in range(3):
        write_ogg(typewriter(rng, v), f'{base}/passport/type{v + 1}.ogg')
