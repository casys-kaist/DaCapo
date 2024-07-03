import math
import numpy as np
from emulator.model_handler import ModelPrecision
from emulator.resource_allocator import ResourceAllocator, TRAIN, INFERENCE, LABEL


class SpatialResourceAllocator(ResourceAllocator):
    def __init__(self, config):
        super().__init__(config)

        self.is_first = True

        self.static_epochs = self.config.initial_epoch
        self.static_m_T_p1 = ModelPrecision.MX9
        self.static_m_I_p1 = self.config.initial_m_I_p1
        self.static_m_L_p2 = ModelPrecision.MX6
        self.static_m_I_p2 = self.config.initial_m_I_p2
        self.static_f_I_p1 = self.config.initial_f_I_p1
        self.static_f_I_p2 = self.config.initial_f_I_p2
        self.static_sampling_rate = self.config.initial_sampling_rate

        self.static_p1_time: float = None
        self.static_p2_time: float = None

    def allocate_static_spatial_resource(self):
        if self.is_first:
            results = []

            for m_I_p2 in self.M_I_p2:
                for f_I_p2 in self.F_I_p2:
                    for m_I_p1 in self.M_I_p1:
                        value = self.allocate_without_training(m_I_p1=m_I_p1,
                                                               m_I_p2=m_I_p2,
                                                               f_I_p1=self.total_row,
                                                               f_I_p2=f_I_p2)
                
                        if value is not None:
                            results.append(value)

            results = sorted(results, key=lambda x: x["p1_time"])

            assert (len(results) > 0)

            result = results[0]

            self.static_m_I_p1 = result["m_I_p1"]
            self.static_m_I_p2 = result["m_I_p2"]
            self.static_f_I_p1 = result["f_I_p1"]
            self.static_f_I_p2 = result["f_I_p2"]
            self.static_epochs = 0

            self.static_p1_time = result["p1_time"]
            self.static_p2_time = result["p2_time"]

            self.is_first = False

            return
        
        self.static_m_I_p1 = self.config.initial_m_I_p1
        self.static_m_I_p2 = self.config.initial_m_I_p2
        self.static_f_I_p1 = self.config.initial_f_I_p1
        self.static_f_I_p2 = self.config.initial_f_I_p2
        self.static_epochs = self.config.initial_epoch

        for f_bsa in [self.static_f_I_p1, self.static_f_I_p2]:
            iter_bsa = self.calculate_iter(m=ModelPrecision.MX6,
                                           f=f_bsa,
                                           batch_size=1,
                                           type=INFERENCE)
            drop_ratio = np.max([0., 1. - (1. / (self.fps * iter_bsa))])

            assert (drop_ratio == 0)

        fps = self.config.fps
        window_time = self.config.window_time
        num_sample = int(math.floor((fps * window_time) * self.static_sampling_rate))

        iter_T_p1 = self.calculate_iter(m=self.m_T_p1,
                                        f=self.total_row - self.static_f_I_p1,
                                        batch_size=self.batch_size,
                                        type=TRAIN)
        
        iter_L_p2 = self.calculate_iter(m=self.m_L_p2,
                                        f=self.total_row - self.static_f_I_p2,
                                        batch_size=1,
                                        type=LABEL)
        
        p2_time = iter_L_p2 * num_sample
        p1_time = self.config.window_time - p2_time
        assert (p1_time + p2_time <= window_time)

        p1_train_time = iter_T_p1 * (math.ceil(num_sample / self.batch_size) * self.static_epochs)
        assert (p1_train_time <= p1_time)

        self.static_p1_time = p1_time
        self.static_p2_time = p2_time

    def allocate_without_training(self,
                                  m_I_p1: int,
                                  m_I_p2: int,
                                  f_I_p1: int,
                                  f_I_p2: int):
        window_time = self.config.window_time

        iter_I_p1 = self.calculate_iter(m=m_I_p1,
                                        f=f_I_p1,
                                        batch_size=1,
                                        type=INFERENCE)
        drop_ratio_p1 = np.max([0., 1. - (1. / (self.fps * iter_I_p1))])
        
        iter_I_p2 = self.calculate_iter(m=m_I_p2,
                                        f=f_I_p2,
                                        batch_size=1,
                                        type=INFERENCE)
        drop_ratio_p2 = np.max([0., 1. - (1. / (self.fps * iter_I_p2))])

        if drop_ratio_p1 != 0 or drop_ratio_p2 != 0:
            return None

        num_total_imgs = self.config.fps * self.config.window_time
        sr_to_sample = self.static_sampling_rate
        num_imgs_to_sample = int(math.floor(num_total_imgs * sr_to_sample))

        iter_L_p2 = self.calculate_iter(m=self.m_L_p2,
                                        f=self.total_row - f_I_p2,
                                        batch_size=1,
                                        type=LABEL)
        
        p2 = iter_L_p2 * num_imgs_to_sample
        p1 = window_time - p2

        if p1 <= 0 or p1 + p2 > window_time:
            return None
        
        return {
            "p1_time": p1,
            "m_I_p1": m_I_p1,
            "f_I_p1": f_I_p1,
            "p2_time": p2,
            "m_I_p2": m_I_p2,
            "f_I_p2": f_I_p2
        }