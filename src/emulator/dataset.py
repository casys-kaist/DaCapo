import json
from PIL import Image
from pathlib import Path
from typing import List, Tuple
import torchvision.transforms as transforms
from torch.utils.data import Sampler, Dataset


class DaCapoDataset(Dataset):
    def __init__(self, scenario_dir: Path, transform, *args, **kargs):
        super().__init__(*args, **kargs)

        self.scenario_dir = scenario_dir
        self.json_path = scenario_dir / "ann.json"
        self.image_dir = scenario_dir / "images"

        if transform is not None:
            self.transform = transform
        else:
            self.transform = transforms.Compose([
                transforms.Resize((224, 224)),
                transforms.ToTensor(),
            ])

        imgs = json.load(open(self.json_path))

        self.samples = []

        for img in imgs:
            self.samples.append((
                str(self.image_dir / img["image"]),
                img["category"],
                img["image"].split(".jpg")[0]
            ))

    def __getitem__(self, index):
        img_path = self.samples[index][0]
        label = self.samples[index][1]
        img_name = self.samples[index][2]
        
        img = Image.open(img_path).convert("RGB")

        if self.transform:
            img = self.transform(img)
        else:
            img = img

        return img, label, img_name

    def __len__(self):
        return len(self.samples)
    
    def generate_frame_latency_table(self, fps: int):
        latency = 1. / fps

        return [latency * (i) for i in range(len(self.samples))]
    

class TrainSampleDataset(Dataset):
    def __init__(self, ori_dataset: DaCapoDataset, indices: List[int], *args, **kargs):
        super().__init__(*args, **kargs)

        self.scenario_dir = ori_dataset.scenario_dir
        self.json_path = ori_dataset.json_path
        self.image_dir = ori_dataset.image_dir

        self.transform = transforms.Compose([
            transforms.ToTensor(),
            transforms.Resize((224, 224)),
        ])

        self.samples = [ori_dataset.samples[i] for i in indices]

        self.labels = []
        for sample in self.samples:
            self.labels.append(sample[1])

    def __getitem__(self, index):
        img_path = self.samples[index][0]
        label = self.samples[index][1]
        img_name = self.samples[index][2]
        
        img = Image.open(img_path).convert("RGB")

        if self.transform:
            img = self.transform(img)
        else:
            img = img

        return img, label, img_name

    def __len__(self):
        return len(self.samples)
    
    def get_labels(self):
        return self.labels


def split_dataset_indices_by_time(frame_latency_table: List[float],
                                  start_index: int,
                                  start_time: float,
                                  duration_time: float) -> Tuple[Tuple[int], int, float, float]:
    end_time = start_time + duration_time
    for index, value in enumerate(frame_latency_table):
        if value < end_time:
            last_index = index

    index_slice = (start_index, last_index)

    if last_index + 1 >= len(frame_latency_table):
        # the time is not sufficient to the duration time
        # because this is the end for streaming data
        time = frame_latency_table[last_index] - frame_latency_table[start_index]
        return index_slice, -1, -1, time
    else:
        time = frame_latency_table[last_index + 1] - frame_latency_table[start_index]
        return index_slice, last_index + 1, frame_latency_table[last_index + 1], time


class CustomSampler(Sampler):
    def __init__(self, indices):
        self.indices = indices

    def __iter__(self):
        return iter(self.indices)

    def __len__(self):
        return len(self.indices)


if __name__ == "__main__":
    pass