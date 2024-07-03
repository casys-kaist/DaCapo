from util.csv_logger import CsvLogger


HEADER = [
    "Phase index",
    "Phase name",
    "# of images",
    "Executed time",
    "Accuracy on inference",
    "Train: # of images",
    "Train epochs",
    "Valid: # of images",
    "Label: # of images",
    "Buffer count",
    "Buffer capacity"
]


class PhaseInfoLogger(CsvLogger):
    def __init__(self, file_path):
        super().__init__(file_path, HEADER)

    def write_phase_info(self,
                         phase_index,
                         phase_name,
                         num_imgs,
                         exec_time,
                         accuracy,
                         num_train_imgs,
                         train_epochs,
                         num_valid_imgs,
                         num_label_imgs,
                         buffer_count,
                         buffer_capacity):
        
        self.write_row([phase_index,
                        phase_name,
                        num_imgs,
                        exec_time,
                        accuracy,
                        num_train_imgs,
                        train_epochs,
                        num_valid_imgs,
                        num_label_imgs,
                        buffer_count,
                        buffer_capacity])