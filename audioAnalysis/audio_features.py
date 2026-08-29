"""
audio_features.py

Extract audio features directly from a streaming audio URL.

Requirements:
    pip install librosa numpy pyloudnorm

System requirement:
    FFmpeg must be installed and available on PATH.

Usage:
    python audio_features.py "https://example.com/audio"

The script does not save the audio stream to disk.
FFmpeg decodes the stream and sends raw PCM audio directly to Python.
"""

from __future__ import annotations

import json
import subprocess
import sys
from typing import Dict

import librosa
import numpy as np
import pyloudnorm as pyln


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SAMPLE_RATE = 44100

N_FFT = 2048
HOP_LENGTH = 512

CHANNELS = 1

# Number of seconds of audio we ask FFmpeg for at a time.
READ_SECONDS = 10


# ---------------------------------------------------------------------------
# Streaming
# ---------------------------------------------------------------------------

def stream_audio(url: str, duration: float):
    

    bytes_per_sample = 2  # s16le = 16-bit = 2 bytes

    chunk_size = (
        SAMPLE_RATE
        * CHANNELS
        * bytes_per_sample
        * READ_SECONDS
    )

    process = subprocess.Popen(
    [
        "ffmpeg",

        "-i",
        url,

        "-t",
        str(duration),

        "-f",
        "s16le",
        "-ac",
        str(CHANNELS),
        "-ar",
        str(SAMPLE_RATE),

        "pipe:1",
    ],
    stdout=subprocess.PIPE,
    stderr=subprocess.DEVNULL,
)

    assert process.stdout is not None

    try:
        while True:
            data = process.stdout.read(chunk_size)

            if not data:
                break

            samples = np.frombuffer(
                data,
                dtype=np.int16
            ).astype(np.float32)

            samples /= 32768.0

            yield samples

    finally:
        process.kill()
        process.wait()


def load_stream(
    url: str,
    duration: float
) -> np.ndarray:

    chunks = []

    for chunk in stream_audio(url, duration):
        chunks.append(chunk)

    if not chunks:
        raise RuntimeError(
            "The audio stream contained no data."
        )

    return np.concatenate(chunks)


# ---------------------------------------------------------------------------
# Loudness
# ---------------------------------------------------------------------------

def calculate_loudness_lufs(
    y: np.ndarray,
    sr: int
) -> float:

    meter = pyln.Meter(sr)

    loudness = meter.integrated_loudness(y)

    return float(loudness)


# ---------------------------------------------------------------------------
# RMS
# ---------------------------------------------------------------------------

def calculate_rms_db(
    y: np.ndarray
) -> float:

    rms = librosa.feature.rms(
        y=y,
        frame_length=N_FFT,
        hop_length=HOP_LENGTH
    )[0]

    value = float(np.mean(rms))

    value = max(value, 1e-12)

    return float(
        20.0 * np.log10(value)
    )


# ---------------------------------------------------------------------------
# Spectral features
# ---------------------------------------------------------------------------

def calculate_spectral_features(
    y: np.ndarray,
    sr: int
) -> tuple[float, float]:

    S = np.abs(
        librosa.stft(
            y,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH
        )
    )

    # ---------------------------------------------------------------
    # Spectral centroid
    # ---------------------------------------------------------------

    centroid = librosa.feature.spectral_centroid(
        S=S,
        sr=sr
    )[0]

    spectral_centroid_hz = float(
        np.mean(centroid)
    )

    # ---------------------------------------------------------------
    # Spectral flux
    # ---------------------------------------------------------------

    # Normalize each frame so flux primarily measures spectral change,
    # rather than simply volume.
    frame_energy = np.sum(
        S,
        axis=0,
        keepdims=True
    )

    normalized_S = (
        S / np.maximum(frame_energy, 1e-12)
    )

    difference = np.diff(
        normalized_S,
        axis=1
    )

    # Only count increases in spectral energy.
    positive_difference = np.maximum(
        difference,
        0.0
    )

    flux = np.sqrt(
        np.sum(
            positive_difference ** 2,
            axis=0
        )
    )

    spectral_flux = float(
        np.mean(flux)
    )

    return (
        spectral_centroid_hz,
        spectral_flux
    )


# ---------------------------------------------------------------------------
# Tempo / beats
# ---------------------------------------------------------------------------

def calculate_tempo_and_beats(
    y: np.ndarray,
    sr: int
) -> tuple[float, float]:

    tempo, beat_frames = librosa.beat.beat_track(
        y=y,
        sr=sr,
        hop_length=HOP_LENGTH
    )

    tempo = float(
        np.asarray(tempo).reshape(-1)[0]
    )

    duration = librosa.get_duration(
        y=y,
        sr=sr
    )

    if duration <= 0:
        beat_density = 0.0
    else:
        beat_density = (
            len(beat_frames) / duration
        )

    return tempo, float(beat_density)


