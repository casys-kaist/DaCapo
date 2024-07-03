import torch
import torch.nn as nn
from bfp.bfp_config import BfpConfig
from bfp.bfp_linear import BfpLinear


class CustomLinear(nn.Module):
    current_id = 0

    def __init__(self, input_features, output_features, bias, global_id: int):
        super(CustomLinear, self).__init__()
        self.id = CustomLinear.current_id
        CustomLinear.current_id += 1

        self.m_bit = BfpConfig.bfp_M_Bit

        self.global_id = global_id

        self.input_features = input_features
        self.output_features = output_features

        self.weight = nn.Parameter(torch.empty(output_features, input_features))

        self.bfp_gemms = {
            "fwd": None,
            "grad-a": None,
            "grad-w": None
        }

        if bias:
            self.bias = nn.Parameter(torch.empty(output_features))
        else:
            self.register_parameter('bias', None)

        self.intermediate_memory = {
            "weight-t": None,
            "grad-output-t": None,
            "input-t": None
        }

    def forward(self, input):
        return BfpLinear.apply(input, self.weight, self.bias, self.bfp_gemms, self.id, self.global_id, self.m_bit)


if __name__ == "__main__":
    pass