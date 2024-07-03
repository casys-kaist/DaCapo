import torch
from ctypes import *
from bfp.bfp_config import BfpConfig
from bfp.torch_mx_bfp_converter import convert_fp_to_mx, convert_mx_to_fp


class BfpGemm:
    def __init__(self,
                 lhs_shape: torch.Size,
                 is_lhs_bfp: bool,
                 lhs_name: str,

                 rhs_shape: torch.Size,
                 is_rhs_bfp: bool,
                 rhs_name: str,

                 use_stochastic_rounding: bool,
                 layer_index: int,
                 m_bit: int):
        self.device = "cuda"
        self.layer_index = layer_index

        self.m_bit = m_bit

        self.lhs_name = lhs_name
        self.rhs_name = rhs_name

        self.is_lhs_bfp = is_lhs_bfp
        self.is_rhs_bfp = is_rhs_bfp

        self.use_stochastic_rounding = use_stochastic_rounding

        self.lhs_cvt_module = self.decide_conversion_module(self.is_lhs_bfp)
        self.rhs_cvt_module = self.decide_conversion_module(self.is_rhs_bfp)

        self.lhs_wrapper = None
        self.lhs_fp_from_bfp = None
        if self.is_lhs_bfp:
            if not BfpConfig.use_mx:
                raise ValueError("not support non-mx bfp format")

        self.rhs_wrapper = None
        self.rhs_fp_from_bfp = None
        if self.is_rhs_bfp:
            if not BfpConfig.use_mx:
                raise ValueError("not support non-mx bfp format")

    def get_fp(self, src_tensor: torch.Tensor, **kwargs):
        return src_tensor
    
    def get_fp_from_mx_bfp(self, src_tensor: torch.Tensor, **kwargs):
        S, E, M, _ = convert_fp_to_mx(group_size=(BfpConfig.group_size, 2),
                                      mx_e_bit=(8, 1),
                                      mx_m_bit=self.m_bit,
                                      tensor=src_tensor,
                                      use_SR=self.use_stochastic_rounding)
        return convert_mx_to_fp(S=S, E=E, M=M, mx_m_bit=self.m_bit)

    def decide_conversion_module(self, is_bfp: bool):
        if is_bfp:
            if BfpConfig.use_mx:
                return self.get_fp_from_mx_bfp
            else:
                raise ValueError("not support non-mx bfp format")
        else:
            return self.get_fp

    def run(self, lhs: torch.Tensor, rhs: torch.Tensor):
        lhs_input = self.lhs_cvt_module(src_tensor=lhs,
                                        dst_tensor=self.lhs_fp_from_bfp,
                                        wrapper=self.lhs_wrapper,
                                        is_lhs=True)
        rhs_input = self.rhs_cvt_module(src_tensor=rhs,
                                        dst_tensor=self.rhs_fp_from_bfp,
                                        wrapper=self.rhs_wrapper,
                                        is_lhs=False)

        rhs_num_dims = rhs_input.ndim
        perm_order = list(range(rhs_num_dims - 2)) + [rhs_num_dims - 1,
                                                      rhs_num_dims - 2]

        return torch.matmul(lhs_input, rhs_input.permute(*perm_order))


if __name__ == "__main__":
    pass