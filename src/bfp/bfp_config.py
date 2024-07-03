import json
import torch
from typing import Dict, Any


class PrecisionFlag:
    FP = 0
    BFP = 1


class BfpConfig:
    use_bfp = False
    bfp_M_Bit = None
    group_size = None

    use_mx = False

    use_multi_exp = False
    block_wise_multi_exp = False
    num_shared_exponent = None

    fwd_prec = None # PrecisionFlag.FP
    bwd_a_prec = None # PrecisionFlag.FP
    bwd_w_prec = None # PrecisionFlag.FP
    
    apply_single_bfp_tensor = False
    prec_activation = False
    prec_weight = False
    prec_gradient = False

    replace_outlier_bfp_to_fp = False
    data_format_to_replace = None # PrecisionFlag.FP

    batch_size_to_remember = None # decide in runtime

    # replace blocks to zero
    A_repl_block_to_zero = False
    A_threshold = None
    W_repl_block_to_zero = False
    W_threshold = None
    G_repl_block_to_zero = False
    G_threshold = None

    ##############################
    # use, but NOT touched
    ##############################
    f_st = True
    a_st = True 
    w_st = True


    ##############################
    # only for test
    ##############################
    should_log = False
    log_path = None
    is_validation = False
    curr_epoch = 0
    curr_iteration = 0
    iteration_to_stop = None
    exp_name = None


    ##############################
    # NOT used
    ##############################
    use_flex_bfp = False
    is_fast = False

    f_thres = False
    a_thres = False
    w_thres = False
    threshold = None

    use_shift = False
    static_2nd_exp = False

    use_reconfig = False
    reconfig_m = False
    reconfig_g = False
    reconfig_e = False

    apply_high_prec_to_both_ends = False

    layer_cnt = 0
    first_layer_id = 1
    last_layer_id = None

    # exponent-aware mantissa
    exp_aware_mantissa = False
    # bit_list = np.array([2, 4, 6, 8])
    bit_list = torch.tensor([2.0, 4.0, 6.0, 8.0], dtype=torch.float, device="cuda")

    # bit logging info
    fwd_lhs_bits = []
    fwd_rhs_bits = []
    bwd_a_lhs_bits = []
    bwd_a_rhs_bits = []
    bwd_w_lhs_bits = []
    bwd_w_rhs_bits = []


    @classmethod
    def generate_template(cls) -> Dict[str, Any]:
        result: Dict[str, Any] = {
            "use-bfp": False,
            "bfp-M-Bit": None,
            "group-size": None,

            "use-mx": False,

            "use-multi-exp": False,
            "block-wise-multi-exp": False,
            "num-shared-exponent": None,

            "fwd-prec": 0,
            "bwd-a-prec": 0,
            "bwd-w-prec": 0,
            
            "apply-single-bfp-tensor": False,
            "prec-activation": False,
            "prec-weight": False,
            "prec-gradient": False,

            "replace-outlier-bfp-to-fp": False,
            "data-format-to-replace": None,

            "A_repl_block_to_zero": False,
            "A_threshold": None,
            "W_repl_block_to_zero": False,
            "W_threshold": None,
            "G_repl_block_to_zero": False,
            "G_threshold": None,

            "should-log": None,
            "iteration-to-stop": None
        }

        return result


    @classmethod
    def load_config_from_json(cls, json_path: str):
        with open(json_path) as f:
            json_data = json.load(f)

        BfpConfig.use_bfp = json_data["use-bfp"]
        BfpConfig.bfp_M_Bit = json_data["bfp-M-bit"]
        BfpConfig.group_size = json_data["group-size"]

        BfpConfig.use_mx = json_data["use-mx"]

        BfpConfig.use_multi_exp = json_data["use-multi-exp"]
        BfpConfig.block_wise_multi_exp = json_data["block-wise-multi-exp"]
        BfpConfig.num_shared_exponent = json_data["num-shared-exponent"]

        BfpConfig.apply_single_bfp_tensor = json_data["apply-single-bfp-tensor"]
        BfpConfig.prec_activation = json_data["prec-activation"]
        BfpConfig.prec_weight = json_data["prec-weight"]
        BfpConfig.prec_gradient = json_data["prec-gradient"]

        if BfpConfig.use_bfp:
            if BfpConfig.apply_single_bfp_tensor:
                BfpConfig.fwd_prec = -1
                BfpConfig.bwd_a_prec = -1
                BfpConfig.bwd_w_prec = -1
            else:
                BfpConfig.fwd_prec = PrecisionFlag.BFP
                BfpConfig.bwd_a_prec = PrecisionFlag.BFP
                BfpConfig.bwd_w_prec = PrecisionFlag.BFP
        else:
            BfpConfig.fwd_prec = PrecisionFlag.FP
            BfpConfig.bwd_a_prec = PrecisionFlag.FP
            BfpConfig.bwd_w_prec = PrecisionFlag.FP

        BfpConfig.replace_outlier_bfp_to_fp = json_data["replace-outlier-bfp-to-fp"]
        BfpConfig.data_format_to_replace = json_data["data-format-to-replace"]

        BfpConfig.A_repl_block_to_zero = json_data["A_repl_block_to_zero"]
        BfpConfig.A_threshold = json_data["A_threshold"]
        BfpConfig.W_repl_block_to_zero = json_data["W_repl_block_to_zero"]
        BfpConfig.W_threshold = json_data["W_threshold"]
        BfpConfig.G_repl_block_to_zero = json_data["G_repl_block_to_zero"]
        BfpConfig.G_threshold = json_data["G_threshold"]

        ##############################
        # use, but NOT touched
        ##############################
        BfpConfig.should_log = json_data["should-log"]
        BfpConfig.iteration_to_stop = json_data["iteration-to-stop"]


        ##############################
        # use, but NOT touched
        ##############################
        # BfpConfig.f_st = json_data["f-st"]
        # BfpConfig.a_st = json_data["a-st"]
        # BfpConfig.w_st = json_data["w-st"]


        ##############################
        # NOT used
        ##############################
        # BfpConfig.use_flex_bfp = json_data["use-flex-bfp"]
        # BfpConfig.is_fast = json_data["is-fast"]

        # BfpConfig.f_thres = json_data["f-thres"]
        # BfpConfig.a_thres = json_data["a-thres"]
        # BfpConfig.w_thres = json_data["w-thres"]
        # BfpConfig.threshold = json_data["threshold"]

        # BfpConfig.use_shift = json_data["shift-multi-exp"]
        # BfpConfig.static_2nd_exp = json_data["static-2nd-exp"]

        # BfpConfig.use_reconfig = json_data["use-reconfig"]
        # BfpConfig.reconfig_m = json_data["reconfig-m"]
        # BfpConfig.reconfig_g = json_data["reconfig-g"]
        # BfpConfig.reconfig_e = json_data["reconfig-e"]
        
        # BfpConfig.apply_high_prec_to_both_ends = json_data["apply-high-prec-to-both-ends"]
        # BfpConfig.exp_aware_mantissa = json_data["exp-aware-mantissa"]


    def print_current_config():
        print(f"[BFP configuration]")
        if not BfpConfig.use_bfp:
            print(f"use_bfp: false")
            return

        print(f"use bfp: true")
        print(f"mantissa bit: {BfpConfig.bfp_M_Bit}")
        print(f"group size: {BfpConfig.group_size}")
        print(f"stochastic rounding: (F: {BfpConfig.f_st}, dA: {BfpConfig.a_st}, dW {BfpConfig.w_st})")
        print(f"use micro exponent (mx): {BfpConfig.use_mx}")
        print(f"multi exponent: {BfpConfig.use_multi_exp} (block-wise: {BfpConfig.block_wise_multi_exp}, # of exp: {BfpConfig.num_shared_exponent})")
        print(f"static 2nd exponent: {BfpConfig.static_2nd_exp}")
        print(f"bfp operation-wise precisions fwd: {BfpConfig.fwd_prec}, bwd-a: {BfpConfig.bwd_a_prec}, bwd-w: {BfpConfig.bwd_w_prec}")
        print(f"apply BFP to tensor-wise: {BfpConfig.apply_single_bfp_tensor}")
        print(f"bfp tensor-wise preicision    A: {BfpConfig.prec_activation}, W: {BfpConfig.prec_weight}, G: {BfpConfig.prec_gradient}")
        print(f"replace BFP to FP: {BfpConfig.replace_outlier_bfp_to_fp}")
        print(f"replace BFP blocks to zero " \
              f"A: {BfpConfig.A_repl_block_to_zero} (threshold: {BfpConfig.A_threshold}), " \
              f"W: {BfpConfig.W_repl_block_to_zero} (threshold: {BfpConfig.W_threshold}), " \
              f"G: {BfpConfig.G_repl_block_to_zero} (threshold: {BfpConfig.G_threshold})")
        if BfpConfig.iteration_to_stop is not None:
            print(f"test iteration: {BfpConfig.iteration_to_stop} iterations")
