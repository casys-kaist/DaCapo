import time
import torch
import argparse
import numpy as np
from tqdm import tqdm
from pathlib import Path
from typing import List, Tuple
from torch.utils.data import DataLoader
from util.reproduce import set_reproducibility
from util.accuracy import ClassificationAccuracyTracker
from emulator.config import Config
from emulator.sample_buffer import SampleBuffer
from emulator.accuracy_logger import AccuracyLogger
from emulator.phase_info_logger import PhaseInfoLogger
from emulator.model_handler import ModelHandler, ModelPrecision
from emulator.spatiotemporal_resource_allocator import SpatiotemporalResourceAllocator
from emulator.dataset import split_dataset_indices_by_time, DaCapoDataset, TrainSampleDataset


parser = argparse.ArgumentParser(description="DaCapo-Spatiotemporal System Simultor")
parser.add_argument("--config-path", type=str, help="path of emulator configuration .json file")


class DaCapoSimulator:
    def __init__(self, config: Config):
        self.config: Config = config

        set_reproducibility(self.config.seed)

        if self.config.cl_type != "DACAPO":
            raise ValueError(f"continual learning type must be DACAPO, not {self.config.cl_type}")
            
        self.resource_allocator = SpatiotemporalResourceAllocator(config=self.config)

        self.student_model_handler = ModelHandler(config=self.config,
                                                  name=self.config.student_model,
                                                  precision=ModelPrecision.MX9,
                                                  num_classes=self.config.num_classes,
                                                  device=self.config.device,
                                                  weight_path=self.config.student_weight)

        self.train_datasets: List[TrainSampleDataset] = [None,]

        self.max_sampling_img_num = self.config.max_num_imgs_to_label
        self.num_data_for_first_label = self.max_sampling_img_num
        self.sub_label_sr = self.config.min_num_imgs_to_label

        self.sample_buffer = SampleBuffer(config=config)

        # instances for logging results
        self.phase_info_logger = PhaseInfoLogger(file_path=f"{self.config.output_root}/phase_log.csv")

        self.accuracy_logger = AccuracyLogger(output_dir_path=Path(self.config.output_root),
                                              config=self.config)

        self.corrects_per_phase = []

    def run(self):
        self.entire_dataset = DaCapoDataset(scenario_dir=Path(self.config.scenario_path),
                                            transform=None)
        self.latency_table = self.entire_dataset.generate_frame_latency_table(fps=self.config.fps)

        is_done = False
        start_index = 0
        start_time = 0

        self.phase_index = 0

        while not is_done:
            is_done, \
            next_start_idx, \
            next_start_time = self.__run(start_index=start_index,
                                         start_time=start_time)

            start_index = next_start_idx
            start_time = next_start_time

            self.phase_index += 1

        self.accuracy_logger.generate_log_file(corrects_per_phase=self.corrects_per_phase)

    def __run(self,
              start_index: int,
              start_time: float) -> Tuple[bool, int, float]: 
        is_done = False

        train_phase_acc_tracker = ClassificationAccuracyTracker()
        label_phase_acc_tracker = ClassificationAccuracyTracker()
        additional_label_phase_acc_tracker = ClassificationAccuracyTracker()
        total_phase_acc_tracker = ClassificationAccuracyTracker()

        valid_metric = None
        drift_occur = False

        if self.sample_buffer.count() > 0:
            # train
            train_dataset, valid_dataset = self.sample_buffer.generate_dataset(phase_index=self.phase_index,
                                                                               entire_dataset=self.entire_dataset,
                                                                               num_data_for_train=self.config.num_imgs_to_train,
                                                                               num_data_for_valid=self.config.num_imgs_to_valid)
            print(f"TRAIN LENGTH: {len(train_dataset)}")
            print(f"VALID LENGTH: {len(valid_dataset)}")

            required_train_time = self.resource_allocator.allocate_train_time(train_dataset=train_dataset)
            required_valid_time = self.resource_allocator.allocate_valid_time(valid_dataset=valid_dataset)

            # >>>>> get required time for the phase and check phase budget >>>>>
            train_phase_index_slice, \
            train_start_idx, \
            train_start_time, \
            train_time_budget = split_dataset_indices_by_time(frame_latency_table=self.latency_table,
                                                              start_index=start_index,
                                                              start_time=start_time,
                                                              duration_time=required_train_time + required_valid_time)
            # <<<<< get required time for the phase and check phase budget <<<<<

            self.student_model_handler.infer(phase=1,
                                             dataset=self.entire_dataset,
                                             index_slice=train_phase_index_slice,
                                             phase_accuracy_tracker=train_phase_acc_tracker,
                                             window_accuracy_tracker=total_phase_acc_tracker)
            print(f"# of processed images: {len(total_phase_acc_tracker.corrects)}")

            if train_time_budget < (required_train_time + required_valid_time):
                print(f"experiment ended at train phase, "
                      f"total # of images: {len(total_phase_acc_tracker.corrects)},"
                      f"start index: {train_start_idx}")

                self.corrects_per_phase.append(total_phase_acc_tracker.corrects)

                self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                        phase_name="last infer only at train phase",
                                                        num_imgs=len(train_phase_acc_tracker.corrects),
                                                        exec_time=train_time_budget,
                                                        accuracy=train_phase_acc_tracker.avg_acc1,
                                                        num_train_imgs=-1,
                                                        train_epochs=-1,
                                                        num_valid_imgs=-1,
                                                        num_label_imgs=-1,
                                                        buffer_count=self.sample_buffer.count(),
                                                        buffer_capacity=self.sample_buffer.capacity)

                return True, -1, -1
            else:
                valid_metric = self.student_model_handler.train(train_dataset=train_dataset,
                                                                valid_dataset=valid_dataset,
                                                                epochs=1)
                
                self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                        phase_name=f"train phase",
                                                        num_imgs=len(train_phase_acc_tracker.corrects),
                                                        exec_time=train_time_budget,
                                                        accuracy=train_phase_acc_tracker.avg_acc1,
                                                        num_train_imgs=len(train_dataset),
                                                        train_epochs=1,
                                                        num_valid_imgs=len(valid_dataset),
                                                        num_label_imgs=-1,
                                                        buffer_count=self.sample_buffer.count(),
                                                        buffer_capacity=self.sample_buffer.capacity)

                print(f"train phase {required_train_time:.1f} seconds, "
                      f"# of images: {train_phase_index_slice[1] - train_phase_index_slice[0] + 1}")
                print(f"valid metric: {valid_metric:.3f}%")

                sub_label_sr = self.sub_label_sr

                # >>>>> get required time for the phase and check phase budget >>>>>
                required_label_time = self.resource_allocator.allocate_label_time(num_data_to_sample=sub_label_sr)
                
                # inference at label phase
                label_phase_index_slice, \
                next_start_idx, \
                next_start_time, \
                label_time_budget = split_dataset_indices_by_time(frame_latency_table=self.latency_table,
                                                                  start_index=train_start_idx,
                                                                  start_time=train_start_time,
                                                                  duration_time=required_label_time)

                # <<<<< get required time for the phase and check phase budget <<<<<
                self.student_model_handler.infer(phase=2,
                                                 dataset=self.entire_dataset,
                                                 index_slice=label_phase_index_slice,
                                                 phase_accuracy_tracker=label_phase_acc_tracker,
                                                 window_accuracy_tracker=total_phase_acc_tracker)

                sub_label_index_start = train_phase_index_slice[0]
                sub_label_index_end = label_phase_index_slice[1]
                index_slice_for_sampling = list(range(sub_label_index_start,
                                                      sub_label_index_end + 1))

                corrects_of_data_to_sample = []
                corrects_of_data_to_sample.extend(train_phase_acc_tracker.corrects)
                corrects_of_data_to_sample.extend(label_phase_acc_tracker.corrects)

                assert len(index_slice_for_sampling) == len(corrects_of_data_to_sample)
                print(f"IMG LENGTH TO BE SUB LABELED: {len(corrects_of_data_to_sample)}")
                print(f"SUB SAMPLE NUM: {sub_label_sr} from {len(corrects_of_data_to_sample)}")
                
                if label_time_budget < required_label_time:
                    print(f"experiment ended at label phase, total # of images: {len(total_phase_acc_tracker.corrects)} start index: {train_start_idx}")

                    self.corrects_per_phase.append(total_phase_acc_tracker.corrects)

                    self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                            phase_name="last infer only at sub label phase",
                                                            num_imgs=len(label_phase_acc_tracker.corrects),
                                                            exec_time=label_time_budget,
                                                            accuracy=label_phase_acc_tracker.avg_acc1,
                                                            num_train_imgs=-1,
                                                            train_epochs=-1,
                                                            num_valid_imgs=-1,
                                                            num_label_imgs=-1,
                                                            buffer_count=self.sample_buffer.count(),
                                                            buffer_capacity=self.sample_buffer.capacity)

                    return True, -1, -1
                
                self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                        phase_name=f"label phase",
                                                        num_imgs=len(label_phase_acc_tracker.corrects),
                                                        exec_time=label_time_budget,
                                                        accuracy=label_phase_acc_tracker.avg_acc1,
                                                        num_train_imgs=-1,
                                                        train_epochs=-1,
                                                        num_valid_imgs=-1,
                                                        num_label_imgs=sub_label_sr,
                                                        buffer_count=self.sample_buffer.count(),
                                                        buffer_capacity=self.sample_buffer.capacity)
                print(f"SUB LABELING TIME: {label_time_budget}")

                indices_per_label, label_metric = self.__sample_data_to_train(entire_dataset=self.entire_dataset,
                                                                              indices=index_slice_for_sampling,
                                                                              corrects=corrects_of_data_to_sample, 
                                                                              label_sr=sub_label_sr)

                if label_metric - valid_metric <= self.config.accuracy_threshold:
                    print(f"DRIFT OCCUR, reset buffer: {label_metric:.1f}% - {valid_metric:.1f}% "
                          f"({label_metric - valid_metric:.1f}%)")
                    
                    drift_occur = True
                    self.sample_buffer.reset()

                self.sample_buffer.push_data(indices_per_label=indices_per_label)

        # fisrt label phase or extra label due to drift
        if self.sample_buffer.count() == 0 or drift_occur:
            if drift_occur:
                name = "extra label"
                num_data_to_sample = self.config.max_num_imgs_to_label - self.sample_buffer.count()
                additional_label_start_idx = next_start_idx
                additional_label_start_time = next_start_time
            else:
                name = "first label"
                num_data_to_sample = self.num_data_for_first_label
                additional_label_start_idx = start_index
                additional_label_start_time = start_time

            # >>>>> get required time for the phase and check phase budget >>>>>
            required_additional_label_time = self.resource_allocator.allocate_label_time(num_data_to_sample=num_data_to_sample)
            
            # inference at label phase
            additional_label_phase_index_slice, \
            next_start_idx, \
            next_start_time, \
            additional_label_time_budget = split_dataset_indices_by_time(frame_latency_table=self.latency_table,
                                                                         start_index=additional_label_start_idx,
                                                                         start_time=additional_label_start_time,
                                                                         duration_time=required_additional_label_time)
            # <<<<< get required time for the phase and check phase budget <<<<<
            
            self.student_model_handler.infer(phase=2,
                                             dataset=self.entire_dataset,
                                             index_slice=additional_label_phase_index_slice,
                                             phase_accuracy_tracker=additional_label_phase_acc_tracker,
                                             window_accuracy_tracker=total_phase_acc_tracker)

            if additional_label_time_budget < required_additional_label_time:
                print(f"experiment ended at {name}, "
                      f"total # of images: {len(total_phase_acc_tracker.corrects)} start index: {train_start_idx}")

                self.corrects_per_phase.append(total_phase_acc_tracker.corrects)

                self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                        phase_name=f"last infer only at {name}",
                                                        num_imgs=len(additional_label_phase_acc_tracker.corrects),
                                                        exec_time=additional_label_time_budget,
                                                        accuracy=additional_label_phase_acc_tracker.avg_acc1,
                                                        num_train_imgs=-1,
                                                        train_epochs=-1,
                                                        num_valid_imgs=-1,
                                                        num_label_imgs=-1,
                                                        buffer_count=self.sample_buffer.count(),
                                                        buffer_capacity=self.sample_buffer.capacity)

                return True, -1, -1
            
            self.phase_info_logger.write_phase_info(phase_index=self.phase_index,
                                                    phase_name=name,
                                                    num_imgs=len(additional_label_phase_acc_tracker.corrects),
                                                    exec_time=additional_label_time_budget,
                                                    accuracy=additional_label_phase_acc_tracker.avg_acc1,
                                                    num_train_imgs=-1,
                                                    train_epochs=-1,
                                                    num_valid_imgs=-1,
                                                    num_label_imgs=num_data_to_sample,
                                                    buffer_count=self.sample_buffer.count(),
                                                    buffer_capacity=self.sample_buffer.capacity)

            # update sample buffer -> labeling
            index_slice_for_sampling = list(range(additional_label_phase_index_slice[0],
                                                  additional_label_phase_index_slice[1] + 1))
            corrects_of_data_to_sample = additional_label_phase_acc_tracker.corrects

            indices_per_label, label_metric = self.__sample_data_to_train(entire_dataset=self.entire_dataset,
                                                                          indices=index_slice_for_sampling,
                                                                          corrects=corrects_of_data_to_sample, 
                                                                          label_sr=num_data_to_sample)
            
            self.sample_buffer.push_data(indices_per_label=indices_per_label)

        # >>>>> log >>>>>
        if self.sample_buffer.count() > 0:
            print(f"train phase metric: {train_phase_acc_tracker.avg_acc1:.1f}%")
            print(f"label phase metric: {label_phase_acc_tracker.avg_acc1:.1f}%")

            if drift_occur:
                print(f"extra label metric:  {additional_label_phase_acc_tracker.avg_acc1:.1f}%")    
        else:
            print(f"first label metric:  {additional_label_phase_acc_tracker.avg_acc1:.1f}%")

        print(f"total phase metric:  {total_phase_acc_tracker.avg_acc1:.1f}%")
        
        print(f"# OF IMAGES THIS PHASE: {len(total_phase_acc_tracker.corrects)}")
        self.corrects_per_phase.append(total_phase_acc_tracker.corrects)

        total_corrects = []
        for c in self.corrects_per_phase:
            total_corrects.extend(c)

        num_window = self.config.window_time * self.config.fps
        for w in range(0, len(total_corrects), num_window):
            corrects_of_phase = total_corrects[w:w+(num_window)]

            if len(corrects_of_phase) == num_window:
                print(f"window #{int(w / num_window)}: {np.sum(corrects_of_phase) / len(corrects_of_phase) * 100:.1f}%")
        # <<<<< log <<<<<

        if next_start_idx == -1:
            is_done = True
        
        return is_done, next_start_idx, next_start_time

    def __sample_data_to_train(self,
                               entire_dataset: DaCapoDataset,
                               indices: List[int],
                               corrects: List[int],
                               label_sr: int) -> Tuple[List[List[int]], float]:
        assert (len(indices) == len(corrects))

        total_num = len(indices)
        num_imgs_to_sample = label_sr
        step = int(np.floor(total_num / num_imgs_to_sample))

        print(f"[SAMPLING] # of images: {total_num}, trying to sample: {num_imgs_to_sample} images...")

        sampled_indices = []
        sampled_corrects = []

        if step >= 1:
            for offset in range(0, indices[-1] - indices[0] + 1, step):
                if len(sampled_indices) == num_imgs_to_sample:
                    break
                
                sampled_indices.append(indices[offset])
                sampled_corrects.append(corrects[offset])
        else:
            sampled_indices = list(range(total_num))

        print(f"# of images: {total_num}, sampled # of images: {len(sampled_indices)} ({len(sampled_indices) / total_num * 100.:.1f}%)")

        train_dataset = TrainSampleDataset(ori_dataset=entire_dataset,
                                           indices=sampled_indices)
        
        indices_per_label = [[] for _ in range(self.config.num_classes)]

        assert (len(train_dataset) == len(sampled_indices))

        train_sample_cnt = 0
        data_loader = DataLoader(dataset=train_dataset,
                                 shuffle=False,
                                 pin_memory=True,
                                 batch_size=self.config.infer_batch_size,
                                 num_workers=self.config.num_workers)

        for inputs, targets, _ in tqdm(data_loader,
                                       desc="Checking sampled train dataset",
                                       unit=" batches"):
            for b in range(inputs.shape[0]):
                label = targets[b].item()
                indices_per_label[label].append(sampled_indices[train_sample_cnt])
                train_sample_cnt += 1

        assert (len(train_dataset) == int(np.sum([len(v) for v in indices_per_label])))

        return indices_per_label, np.sum(sampled_corrects) / len(sampled_corrects) * 100.


if __name__ == "__main__":
    args = parser.parse_args()
    simulator = DaCapoSimulator(config=Config(config_file_path=args.config_path))
    
    torch.cuda.synchronize()
    start_time = time.time()

    simulator.run()
    
    torch.cuda.synchronize()
    end_time = time.time()

    print(f"experiment time: {end_time-start_time:.1f} seconds")