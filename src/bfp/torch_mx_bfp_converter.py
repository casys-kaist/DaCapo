import torch
import torch.nn.functional as F


def convert_fp_to_mx(**kwargs):
    group_size, sub_group_size = kwargs["group_size"][0], kwargs["group_size"][1]
    exponent, micro_exponent = kwargs["mx_e_bit"][0], kwargs["mx_e_bit"][1]
    mx_m_bit = kwargs["mx_m_bit"]
    tensor = kwargs["tensor"]
    use_SR = kwargs["use_SR"]
    
    assert group_size % sub_group_size == 0
    
    M, E = torch.frexp(tensor)
    
    S = torch.sign(tensor)
    M *= 2.0
    E -= 1
    
    last_E_dim = E.shape[-1]
    has_tail = False if last_E_dim % group_size == 0 else True
    
    if has_tail:
        E_padded = F.pad(E, (0, group_size - (last_E_dim % group_size)), value = torch.iinfo(torch.int16).min)
    else:
        E_padded = E
    
    # calculate exponent
    
    E_sub_group = torch.reshape(E_padded, (-1, group_size // sub_group_size, sub_group_size))   # ? x (g / sg) x sg
    E_sub_group_max, _ = torch.max(E_sub_group, dim = 2, keepdim = True)                        # ? x (g / sg) x 1
    E_expanded_sub_group_max = E_sub_group_max.repeat(1, 1, sub_group_size)                     # ? x (g / sg) x sg
    
    E_group = E_expanded_sub_group_max.reshape(-1, group_size)                                  # ? x g
    E_group_max, _ = torch.max(E_group, dim = 1, keepdim = True)                                # ? x 1
    E_expanded_group_max = E_group_max.repeat(1, group_size)                                    # ? x g
    
    E_diff = E_expanded_group_max - E_group                                                     # ? x g
    E_diff = torch.clamp(E_diff, min = 0, max = (1 << micro_exponent) - 1)                      # ? x g
    
    E_shared_exponent = E_expanded_group_max.reshape(E_padded.shape)[:,:last_E_dim]             # original
    E_micro_exponent = E_diff.reshape(E_padded.shape)[:,:last_E_dim]                            # original
    
    E_actual_diff = E_shared_exponent - E_micro_exponent - E                                    # original
    
    # calculate mantissa
    fp_frac_bit = 23
    offset = (fp_frac_bit + 1) - mx_m_bit
    
    M_scale = 1 << fp_frac_bit
    M_int = (torch.abs(M) * M_scale).int()
    M_int >>= E_actual_diff
    
    if use_SR:
        SR_range = 1 << offset

        M_for_SR_diff = M_int & (SR_range - 1)
        prob_to_left = (SR_range - M_for_SR_diff) / SR_range
        prob_rand = torch.rand(size = prob_to_left.shape, device = prob_to_left.device)
        SR_noise = prob_rand >= prob_to_left
        
    M_int >>= offset

    if use_SR:
        M_int += SR_noise

    return (S, (E_shared_exponent, E_micro_exponent), M_int, 0)

def convert_mx_to_fp(**kwargs):
    S = kwargs["S"]
    E_shared_exponent, E_micro_exponent = kwargs["E"][0], kwargs["E"][1]
    M = kwargs["M"]
    mx_m_bit = kwargs["mx_m_bit"]
    
    num_to_divide = 1 << (mx_m_bit - 1)
    float_M = M / num_to_divide
    actual_E = E_shared_exponent - E_micro_exponent
    fp_from_mx = S * torch.pow(2., actual_E) * float_M    
    
    return fp_from_mx
    
if __name__ == "__main__":
    pass