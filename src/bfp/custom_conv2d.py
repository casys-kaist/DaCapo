import math
import torch
import torch.nn as nn
from bfp.bfp_config import BfpConfig
from bfp.bfp_linear import BfpLinear


class CustomConv2d(nn.Module):
    current_id = 0

    def __init__(self, 
                 in_channels,
                 out_channels,
                 kernel_size,
                 stride,
                 padding,
                 bias,
                 global_id: int):
        super(CustomConv2d, self).__init__()

        self.id = CustomConv2d.current_id
        CustomConv2d.current_id += 1

        self.m_bit = BfpConfig.bfp_M_Bit

        self.global_id = global_id
        self.in_channels = in_channels
        self.out_channels = out_channels
        self.kernel_size = kernel_size
        self.stride = stride
        self.padding = padding

        self.weight = nn.Parameter(
            torch.empty(
                self.out_channels,
                self.in_channels,
                self.kernel_size,
                self.kernel_size
            ))

        if bias:
            self.bias = nn.Parameter(
                torch.empty(
                    self.out_channels
                )
            )
        else:
            self.register_parameter('bias', None)

        self.bfp_gemms = {
            "fwd": None,
            "grad-w": None,
            "grad-a": None
        }

        self.im2col = torch.nn.Unfold(
            kernel_size=kernel_size,
            padding=padding,
            stride=stride
        )

    def forward(self, inputs):
        if inputs.shape[2] != inputs.shape[3]:
            raise ValueError(f"not supported inputs width, and height ({inputs.shape[2]}, {inputs.shape[3]})")

        batch_size = inputs.shape[0]
        out_width = math.floor(((inputs.shape[2] + 2 * self.padding - (self.kernel_size)) / self.stride) + 1)

        inputs = self.im2col(inputs)
        inputs = inputs.permute(0, 2, 1).reshape(-1, inputs.shape[1])
        weight = self.weight.reshape(self.out_channels, -1)

        outputs =  BfpLinear.apply(
            inputs, 
            weight, self.bias, 
            self.bfp_gemms,
            self.id,
            self.global_id,
            self.m_bit)

        outputs = outputs.reshape(batch_size, out_width, out_width, self.out_channels)
        outputs = outputs.permute(0, 3, 1, 2).contiguous()

        return outputs


if __name__ == "__main__":
    pass