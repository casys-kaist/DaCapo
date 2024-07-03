import csv
from typing import List, Any


class CsvLogger:
    def __init__(self, file_path, header: List[str]):
        self.file_path = file_path
        self.header = header
        self.num_column = len(header)

        with open(file_path, "w") as f:
            csv_writer = csv.writer(f)
            csv_writer.writerow(self.header)

    def write_row(self, row: List[Any]):
        assert (len(row) == self.num_column)

        with open(self.file_path, "a") as f:
            csv_writer = csv.writer(f)
            csv_writer.writerow(row)