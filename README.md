# DaCapo: Accelerating Continuous Learning in Autonomous Systems for Video Analytics
* [0. Clone GitHub repository](#0-clone-github-repository)
* [1. Installation](#1-installation)
  + [1.1. Setup Docker image](#11-setup-docker-image)
  + [1.2. Download data](#12-download-data)
  + [1.3. Generate Docker container](#13-generate-docker-container)
* [2. Run experiment](#2-run-experiment)
  + [2.1. Experiment with DaCapo system simulator](#21-experiment-with-dacapo-system-simulator)
* [3. Summarize experiment result](#3-summarize-experiment-result)
  + [3.1. Run script](#31-run-script)
  + [3.2. Expected summarized result](#31-expected-summarized-result)
* [4. Citation](#4-citation)


#### Tested environment
- Host
  - Docker 24.0
- Docker image
  - Ubuntu 18.04
- GPU
  - NVIDIA RTX 3090 (24GB) *multiple GPUs can run experiments in parallel

## 0. Clone GitHub repository

```shell
git clone --recursive https://github.com/yoonsung-kim/DaCapo.artifacts.git
cd DaCapo.artifacts
```

## 1. Installation

### 1.1. Setup Docker image

Pull base Docker images.

```shell
docker pull pytorch/pytorch:1.13.1-cuda11.6-cudnn8-runtime
```

Generate Docker images for the systems:

1. Build Docker image on the system with NVIDIA RTX 3090

We can set ```NUM_GPU``` environment variable in Dockerfile to make the system run experiments in parallel.

```shell
# at docker/Dockerfile
...
# set the number of GPU
NUM_GPU=<integer>
...
```

```shell
docker build --no-cache -t dacapo-simulation -f docker/Dockerfile .
```

### 1.2. Download data

1. Download data.tar (about 7.5GB). This data includes all scenario datasets and the weights of DNN models. The download links are below:
    - [Google Drive](https://drive.google.com/drive/folders/1rNTPJXrPlkestSTRoxXDQZA93hTOZxmy?usp=sharing)

2. Decompress ```data.tar```. The directory hierarchy is as below:

```shell
data/
├── dataset # all BDD100K scenario datasets
└── weight  # initial weights for benchmarks
```

### 1.3. Generate Docker container

```shell
# Run script on the system with NVIDIA RTX 3090
docker run -it -v <path-to-data-directory>:/data --ipc=host --name dacapo-simulation --gpus all dacapo-simulation:latest
```

## 2. Run experiment

All experiments generate their results in ```$OUTPUT_ROOT``` directory defined in Dockerfiles. The path in a Docker container is ```/data```, and the system saves the results in the ```/data/output``` directory. Note that the ```/data``` is mounted to the host system (i.e., ```docker run ... -v <path-to-data-directory>:/data ...```).

```shell
# in Dockerfile
... 
ENV OUTPUT_ROOT="/data"
...
```

The output directories for the both systems have the same hierarchy as follows:

```shell
data/output/
├── spatial
└── spatiotemporal
```

After running all experiments, we should combine the directories into a single ```output``` directory. Once this is done, we can summarize experiment results by executing post-processing scripts.

### 2.1. Experiment with DaCapo system simulator

Run script as follows:

```shell
./script/run_all_benchmarks.sh
```

## 3. Summarize experiment result

### 3.1. Run script

1. Set environment variable

```shell
export OUTPUT_DIR=<output-directory>
export SUMMARY_DIR=<directory-to-save-summarized-result>
```

2. Run script

```shell
cd script/summarize
python ./end-to-end-accuracy.py --output-root $OUTPUT_DIR --summary-root $SUMMARY_DIR
```

### 3.2. Expected summarized result

These scripts generate summarized results in the ```$SUMMARY_DIR``` directory as follows:

```shell
$SUMMARY_DIR/
└── end-to-end-accuracy/
    └── end-to-end-accuracy-sheet.csv
```

## 4. Citation

If DaCapo proves useful or insightful for your research, please cite our paper using the following BibTeX entry:

```
@inproceedings {10609643,
author = {Y. Kim and C. Oh and J. Hwang and W. Kim and S. Oh and Y. Lee and H. Sharma and A. Yazdanbakhsh and J. Park},
title = {DACAPO: Accelerating Continuous Learning in Autonomous Systems for Video Analytics},
booktitle = {2024 ACM/IEEE 51st Annual International Symposium on Computer Architecture (ISCA)},
year = {2024},
pages = {1246-1261},
doi = {10.1109/ISCA59077.2024.00093}
}
```