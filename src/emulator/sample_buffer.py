import math
import numpy as np
from typing import List, Tuple
from emulator.config import Config
from emulator.dataset import TrainSampleDataset, DaCapoDataset


class SampleBuffer:
    def __init__(self, config: Config):
        self.config = config
        self.num_classes = self.config.num_classes
        self.capacity = self.config.sample_buffer_capacity

        self.indices: List[int] = []
        self.labels: List[int] = []

    def reset(self):
        self.indices: List[int] = []
        self.labels: List[int] = []

    def push_data(self, indices_per_label: List[List[int]]):
        len_per_label = [len(indices_per_label[l]) for l in range(len(indices_per_label))]
        cnt_per_label = [0 for _ in range(len(indices_per_label))]

        cnt = 0
        while True:
            is_done = True

            for l in range(self.num_classes):
                if cnt_per_label[l] < len_per_label[l]:
                    is_done = False

                    self.indices.insert(0, indices_per_label[l][cnt_per_label[l]])
                    self.labels.insert(0, l)
                    cnt_per_label[l] += 1
                    cnt += 1

            if is_done:
                break

        assert (len(self.indices) == len(self.labels))

        if len(self.indices) > self.capacity:
            self.indices = self.indices[0:self.capacity]
            self.labels = self.labels[0:self.capacity]

        # print(f"UPDATED EM: {len(self.indices)} / {self.capacity}")

    def count(self):
        assert (len(self.indices) == len(self.labels))

        return len(self.indices)

    def generate_dataset(self,
                         phase_index: int,
                         entire_dataset: DaCapoDataset,
                         num_data_for_train: int,
                         num_data_for_valid: int) -> Tuple[TrainSampleDataset]:
        num_data_to_sample = num_data_for_train + num_data_for_valid

        indices_per_label = [[] for _ in range(self.num_classes)]
        for i in range(len(self.labels)):
            index = self.indices[i]
            label = self.labels[i]

            indices_per_label[label].append(index)
        
        ratio_per_label = [0. for _ in range(self.num_classes)]
        for l in range(self.num_classes):
            ratio_per_label[l] = len(indices_per_label[l]) / len(self.indices)

        num_to_sample = [int(math.floor(num_data_to_sample * ratio_per_label[l])) for l in range(self.num_classes)]

        # label distribution
        # for l in range(self.num_classes):
        #     print(f"Label #{l}: {num_to_sample[l]} ({ratio_per_label[l] * 100:.1f}%), {len(indices_per_label[l])}")

        print(f"EM size: {len(self.indices)}, num_data_to_sample: {num_data_to_sample}")
        while int(np.sum(num_to_sample)) != num_data_to_sample:
            for l in range(self.num_classes):
                if int(np.sum(num_to_sample)) == num_data_to_sample:
                    break

                if num_to_sample[l] + 1 <= len(indices_per_label[l]):
                    num_to_sample[l] += 1

        indices = []
        labels = []

        for l in range(self.num_classes):
            if num_to_sample[l] == 0:
                continue

            step = int(math.floor(len(indices_per_label[l]) / num_to_sample[l]))
            
            cnt = 0
            # print(f"label: {l} ({len(indices_per_label[l])}, {num_to_sample[l]})")
            for offset in range(0, len(indices_per_label[l]), step):
                if num_to_sample[l] == cnt:
                    break

                indices.append(indices_per_label[l][offset])          
                labels.append(l)
        
                cnt += 1

        assert (len(labels) == len(indices))
        assert (len(labels) == num_data_to_sample)
        # print(f"{np.sum(num_to_sample)}")
        # print(f"labels: {len(labels)}, num data to sample: {num_data_to_sample}")

        cnt_per_label = [0 for _ in range(self.num_classes)]
        # RR: pre-knowledge for model
        while len(indices) != num_data_to_sample:
            for l in range(len(indices_per_label)):
                if len(indices) == num_data_to_sample:
                    break

                if cnt_per_label[l] < len(indices_per_label[l]):
                    indices.append(indices_per_label[l][cnt_per_label[l]])          
                    labels.append(l)
                    cnt_per_label[l] += 1
        
        assert (len(labels) == len(indices))
        indices_per_label = [[] for _ in range(self.num_classes)]
        for i in range(len(labels)):
            indices_per_label[labels[i]].append(indices[i])

        print(f"total sampled # images: {len(indices)}")

        indices_per_label = [[] for _ in range(self.num_classes)]
        for i in range(len(labels)):
            index = indices[i]
            label = labels[i]

            indices_per_label[label].append(index)

        # split sampled data into train/valid datasets
        train_indices = []
        valid_indices = []

        ratio = num_data_for_valid / num_data_to_sample
        for l in range(self.num_classes):
            num_valid = int(math.floor(len(indices_per_label[l]) * ratio))
            step = int(math.floor(1 / ratio))
            
            indices = []

            for offset in range(0, len(indices_per_label[l]), step):
                indices.append(indices_per_label[l][offset])

                if len(indices) == num_valid:
                    break

            valid_indices.extend(indices)
            train_indices.extend([item for item in indices_per_label[l] if item not in indices])

        train_dataset = TrainSampleDataset(ori_dataset=entire_dataset,
                                           indices=train_indices)
        valid_dataset = TrainSampleDataset(ori_dataset=entire_dataset,
                                           indices=valid_indices)
        
        return train_dataset, valid_dataset