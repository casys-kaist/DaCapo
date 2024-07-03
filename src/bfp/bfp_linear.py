import torch
from bfp.bfp_gemm import BfpGemm
from bfp.bfp_config import BfpConfig


class BfpLinear(torch.autograd.Function):
    @staticmethod
    def forward(ctx, ori_inputs, weights, bias, bfp_gemms, id,global_id,m_bit):
        inputs = ori_inputs

        ctx.save_for_backward(inputs, weights, bias)
        ctx.bfp_gemms = bfp_gemms
        ctx.id = id
        ctx.global_id = global_id
        ctx.m_bit = m_bit

        if not BfpConfig.use_bfp:
            outputs = torch.matmul(inputs, weights.t())
        else:
            if bfp_gemms["fwd"] is None:
                bfp_gemms["fwd"] = BfpGemm(
                    lhs_shape=inputs.shape,
                    is_lhs_bfp=BfpConfig.prec_activation,

                    rhs_shape=weights.shape,
                    is_rhs_bfp=BfpConfig.prec_weight,

                    use_stochastic_rounding=BfpConfig.f_st,
                    layer_index=global_id,
                    lhs_name="A",
                    rhs_name="W",
                    
                    m_bit=m_bit)

            bfp_gemm = bfp_gemms["fwd"]
            outputs = bfp_gemm.run(inputs, weights)

        if bias is not None:
            outputs += bias.unsqueeze(0).expand_as(outputs)

        return outputs[0:ori_inputs.shape[0],:]

    @staticmethod
    def backward(ctx, ori_grad_output):
        input, weight, bias = ctx.saved_tensors
        bfp_gemms = ctx.bfp_gemms
        id = ctx.id
        global_id = ctx.global_id
        grad_input = grad_weight = grad_bias = None
        m_bit = ctx.m_bit

        grad_output = ori_grad_output

        if ctx.needs_input_grad[0]:
            if not BfpConfig.use_bfp:
                grad_input = torch.matmul(grad_output, weight)
            else:
                weight_t = weight.permute(1, 0).contiguous()

                if bfp_gemms["grad-a"] is None:
                    bfp_gemms["grad-a"] = BfpGemm(
                        lhs_shape=grad_output.shape,
                        is_lhs_bfp=BfpConfig.prec_gradient,

                        rhs_shape=weight_t.shape,
                        is_rhs_bfp=BfpConfig.prec_weight,

                        use_stochastic_rounding=BfpConfig.a_st,
                        layer_index=global_id,
                        lhs_name="dO",
                        rhs_name="W_t",
                        
                        m_bit=m_bit)

                bfp_gemm = bfp_gemms["grad-a"]
                grad_input = bfp_gemm.run(grad_output, weight_t)

        if ctx.needs_input_grad[1]:
            if len(grad_output.shape) > 2:
                grad_output_size = (
                    int(torch.prod(torch.tensor(grad_output.shape[:-1])).item()),
                    int(grad_output.shape[-1])
                )
            else:
                grad_output_size = (
                    int(grad_output.shape[0]),
                    int(grad_output.shape[1])
                )
            
            if len(input.shape) > 2:
                input_size = (
                    int(torch.prod(torch.tensor(input.shape[:-1])).item()),
                    int(input.shape[-1])
                )
            else:
                input_size = (
                    int(input.shape[0]),
                    int(input.shape[1])
                )

            if not BfpConfig.use_bfp:
                grad_weight = grad_output.reshape(grad_output_size).t().mm(input.reshape(input_size))
            else:
                if grad_output.dim() == 2:
                    grad_output_t = grad_output.permute(1, 0).contiguous()
                else:
                    last_dim = grad_output.shape[2]
                    grad_output_t = grad_output.permute(2, 0, 1).view(last_dim, -1).contiguous()

                if input.dim() == 2:
                    input_t = input.permute(1, 0).contiguous()
                else:
                    last_dim = input.shape[2]
                    input_t = input.permute(2, 0, 1).view(last_dim, -1).contiguous()

                if bfp_gemms["grad-w"] is None:
                    bfp_gemms["grad-w"] = BfpGemm(
                        lhs_shape=grad_output_t.shape,
                        is_lhs_bfp=BfpConfig.prec_gradient,

                        rhs_shape=input_t.shape,
                        is_rhs_bfp=BfpConfig.prec_activation,

                        use_stochastic_rounding=BfpConfig.w_st,
                        layer_index=global_id,
                        lhs_name="dO_t",
                        rhs_name="A_t",
                        
                        m_bit=m_bit)

                bfp_gemm = bfp_gemms["grad-w"]
                grad_weight = bfp_gemm.run(grad_output_t, input_t)

        if bias is not None and ctx.needs_input_grad[2]:
            grad_bias = grad_output.sum(0)

        if grad_input is not None:
            grad_input = grad_input[0:ori_grad_output.shape[0],:]

        return grad_input, grad_weight, grad_bias, None, None, None, None