# ---------------------------------------------------------------------------
# Onset density
# ---------------------------------------------------------------------------

def calculate_onset_density(
    y: np.ndarray,
    sr: int
) -> float:

    onset_envelope = librosa.onset.onset_strength(
        y=y,
        sr=sr,
        hop_length=HOP_LENGTH
    )

    if len(onset_envelope) == 0:
        return 0.0

    onset_frames = librosa.onset.onset_detect(
        onset_envelope=onset_envelope,
        sr=sr,
        hop_length=HOP_LENGTH,
        backtrack=False
    )

    duration = librosa.get_duration(
        y=y,
        sr=sr
    )

    if duration <= 0:
        return 0.0

    return float(
        len(onset_frames) / duration
    )


# ---------------------------------------------------------------------------
# Frequency-band ratios
# ---------------------------------------------------------------------------

def calculate_frequency_ratios(
    y: np.ndarray,
    sr: int
) -> Dict[str, float]:

    S = np.abs(
        librosa.stft(
            y,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH
        )
    )

    power = S ** 2

    frequencies = librosa.fft_frequencies(
        sr=sr,
        n_fft=N_FFT
    )

    bands = {
        "sub_bass_ratio": (20.0, 60.0),
        "bass_ratio": (60.0, 250.0),
        "low_mid_ratio": (250.0, 500.0),
        "mid_ratio": (500.0, 2000.0),
        "high_mid_ratio": (2000.0, 6000.0),
        "high_ratio": (6000.0, 16000.0),
    }

    # Total power in the complete frequency range.
    total_mask = (
        (frequencies >= 20.0)
        & (frequencies < 16000.0)
    )

    total_energy = float(
        np.sum(power[total_mask])
    )

    if total_energy <= 1e-12:
        return {
            name: 0.0
            for name in bands
        }

    result = {}

    for name, (low, high) in bands.items():

        mask = (
            (frequencies >= low)
            & (frequencies < high)
        )

        band_energy = float(
            np.sum(power[mask])
        )

        result[name] = (
            band_energy / total_energy
        )

    return result

# ---------------------------------------------------------------------------
# Feature extraction
# ---------------------------------------------------------------------------

def extract_audio_features(
    url: str,
    duration: float
) -> Dict[str, float]:

    print(
        "Reading audio stream...",
        file=sys.stderr
    )

    y = load_stream(
        url,
        duration
    )

    if len(y) == 0:
        raise RuntimeError(
            "No audio samples were received."
        )

    sr = SAMPLE_RATE

    duration = librosa.get_duration(
        y=y,
        sr=sr
    )

    if duration < 1.0:
        raise RuntimeError(
            "Audio stream is too short for analysis."
        )

    print(
        f"Received {duration:.1f} seconds of audio.",
        file=sys.stderr
    )

    # ---------------------------------------------------------------
    # Calculate features
    # ---------------------------------------------------------------

    loudness_lufs = calculate_loudness_lufs(
        y,
        sr
    )

    rms_db = calculate_rms_db(
        y
    )

    (
        spectral_centroid_hz,
        spectral_flux
    ) = calculate_spectral_features(
        y,
        sr
    )

    bpm, beat_density = (
        calculate_tempo_and_beats(
            y,
            sr
        )
    )

    onset_density = calculate_onset_density(
        y,
        sr
    )

    frequency_ratios = (
        calculate_frequency_ratios(
            y,
            sr
        )
    )

    # ---------------------------------------------------------------
    # Return
    # ---------------------------------------------------------------

    return {
        "loudness_lufs": loudness_lufs,
        "rms_db": rms_db,
        "spectral_centroid_hz": spectral_centroid_hz,
        "spectral_flux": spectral_flux,
        "bpm": bpm,
        "onset_density": onset_density,
        "beat_density": beat_density,
        **frequency_ratios,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":

    if len(sys.argv) != 3:
        print(
            "Usage: python audio_features.py <audio_url> <duration>",
            file=sys.stderr
        )
        sys.exit(1)

    url = sys.argv[1]

    try:
        duration = float(sys.argv[2])

        features = extract_audio_features(
            url,
            duration
        )

        print(
            json.dumps(
                features,
                indent=2
            )
        )

    except Exception as e:

        print(
            f"ERROR: {type(e).__name__}: {e}",
            file=sys.stderr
        )

        sys.exit(1)