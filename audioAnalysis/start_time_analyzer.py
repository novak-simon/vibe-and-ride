from bisect import bisect_right
from typing import Any, List, Tuple
from audio_analysis_model import AudioAnalysis
import numpy as np
import librosa
import sys
class StartEndTimeAnalyzer:

    def __init__(self):
        pass


    def _avg(self, values: List[tuple]|List[int]) -> float:
        """
        Returns weighted average of first values in list of tuples as a 1 point float
        """
        avg_value = 0
        total_weight = 0
        for v in values:
            if isinstance(v, tuple):
                avg_value += v[0] * v[1]
                total_weight += v[1]
            else:
                avg_value += v
                total_weight += 1
        return round(avg_value / total_weight if total_weight else 0, 2)

    def _closest_lower(self, sorted_list: List[Any], value: float) -> float:
        """
        Returns the closest lower value from a sorted list
        """
        if not sorted_list:
            return None
        if isinstance(sorted_list[0], tuple):
            sorted_list = [v[0] for v in sorted_list]
        i = bisect_right(sorted_list, value)
        return round(sorted_list[i - 1], 2) if i > 0 else None

    def _wave_to_diff(self, wave: int) -> float:
        """
        Converts waveform value to a difference value
        """
        if wave < 46.875:
            return -5/(wave-50)
        elif wave < 68.125:
            return 8/25*(wave-(335/8))
        else:
            return (-5/(wave-65)) + 10


    def _create_blocks(self, from_list: List[tuple], max_diff: float, compare: bool = False) -> List[tuple]:
        """
        Groups values into blocks based on max_diff
        """
        blocks = []
        temp = []
        for idx, val in enumerate(from_list):
            if not temp:
                temp.append(val)
                continue
            avg = self._avg(temp)
            if abs(val[0] - avg) > max_diff:
                if compare:
                    if abs(val[0] - (from_list[idx + 1][0] if idx + 1 < len(from_list) else val[0])) > abs(val[0] - avg):
                        temp.append(val)
                        continue
                blocks.append((avg, round(from_list[idx - 1][1], 2)))
                temp.clear()
            temp.append(val)
        blocks.append((self._avg(temp), round(val[1], 2)))
        temp.clear()
        return blocks

    def _group_waveforms(self, peaks: List[float], duration: float, group_size: int) -> Tuple[List[tuple], float]:
        grouped = []
        temp = []
        i = 0
        waveforms = len(peaks)
        grouped_length = duration/waveforms*group_size
        for val in peaks:
            i += 1
            temp.append(val)
            if i % group_size == 0 or i == waveforms-1:
                grouped.append((self._avg(temp), round(grouped_length/group_size*i, 2)))
                temp.clear()
        return grouped, grouped_length

    def _zoom_in(self, input_list: List[Any], output_list: List[Any], input_interval_length: float, minus: int, plus: int) -> List[Any]:
        # Implementation for zooming in on specific intervals
        index = int(round(input_list[0][1]/input_interval_length)) - 1
        candidates = []
        max_diff = [0, 0]
        alt_max_diff = [0, 0]
        lower_bound = max(0, index - minus)
        upper_bound = min(len(output_list) - 1, index + plus)
        for idx, val in enumerate(output_list[lower_bound:upper_bound + 1]):
            if not isinstance(val, tuple):
                if lower_bound > 0:
                    diff = round(abs(val - output_list[lower_bound - 1:upper_bound][idx]), 2)
                else:
                    if idx > 0:
                        diff = round(abs(val - output_list[lower_bound:upper_bound + 1][idx - 1]), 2)
                    else:
                        diff = 0
                val = (val, lower_bound + idx)
            else:
                if lower_bound > 0:
                    diff = round(abs(val[0] - output_list[lower_bound - 1:upper_bound][idx][0]), 2)
                else:
                    if idx > 0:
                        diff = round(abs(val[0] - output_list[lower_bound:upper_bound + 1][idx - 1][0]), 2)
                    else:
                        diff = 0
            candidates.append(val)
            if diff > max_diff[0] + self._wave_to_diff(val[0]):
                max_diff = [diff, idx]
            if diff > alt_max_diff[0]:
                alt_max_diff = [diff, idx]
        if max_diff is None or max_diff[0] == 0:
            max_diff = alt_max_diff
        if max_diff[1] - 1 >= 0:
            chosen = [candidates[max_diff[1] - 1], candidates[max_diff[1]]]
        else:
            chosen = [candidates[max_diff[1]]]
        return chosen

    def zoom_out_phase(self, peaks: List[float], duration: float) -> Tuple[List[tuple], List[tuple], float]:
        # Groups waveforms into groups of 10 (approximately 2 seconds) and for each group takes the average waveform
        values_grouped, grouped_length = self._group_waveforms(peaks, duration, 10)
        # Goes through grouped values and compares each value to the average of the current forming group, when too distinct, adds current forming group to new list and starts forming new group
        small_blocks = self._create_blocks(values_grouped, 5, compare=False)   #5
        medium_blocks = self._create_blocks(small_blocks, 10, compare=True)
        return medium_blocks, values_grouped, grouped_length

    def zoom_in_phase(self, peaks: List[float], duration: float, medium_blocks: List[tuple], values_grouped: List[tuple], grouped_length: float, beatnet_analysis: AudioAnalysis, librosa_analysis: AudioAnalysis) -> List[tuple]:
        grouped_chosen = self._zoom_in(medium_blocks, values_grouped, grouped_length, 2, 2)
        wave_length = float(duration/len(peaks))
        wave_chosen = self._zoom_in(grouped_chosen, peaks, wave_length, 9, 10)
        wave_chosen_index = int(wave_chosen[0][1])
        waveform_based_start_time = round(wave_chosen_index * wave_length, 2)
        used_analysis = beatnet_analysis if beatnet_analysis.bpm_confidence and (not librosa_analysis.bpm_confidence or beatnet_analysis.bpm_confidence > librosa_analysis.bpm_confidence) else librosa_analysis
        if used_analysis is beatnet_analysis:
            timestamps = [t[0] for t in beatnet_analysis.beat_timestamps]
        else:
            timestamps = librosa_analysis.beat_timestamps
        start_time = self._closest_lower(timestamps, waveform_based_start_time)
        if start_time is None:
            start_time = waveform_based_start_time
        print(f"grouped_chosen={grouped_chosen}, wave_chosen={wave_chosen}, wave_chosen_index={wave_chosen_index}, computed_time={waveform_based_start_time}", file=sys.stderr)
        return start_time
    
    def get_waveform_peaks(self, y, num_points=1000):
        chunk_size = len(y) // num_points
        peaks = [np.max(np.abs(y[i:i+chunk_size])) * 100 for i in range(0, len(y), chunk_size)]
        return peaks

    def analyze(self, audio_path: str, duration: float, beatnet_analysis: AudioAnalysis, librosa_analysis: AudioAnalysis) -> AudioAnalysis:
        """
        Estimates best starting time in miliseconds for playing the song
        Calculates based on waveforms of the song
        """
        y, sr = librosa.load(audio_path, sr=None)  # sr=None preserves original sample rate
        peaks = self.get_waveform_peaks(y, num_points=1000)

        medium_blocks, values_grouped, grouped_length = self.zoom_out_phase(peaks, duration)
        start_time = self.zoom_in_phase(peaks, duration, medium_blocks, values_grouped, grouped_length, beatnet_analysis, librosa_analysis)

        reversed_medium_blocks = list(reversed(medium_blocks))
        reversed_values_grouped = list(reversed(values_grouped))

        print(f"medium_blocks[0]={medium_blocks[0]}, values_grouped[0]={values_grouped[0]}, values_grouped[-1]={values_grouped[-1]}", file=sys.stderr)
        print(f"reversed_medium_blocks[0]={reversed_medium_blocks[0]}", file=sys.stderr)

        end_time = self.zoom_in_phase(peaks, duration, reversed_medium_blocks, reversed_values_grouped, grouped_length, beatnet_analysis, librosa_analysis)
        return AudioAnalysis(
            bpm=None,
            bpm_confidence=None,
            beat_intervals=None,
            beat_timestamps=None,
            start_time=start_time,
            end_time=end_time
        )