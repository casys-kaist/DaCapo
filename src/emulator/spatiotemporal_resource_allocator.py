import math
import numpy as np
from emulator.config import Config
from emulator.model_handler import ModelPrecision
from emulator.dataset import TrainSampleDataset
from emulator.resource_allocator import ResourceAllocator, TRAIN, INFERENCE, LABEL


class SpatiotemporalResourceAllocator(ResourceAllocator):
    def __init__(self, config: Config):
        super().__init__(config=config)

        self.F_I = np.arange(self.total_row - 1) + 1

        self.bsa_f: int = None
        self.tsa_f: int = None

        self.allocate_spatial_resource()

    def allocate_spatial_resource(self):
        for f in self.F_I:
            iter_bsa = self.calculate_iter(m=ModelPrecision.MX6,
                                           f=f,
                                           batch_size=1,
                                           type=INFERENCE)
        
            drop_ratio_p1 = np.max([0., 1. - (1. / (self.fps * iter_bsa))])

            if drop_ratio_p1 != 0:
                continue

            self.bsa_f = f
            self.tsa_f = self.total_row - f

            break

    def allocate_train_time(self, train_dataset: TrainSampleDataset) -> float:
        iter_T = self.calculate_iter(m=ModelPrecision.MX9,
                                     f=self.tsa_f,
                                     batch_size=self.batch_size,
                                     type=TRAIN)
        
        train_time = iter_T * math.ceil(len(train_dataset) / self.batch_size)

        return train_time
    
    def allocate_valid_time(self, valid_dataset: TrainSampleDataset) -> float:
        iter_V = self.calculate_iter(m=ModelPrecision.MX6,
                                     f=self.tsa_f,
                                     batch_size=self.batch_size,
                                     type=INFERENCE)
        
        valid_time = iter_V * math.ceil(len(valid_dataset) / self.batch_size)

        return valid_time

    def allocate_label_time(self, num_data_to_sample: int) -> float:
        iter_L = self.calculate_iter(m=ModelPrecision.MX6,
                                     f=self.tsa_f,
                                     batch_size=1,
                                     type=LABEL)
        
        label_time = iter_L * num_data_to_sample

        return label_time