import os
import csv
import json
import argparse
import numpy as np
from pathlib import Path
from scipy.stats import gmean


parser = argparse.ArgumentParser(description="summarize results for figure. 9")
parser.add_argument("--output-root", type=str, help="root path of output")
parser.add_argument("--summary-root", type=str, default="./", help="root path of summarized results")


NAME_DICT = {
    "spatial": "DaCapo-Spatial",
    "spatiotemporal": "DaCapo-Spatiotemporal",
}

MODELS = [
    "resnet18",
    "vit_b_32",
    "resnet34",
]

CSV_RAW_DATA = {}

NUM_SCENARIO = 6
NUM_WARM_UP = 4
FPS = 30
WINDOW_TIME = 120
NUM_IMGS_PER_WINDOW = FPS * WINDOW_TIME


if __name__ == "__main__":
    args = parser.parse_args()
    output_root = Path(args.output_root)
    summary_root = Path(args.summary_root)

    name_keys = list(NAME_DICT.keys())

    for model in MODELS:
        # print(model)
        # scenario
        CSV_RAW_DATA[model] = {}

        for name in name_keys:
            CSV_RAW_DATA[model][name] = {}

            for s in range(1, NUM_SCENARIO+1):
                # print(s, end=" ")
                s_name = f"s{s}"
                # print(NAME_DICT[name], end=" ")
                # TODO: make it consistent to every baselines
                gmean_acc = 0.

                if "eomu" in name:
                    output_path = output_root / name / model / "output" / s_name / "result.json"
                    
                    if os.path.isfile(output_path):
                        corrects = []
                        with open(output_path) as f:
                            data = json.load(f)

                            for row in data:
                                corrects.extend(row)
                        corrects = corrects[NUM_WARM_UP * NUM_IMGS_PER_WINDOW:]
                        accs = []
                        for offset in range(0,len(corrects),NUM_IMGS_PER_WINDOW):
                            sub_corrects = corrects[offset:offset+NUM_IMGS_PER_WINDOW]
                            accs.append(np.sum(sub_corrects) / len(sub_corrects) * 100.)
                        gmean_acc = gmean(accs)
                else:
                    output_path = output_root / name / model / "output" / s_name / "accuracy_per_window.csv"

                    if os.path.isfile(output_path):
                        with open(output_path) as f:
                            row_cnt = 0
                            csv_reader = csv.reader(f)
                            for row in csv_reader:
                                row_cnt += 1
                                
                        if row_cnt == 15:
                            accs = []
                            with open(output_path) as f:
                                csv_reader = csv.reader(f)

                                next(csv_reader)
                                
                                for row in csv_reader:
                                    window_idx, acc = row[0], float(row[1])
                                    accs.append(acc)
                            
                            accs = accs[NUM_WARM_UP:]
                            gmean_acc = gmean(accs)
                            # print(gmean_acc)
                
                CSV_RAW_DATA[model][name][s_name] = gmean_acc
            # print()

    # make gmean
    for model in MODELS:
        for name in name_keys:
            accs_of_s = []

            for s in range(1, NUM_SCENARIO+1):
                s_name = f"s{s}"
                accs_of_s.append(CSV_RAW_DATA[model][name][s_name])

            CSV_RAW_DATA[model][name]["gmean"] = gmean(accs_of_s)

    # make summary file
    summary_dir = Path(summary_root / "end-to-end-accuracy")
    summary_dir.mkdir(parents=True, exist_ok=True)
    summary_file = summary_dir / "end-to-end-accuracy-sheet.csv"
    f = open(summary_file, "w")
    csv_writer = csv.writer(f)

    header = ["Model", "Scenario"]
    for name in name_keys:
        header.append(NAME_DICT[name])

    csv_writer.writerow(header)
    
    for model in MODELS:
        print(model)
        for s in range(1, NUM_SCENARIO+1):
            s_name = f"s{s}"
            print(s_name, end=" ")
            row = [model, f"S{s}"]
            for name in name_keys:
                print(CSV_RAW_DATA[model][name][s_name], end=" ")
                row.append(CSV_RAW_DATA[model][name][s_name])
            print()
            csv_writer.writerow(row)


        row = [model, "GMEAN"]
        print("gm", end=" ")
        for name in name_keys:
            print(CSV_RAW_DATA[model][name]["gmean"], end=" ")
            row.append(CSV_RAW_DATA[model][name]["gmean"])
        csv_writer.writerow(row)
        print()
