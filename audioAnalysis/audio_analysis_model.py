from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional


@dataclass(frozen=True)
class AudioAnalysis:
    bpm: int
    bpm_confidence: Optional[int]
    beat_intervals: List[float]
    beat_timestamps: List[float]|List[tuple[float, int]]
    start_time: float = 0
    end_time: float = 0