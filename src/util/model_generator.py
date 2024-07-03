import os
import timm
import torch
import torch.nn as nn
from torchvision import models


MODEL_NAMES = [
    "resnet18",
    "resnet34",
    "vit_b_16",
    "vit_b_32",
    "wide_resnet50_2",
    "wide_resnet101_2",
]

VIT_NAMES = {
    "vit_b_16": "vit_base_patch16_224.augreg_in1k",
    "vit_b_32": "vit_base_patch32_224.augreg_in1k",
}


class ModelGenerator:
    @classmethod
    def generate_model(cls,
                       name: str,
                       num_classes: int,
                       device: torch.device,
                       weight_path: str = None) -> nn.Module:
        if name not in MODEL_NAMES:
            raise ValueError(f"invalid model name: {name}")
        
        if name in list(VIT_NAMES.keys()):
            if weight_path is not None:
                module = timm.create_model(VIT_NAMES[name], num_classes=num_classes)
                module.load_state_dict(torch.load(weight_path, map_location=device)["state_dict"])
            else:
                module = timm.create_model(VIT_NAMES[name], pretrained=True)
                last_linear = module.head
                module.head = nn.Linear(last_linear.in_features,
                                        num_classes,
                                        bias=False if last_linear.bias is None else True)
        else:
            if weight_path is not None:
                module = models.get_model(name, num_classes=num_classes)
                print(f"load weights... {os.path.basename(weight_path)}")
                module.load_state_dict(torch.load(weight_path, map_location=device)["state_dict"])
            else:
                module = models.get_model(name, pretrained=True)
                last_linear = module.fc
                module.fc = nn.Linear(last_linear.in_features,
                                      num_classes,
                                      bias=False if last_linear.bias is None else True)

        return module
