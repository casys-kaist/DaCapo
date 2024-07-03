import os
import ray
import json
import shlex
import argparse
import subprocess
from pathlib import Path


parser = argparse.ArgumentParser(description="dacapo experiment launcher")
parser.add_argument("--cl-type", type=str, help="type of continuous learning algorithm on dacapo hardware")
parser.add_argument("--seed", type=int, default=128, help="seed")
parser.add_argument("--model", type=str, nargs="+", default=["resnet18"], help="list of model names (resnet18, resnet34, vit_b_32)")


CL_TYPES = [
    "spatial",
    "spatiotemporal",
]

PROJECT_HOME = Path(os.environ["PROJECT_HOME"])
DATA_HOME = Path(os.environ["DATA_HOME"])
OUTPUT_ROOT = Path(os.environ["OUTPUT_ROOT"])

TABLE_PATH = PROJECT_HOME / "data/tables"

CONFIG_DIRS = {
    "resnet18": PROJECT_HOME / "data/config/resnet18-wide_resnet50_2",
    "resnet34": PROJECT_HOME / "data/config/resnet34-wide_resnet101_2",
    "vit_b_32": PROJECT_HOME / "data/config/vit_b_32-vit_b_16",
}

STUDENT_WEIGHTS = {
    "resnet18": DATA_HOME / "weight/resnet18.pth",
    "resnet34": DATA_HOME / "weight/resnet34.pth",
    "vit_b_32": DATA_HOME / "weight/vit_b_32.pth",
}

MODELS = [
    "resnet18",
    "resnet34",
    "vit_b_32",
]

NUM_GPU = int(os.environ["NUM_GPU"])

ray.init(num_gpus=NUM_GPU,
         num_cpus=48)


@ray.remote(num_gpus=1)
def run_on_single_gpu(cl_type: str,
                      model: str,
                      # output_dir: str,
                      output_root: Path,
                      weight_path: str,
                      src_config_path: str,
                      scenario_path: str):
    log_path = Path(output_root / "log")
    log_path.mkdir(parents=True, exist_ok=True)

    config_path = Path(output_root / "config")
    config_path.mkdir(parents=True, exist_ok=True)

    # generate output folder
    scenario_name = os.path.basename(scenario_path).split(".")[0]
    output_path = Path(output_root / "output" / f"{scenario_name}")
    output_path.mkdir(parents=True, exist_ok=True)

    print(f"[experiment info] model: {model}, "
          f"cl type: {cl_type}, "
          f"scenario: {scenario_name}")
    
    # set config
    config = json.load(open(src_config_path))
    config["lr"] = 1e-3
    config["num_classes"] = 9
    config["infer_batch_size"] = 64
    config["num_workers"] = 8
    config["student_weight"] = weight_path
    config["table_path"] = str(TABLE_PATH)
    config["output_root"] = str(output_path)
    config["scenario_path"] = scenario_path

    log_name = f"{model}-{scenario_name}"

    dst_config_path = str(config_path / f"{log_name}.json")
    json.dump(config, open(dst_config_path, "w"), indent=4)

    # run
    cmd = generate_cmd(cl_type=cl_type,
                       config_path=dst_config_path)
    
    out_path = log_path / f"{log_name}.stdout"
    err_path = log_path / f"{log_name}.stderr"
    
    with open(out_path, "wb") as out, open(err_path, "wb") as err:
        handle = subprocess.Popen(
            shlex.split(cmd),
            env=dict(os.environ),
            stdout=out, stderr=err)
        handle.communicate()


def generate_cmd(cl_type: str, config_path: str):
    script_path = PROJECT_HOME / "script"

    if "spatiotemporal" in cl_type:
        file_name = "run_dacapo_spatiotemporal.py"        
    elif "spatial" in cl_type:
        file_name = "run_dacapo_spatial.py"
    elif "ekya" in cl_type:
        file_name = "run_ekya_on_dacapo.py"
    else:
        raise ValueError(f"not supported cl type: '{cl_type}'")

    return f"python {str(script_path / file_name)} " \
           f"--config-path {config_path}"

def generate_config_sub_name(cl_type: str):
    if "spatiotemporal" == cl_type:
        return "dacapo_spatiotemporal.json"
    elif "spatial" == cl_type:
        return "dacapo_spatial.json"
    elif "spatiotemporal-active_cl" == cl_type:
        return "dacapo_spatiotemporal-active_cl.json"
    elif "spatial-active_cl" == cl_type:
        return "dacapo_spatial-active_cl.json"
    elif "ekya" == cl_type:
        return "ekya_on_dacapo.json"
    else:
        raise ValueError(f"not supported cl type: '{cl_type}'")


if __name__ == "__main__":
    args = parser.parse_args()
    cl_type = args.cl_type
    seed = args.seed
    model_list = args.model

    if cl_type not in CL_TYPES:
        raise ValueError(f"invalid cl type: '{cl_type}', must be one of {CL_TYPES}")
    
    config_sub_name = generate_config_sub_name(cl_type=cl_type)

    SCNEARIO_PATH = DATA_HOME / "dataset/bdd100k/6-scenarios"

    scenario_paths = sorted(os.listdir(SCNEARIO_PATH))

    tasks = []

    for model in model_list:
        cl_name = cl_type
        if cl_type == "ekya":
            cl_name = "dacapo-ekya"

        output_root = OUTPUT_ROOT / "output" / cl_name / model

        weight_path = STUDENT_WEIGHTS[model]
        config_dir = CONFIG_DIRS[model]
        src_config_paths = os.listdir(config_dir)

        # output_name = f"{model}"
        # output_dir = Path(OUTPUT / output_name)
        # output_dir.mkdir(parents=True, exist_ok=True)
        
        for scenario_path in sorted(scenario_paths):
            for src_config_path in src_config_paths:
                if config_sub_name not in src_config_path:
                    continue

                task = run_on_single_gpu.remote(cl_type=cl_type,
                                                model=model,
                                                output_root=output_root,
                                                weight_path=str(weight_path),
                                                src_config_path=str(config_dir / src_config_path),
                                                scenario_path=str(SCNEARIO_PATH / scenario_path))
                tasks.append(task)

    for task in tasks:
        ray.get(task)
