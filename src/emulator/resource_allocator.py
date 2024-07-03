import csv
import numpy as np
from pathlib import Path
from emulator.config import Config
from emulator.model_handler import ModelPrecision


TRAIN = "T"
INFERENCE = "I"
LABEL = "L"


class ResourceAllocator:
    def __init__(self, config: Config):
        self.config = config

        self.is_first = True

        # constants
        self.fps = self.config.fps # fps of streaming frame 
        self.m_T_p1 = ModelPrecision.MX9 # mantissa bit for inference at phase 1
        self.m_L_p2 = ModelPrecision.MX6 # mantissa bit for labeling at phase 2
        self.batch_size = self.config.train_batch_size # batch size for training
        self.total_row = self.config.total_row

        # variables
        self.M_I_p1 = [ModelPrecision.MX9, ModelPrecision.MX6, ModelPrecision.MX4] # mantissa bit for inference at phase 1
        self.M_I_p2 = [ModelPrecision.MX9, ModelPrecision.MX6, ModelPrecision.MX4] # mantissa bit for inference at phase 2
        self.F_I_p1 = np.arange(self.total_row - 1) + 1 # fraction of rows on systolic array for inference at phase 1
        self.F_I_p2 = np.arange(self.total_row - 1) + 1 # fraction of rows on systolic array for inference at phase 2

        self.device = self.config.device

        self.simulation_table = {}

        student = self.config.student_model
        teacher = self.config.teacher_model
        train_batch_size = self.config.train_batch_size
        table_path = Path(self.config.table_path)
        freq = self.config.freq

        student_train_batched_path= table_path / "exec_time_result" / student / f"{student}-train-b{train_batch_size}-freq_{freq}_mhz.csv"
        student_infer_batched_path= table_path / "exec_time_result" / student / f"{student}-infer-b{train_batch_size}-freq_{freq}_mhz.csv"
        student_infer_path= table_path / "exec_time_result" / student / f"{student}-infer-b1-freq_{freq}_mhz.csv"
        teacher_infer_path= table_path / "exec_time_result" / teacher / f"{teacher}-label-b1-freq_{freq}_mhz.csv"

        csv_readers = [
            csv.reader(open(student_train_batched_path)),
            csv.reader(open(student_infer_path)),
            csv.reader(open(student_infer_batched_path)),
            csv.reader(open(teacher_infer_path))
        ]

        for csv_reader in csv_readers:
            next(csv_reader)

            for row in csv_reader:
                name, exec_time = str(row[0]), float(row[1])

                if name in self.simulation_table.keys():
                    raise ValueError(f"duplicated: {name}")
                
                self.simulation_table[name] = exec_time

        for f in self.F_I_p1:
            name = f"{ModelPrecision.MX9}_{f}_{self.batch_size}_T"
            if name not in self.simulation_table:
                    raise ValueError(f"no information: {name}")
            
            name = f"{ModelPrecision.MX6}_{f}_1_L"
            if name not in self.simulation_table:
                    raise ValueError(f"no information: {name}")

            for m in self.M_I_p1:
                name = f"{m}_{f}_1_I"
                if name not in self.simulation_table:
                    raise ValueError(f"no information: {name}")
                
                name = f"{m}_{f}_{self.batch_size}_I"
                if name not in self.simulation_table:
                    raise ValueError(f"no information: {name}")
                
    def calculate_iter(self, m: int, f: int, batch_size: int, type: str):
        return self.simulation_table[f"{m}_{f}_{batch_size}_{type}"]