import math
import torch.nn as nn
from bfp.custom_linear import CustomLinear
from bfp.custom_conv2d import CustomConv2d


class BfpModelConverter:
    def __init__(self):
        self.total_layer = 0
        self.curr_layer = 0
        self.layer_num_fp = 0
        self.layer_num_bfp = 0

    def get_layer_cnt(self, module: nn.Module) -> int:
        self.__get_layer_cnt(module)

        return self.total_layer

    def __get_layer_cnt(self, module: nn.Module):
        for name, _ in module.named_children():
            child = getattr(module, name)
            child_cnt = 0
            for _ in child.children():
                child_cnt += 1

            if child_cnt == 0:
                layer = getattr(module, name)
                for _, param in layer.named_parameters():
                    if param.requires_grad:
                        pass

                if "Linear" in str(layer):
                    self.total_layer += 1
                elif "Conv" in str(layer) and layer.groups == 1:
                    self.total_layer += 1
            else:
                self.__get_layer_cnt(child)

    def convert(self, module: nn.Module, ratio: float = 1.0):
        self.total_layer = 0
        self.curr_layer = 0

        self.get_layer_cnt(module)

        self.layer_num_bfp = math.floor(ratio * self.total_layer)
        self.layer_num_fp = self.total_layer - self.layer_num_bfp

        self.__convert(module)

    def __convert(self, module: nn.Module):
        for name, _ in module.named_children():
            child = getattr(module, name)
            child_cnt = 0
            for _ in child.children():
                child_cnt += 1

            if child_cnt == 0:
                layer = getattr(module, name)
                for _, param in layer.named_parameters():
                    if param.requires_grad:
                        pass

                if "Linear" in str(layer):
                    # BfpConfig.layer_cnt += 1
                    # layer_cnt = BfpConfig.layer_cnt
                    self.curr_layer += 1
                    layer_cnt = self.curr_layer

                    if layer_cnt > self.layer_num_fp:
                        custom_layer = CustomLinear(input_features=layer.in_features, 
                                                    output_features=layer.out_features, 
                                                    bias=True if layer.bias is not None else False, 
                                                    global_id=layer_cnt)
                        custom_layer.weight.data = nn.Parameter(layer.weight.data.clone().detach())
                        if layer.bias is not None:
                            custom_layer.bias.data = nn.Parameter(layer.bias.data.clone().detach())
                        setattr(module, name, custom_layer)
                elif "Conv" in str(layer):
                    # BfpConfig.layer_cnt += 1
                    # layer_cnt = BfpConfig.layer_cnt
                    if layer.groups == 1:
                        self.curr_layer += 1
                        layer_cnt = self.curr_layer

                        if layer_cnt > self.layer_num_fp:
                            custom_layer = CustomConv2d(in_channels=layer.in_channels,
                                                        out_channels=layer.out_channels,
                                                        kernel_size=layer.kernel_size[0],
                                                        stride=layer.stride[0],
                                                        padding=layer.padding[0],
                                                        bias=True if layer.bias is not None else False,
                                                        global_id=layer_cnt)

                            if layer.weight.shape != custom_layer.weight.shape:
                                raise ValueError(f"shape of weight -> current layer: {layer.weight.shape}, custom layer: {custom_layer.weight.shape}")

                            custom_layer.weight.data = nn.Parameter(layer.weight.data.clone().detach())
                            if layer.bias is not None:
                                custom_layer.bias.data = nn.Parameter(layer.bias.data.clone().detach())
                            setattr(module, name, custom_layer)
                    elif layer.groups == layer.weight.shape[0] and layer.weight.shape[1] == 1:
                        pass
                    else:
                        raise ValueError(f"not supported conv2d configuration")
            else:
                self.__convert(child)



if __name__ == "__main__":
    pass