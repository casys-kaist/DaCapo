package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
class OS_only_two_bit_multiplier extends Module{
    val io = IO(new Bundle{
        val op_man0 = Input(UInt(2.W))
        val op_man1 = Input(UInt(2.W))
        val op_sign0 = Input(UInt(1.W))
        val op_sign1 = Input(UInt(1.W))
        val operation_mode = Input(UInt(3.W))
        val m_exp_0 = Input(UInt(1.W)) // adding micro_exp (add port)
        val m_exp_1 = Input(UInt(1.W)) // adding micro_exp (add port)
        val result_man = Output(SInt((2*2+1+2).W)) // adding micro_exp (add 2 width wire due to L.26)
    })
    val w_op0_sign = Wire(UInt(1.W))
    val w_op1_sign = Wire(UInt(1.W))
    val w_op0_man = Wire(UInt(2.W))
    val w_op1_man = Wire(UInt(2.W))
    val man_mul_result = Wire(UInt(6.W))// adding micro_exp (add 2 width wire due to L.26)

    w_op0_sign := io.op_sign0 
    w_op0_man := io.op_man0
    w_op1_sign := io.op_sign1
    w_op1_man := io.op_man1
    man_mul_result := (Cat(w_op0_man*w_op1_man , 0.U(2.W)) >> (Cat(0.U(1.W),io.m_exp_0) + Cat(0.U(1.W),io.m_exp_1))) // adding micro_exp (shifter for micro exponent)
    io.result_man := Mux((w_op0_sign ^ w_op1_sign).asBool, -(Cat(0.U(1.W), (man_mul_result)).asSInt), Cat(0.U(1.W), (man_mul_result)).asSInt) //5
}
