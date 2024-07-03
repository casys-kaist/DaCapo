FROM pytorch/pytorch:1.13.1-cuda11.6-cudnn8-runtime

WORKDIR /dacapo.artifacts
COPY . /dacapo.artifacts

ENV PROJECT_HOME="/dacapo.artifacts"
ENV DATA_HOME="/data"
ENV OUTPUT_ROOT="/data"
ENV RAY_DEDUP_LOGS=0
ENV NUM_GPU=4

RUN apt update
RUN apt install -y openssh-server

RUN apt-get update
RUN apt-get install -y git vim tmux

RUN pip install ray scipy timm matplotlib
RUN pip install -e .

ENTRYPOINT ["/bin/bash"]