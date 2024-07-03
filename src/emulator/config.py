import json
import torch
from typing import Tuple


class Config:
    def __init__(self, config_file_path: str):
        self.config_file_path = config_file_path

        self.cl_type: str = None
        self.task_type: str = None

        self.seed: int = None

        self.num_classes: int = None
        
        self.window_time: int = None
        self.fps: int = None
        self.freq: int = None # frequency in MHz
        
        self.train_batch_size: int = None
        self.infer_batch_size: int = None # only for fast simulation, inference batch is 1 due to live streaming of input images
        
        self.lr: float = None
        self.momentum: float = None
        self.weight_decay: float = None
        self.num_workers: int = None
        
        self.student_model: str = None
        self.student_weight: str = None
        self.teacher_model: str = None
        self.teacher_weight: str = None
        
        self.device: torch.device = None
        
        self.table_path: str = None
        self.output_root: str = None
        
        self.total_row: int = None
        
        self.student_image_size: Tuple[int] = None
        self.teacher_image_size: Tuple[int] = None
        
        self.data_path: str = None
        self.annotation_path: str = None
        self.scenario_path: str = None

        self.initial_epoch: int = None
        self.initial_sampling_rate: float = None
        self.initial_m_I_p1: int = None
        self.initial_f_I_p1: int = None
        self.initial_m_I_p2: int = None
        self.initial_f_I_p2: int = None

        self.accuracy_threshold: float = None
        self.sample_buffer_capacity: int = None
        self.min_num_imgs_to_label: int = None
        self.max_num_imgs_to_label: int = None
        self.num_imgs_to_train: int = None
        self.num_imgs_to_valid: int = None
        
        with open(config_file_path) as f:
            cfg_dict = json.load(f)

            for key in cfg_dict.keys():
                if "device" in key:
                    setattr(self, key, torch.device(cfg_dict[key]))
                elif "image_size" in key:
                    image_size = cfg_dict[key]
                    setattr(self, key, (image_size[0], image_size[1]))
                else:
                    setattr(self, key, cfg_dict[key])