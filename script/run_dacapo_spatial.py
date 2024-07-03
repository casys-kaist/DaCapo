import math
import time
import torch
import argparse
import numpy as np
from pathlib import Path
from typing import List, Tuple
from torch.utils.data import Dataset
from util.reproduce import set_reproducibility
from util.accuracy import ClassificationAccuracyTracker
from emulator.config import Config
from emulator.model_handler import ModelPrecision
from emulator.accuracy_logger import AccuracyLogger
from emulator.phase_info_logger import PhaseInfoLogger
from emulator.model_handler import ModelHandler
from emulator.spatial_resource_allocator import SpatialResourceAllocator
from emulator.dataset import split_dataset_indices_by_time, DaCapoDataset, TrainSampleDataset


parser = argparse.ArgumentParser(description="Continual learning emluator")
parser.add_argument("--config-path", type=str, help="path of emulator configuration .json file")


class DaCapoSimulator:
    def __init__(self, config: Config):
        self.config: Config = config

        set_reproducibility(self.config.seed)

        if self.config.cl_type != "STATIC":
            raise ValueError(f"continual learning type must be STATIC, not {self.config.cl_type}")
            
        self.resource_allocator = SpatialResourceAllocator(config=self.config)

        self.student_model_handler = ModelHandler(config=self.config,
                                                  name=self.config.student_model,
                                                  precision=ModelPrecision.MX9,
                                                  num_classes=self.config.num_classes,
                                                  device=self.config.device,
                                                  weight_path=self.config.student_weight)

        self.train_datasets: List[TrainSampleDataset] = [None,]

        # instances for logging results
        self.phase_info_logger = PhaseInfoLogger(file_path=f"{self.config.output_root}/phase_log.csv")

        self.accuracy_logger = AccuracyLogger(output_dir_path=Path(self.config.output_root),
                                              config=self.config)

        self.corrects_per_phase = []

    def run(self):
        self.entire_dataset = DaCapoDataset(scenario_dir=Path(self.config.scenario_path),
                                              transform=None)
        self.latency_table = self.entire_dataset.generate_frame_latency_table(fps=self.config.fps)

        start_idx = 0
        start_time = 0
        duration_time = self.config.window_time

        self.window_index_slices = []

        while start_idx != -1:
            index_slice, \
            next_start_idx, \
            next_start_time, \
            time = split_dataset_indices_by_time(frame_latency_table=self.latency_table,
                                                 start_index=start_idx,
                                                 start_time=start_time,
                                                 duration_time=duration_time)
            print(f"window idx: {len(self.window_index_slices)}")
            print(f"time: {time:.1f} seconds")
            print(f"start idx: {start_idx} (time: {start_time:.1f} seconds)")
            print(f"index slice:{index_slice} (# of images: {index_slice[1] - index_slice[0] + 1})")

            start_idx = next_start_idx
            start_time = next_start_time
            self.window_index_slices.append(index_slice)
    
        for window_idx in range(len(self.window_index_slices)):
            print(f"window #{window_idx}")
            self.__run(window_idx=window_idx,
                       window_index_slice=self.window_index_slices[window_idx])
            
        self.accuracy_logger.generate_log_file(corrects_per_phase=self.corrects_per_phase)

    def __run(self,
              window_idx: int,
              window_index_slice: Tuple[int, int]):
        window_accuracy_tracker = ClassificationAccuracyTracker()
        p1_accuracy_tracker = ClassificationAccuracyTracker()
        p2_accuracy_tracker = ClassificationAccuracyTracker()

        self.resource_allocator.allocate_static_spatial_resource()
        
        p1_index_slice, p2_index_slice = self.__split_indice_by_phase(p1_time=self.resource_allocator.static_p1_time,
                                                                      window_index_slice=window_index_slice)
        
        p1_infer_num_imgs = p1_index_slice[1] - p1_index_slice[0] + 1
        p2_infer_num_imgs = p2_index_slice[1] - p2_index_slice[0] + 1

        print(f"p1: {p1_infer_num_imgs} images, p2: {p2_infer_num_imgs} images")

        # >>> phase 1
        # inference
        self.student_model_handler.change_precision(precision=self.resource_allocator.static_m_I_p1)
        self.student_model_handler.infer(phase=1,
                                         dataset=self.entire_dataset,
                                         index_slice=p1_index_slice,
                                         phase_accuracy_tracker=p1_accuracy_tracker,
                                         window_accuracy_tracker=window_accuracy_tracker)
        p1_acc = p1_accuracy_tracker.avg_acc1
        print(f"exec time: {self.resource_allocator.static_p1_time:.1f} seconds, accuracy: {p1_acc:.1f}%")
        
        # retrain if necessary
        self.student_model_handler.change_precision(precision=ModelPrecision.MX9)
        print(f"change precision to {ModelPrecision.MX9} bit for phase #1 training")
        train_dataset = self.train_datasets[window_idx]
        _ = self.student_model_handler.train(train_dataset=train_dataset,
                                             valid_dataset=None,
                                             epochs=self.resource_allocator.static_epochs)

        self.phase_info_logger.write_phase_info(phase_index=window_idx,
                                                phase_name=f"phase 1 at window #{window_idx}",
                                                num_imgs=p1_infer_num_imgs,
                                                exec_time=self.resource_allocator.static_p1_time,
                                                accuracy=p1_acc,
                                                num_train_imgs=len(train_dataset) if train_dataset is not None else 0,
                                                train_epochs=self.resource_allocator.static_epochs,
                                                num_valid_imgs=-1,
                                                num_label_imgs=-1,
                                                buffer_count=-1,
                                                buffer_capacity=-1)
        # <<< phase 1
        self.corrects_per_phase.append(p1_accuracy_tracker.corrects)

        # >>> phase 2
        # inference
        self.student_model_handler.change_precision(precision=self.resource_allocator.static_m_I_p2)
        print(f"change precision to {self.resource_allocator.static_m_I_p2} bit for phase #2 inference")
        print(f"# of images: {p2_infer_num_imgs}")
        self.student_model_handler.infer(phase=2,
                                         dataset=self.entire_dataset,
                                         index_slice=p2_index_slice,
                                         phase_accuracy_tracker=p2_accuracy_tracker,
                                         window_accuracy_tracker=window_accuracy_tracker)
        # <<< phase 2

        # label
        train_dataset = self.__sample_data_to_train(entire_dataset=self.entire_dataset,
                                                    index_slice=window_index_slice,
                                                    sampling_rate=self.resource_allocator.static_sampling_rate)
        self.train_datasets.append(train_dataset)

        self.phase_info_logger.write_phase_info(phase_index=window_idx,
                                                phase_name=f"phase 2 at window #{window_idx}",
                                                num_imgs=p2_infer_num_imgs,
                                                exec_time=self.resource_allocator.static_p2_time,
                                                accuracy=p2_accuracy_tracker.avg_acc1,
                                                num_train_imgs=-1,
                                                train_epochs=-1,
                                                num_valid_imgs=-1,
                                                num_label_imgs=len(train_dataset),
                                                buffer_count=-1,
                                                buffer_capacity=-1)
        # <<< phase 2
        self.corrects_per_phase.append(p2_accuracy_tracker.corrects)

    def __split_indice_by_phase(self,
                                p1_time: float,
                                window_index_slice: Tuple[int, int]) -> Tuple[Tuple[int, int]]:
        sub_latency_table = self.latency_table[window_index_slice[0]:window_index_slice[1] + 1]

        print(f"{sub_latency_table[0]:.1f} seconds (idx: {window_index_slice[0]}), {sub_latency_table[-1]:.1f} seconds (idx: {window_index_slice[1]})")

        p1_time = sub_latency_table[0] + p1_time
        for index, value in enumerate(sub_latency_table):
            if value <= p1_time:
                last_index = index

        p1_index_slice = (window_index_slice[0], window_index_slice[0] + last_index)
        p2_index_slice = (window_index_slice[0] + last_index + 1, window_index_slice[1])

        print(f"p1 index slice: {p1_index_slice}")
        print(f"p2 index slice: {p2_index_slice}")

        return p1_index_slice, p2_index_slice
    
    def __sample_data_to_train(self,
                               entire_dataset: DaCapoDataset,
                               index_slice: Tuple[int, int],
                               sampling_rate: float) -> Dataset:
        total_num = index_slice[1] - index_slice[0] + 1
        train_num_imgs = int(math.floor(total_num * sampling_rate))
        step = int(np.floor(total_num / train_num_imgs))

        train_sample_indices = []

        for offset in range(0, total_num, step):
            if len(train_sample_indices) == train_num_imgs:
                break
            
            train_sample_indices.append(index_slice[0] + offset)

        print(f"# of images: {total_num}, sampled # of images: {len(train_sample_indices)} ({len(train_sample_indices) / total_num * 100.:.1f}%)")

        train_dataset = TrainSampleDataset(ori_dataset=entire_dataset,
                                           indices=train_sample_indices)
        
        return train_dataset


if __name__ == "__main__":
    args = parser.parse_args()
    simulator = DaCapoSimulator(config=Config(config_file_path=args.config_path))

    torch.cuda.synchronize()
    start_time = time.time()

    simulator.run()

    torch.cuda.synchronize()
    end_time = time.time()

    print(f"experiment time: {end_time-start_time:.1f} seconds")