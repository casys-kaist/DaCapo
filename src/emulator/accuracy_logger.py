import json
import numpy as np
from pathlib import Path
from typing import List, Any
from emulator.config import Config
from util.csv_logger import CsvLogger


HEADER = [
    "Window index",
    "Accuracy"
]


class AccuracyLogger(CsvLogger):
    def __init__(self, output_dir_path: Path, config: Config):
        self.acc_per_window_path = output_dir_path / "accuracy_per_window.csv"
        self.acc_per_image_path = output_dir_path / "accuracy_per_image.json"

        super().__init__(self.acc_per_window_path, HEADER)

        self.config = config

    def generate_log_file(self, corrects_per_phase: List[Any]):
        fps = self.config.fps
        window_time = self.config.window_time
        num_imgs_per_window = fps * window_time

        corrects = []
        for corrects_of_phase in corrects_per_phase:
            corrects.extend(corrects_of_phase)

        with open(self.acc_per_image_path, "w") as f:
            json.dump(corrects, f, indent=4)

        for w in range(0, len(corrects), num_imgs_per_window):
            corrects_per_window = corrects[w:w+num_imgs_per_window]

            assert (len(corrects_per_window) == num_imgs_per_window)

            self.write_row([
                int(w / num_imgs_per_window),
                np.sum(corrects_per_window) / len(corrects_per_window) * 100.
            ])