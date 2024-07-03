import torch.nn as nn
from bfp.bfp_model_converter import BfpModelConverter
from bfp.bfp_config import BfpConfig


class BfpModelPrecisionChanger:
    def __init__(self):
        self.m_bit = 7

    def change_precision(self, module: nn.Module, m_bit: int):
        self.m_bit = m_bit
        self.__change_precision(module)

    def __change_precision(self, module: nn.Module):
        for name, _ in module.named_children():
            child = getattr(module, name)
            child_cnt = 0
            for _ in child.children():
                child_cnt += 1

            if child_cnt <= 1:
                layer = getattr(module, name)
                for _, param in layer.named_parameters():
                    if param.requires_grad:
                        pass

                layer_name = str(layer).split("(")[0]

                if "CustomLinear" in layer_name or "CustomConv2d" in layer_name:
                    layer.m_bit = self.m_bit

                    bfp_gemms = layer.bfp_gemms
    
                    for k in bfp_gemms.keys():
                        if bfp_gemms[k] is not None: 
                            bfp_gemms[k].m_bit = self.m_bit
            else:
                self.__change_precision(child)

if __name__ == "__main__":
    import torch
    BfpConfig.use_bfp = True
    
    BfpConfig.bfp_M_Bit = 7
    BfpConfig.group_size = 16

    BfpConfig.use_mx = True

    BfpConfig.apply_single_bfp_tensor = True
    BfpConfig.prec_activation = True
    BfpConfig.prec_weight = True
    BfpConfig.prec_gradient = True

    from torchvision import models
    model = models.__dict__["resnet18"]()
    # print(model)
    # model = models.resnet50()
    model_converter = BfpModelConverter()
    model_converter.convert(module=model, ratio=1.0)
    model = model.cuda()

    inputs = torch.randn(1, 3, 224, 224).to("cuda")
    _ = model(inputs)

    precision_changer = BfpModelPrecisionChanger()
    precision_changer.change_precision(model, 2)

    _ = model(inputs)

    # BfpModelConverter.search(model)
    print(model)

    print(f"total layer: {model_converter.total_layer} (FP: {model_converter.layer_num_fp}, BFP: {model_converter.layer_num_bfp})")