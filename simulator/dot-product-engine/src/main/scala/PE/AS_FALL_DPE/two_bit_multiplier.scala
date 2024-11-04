package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
class two_bit_multiplier extends Module{
    val io = IO(new Bundle{
        val op_man0 = Input(UInt(2.W))
        val op_man1 = Input(UInt(2.W))
        val op_sign0 = Input(UInt(1.W))
        val op_sign1 = Input(UInt(1.W))
        val operation_mode = Input(UInt(3.W))
        val life_count = Input(SInt(8.W))

        val result_man = Output(SInt((2*2+1).W))
    })
    val wgt_man = RegInit(0.U(2.W))
    val wgt_sign = RegInit(0.U(1.W))

    val w_op0_sign = Wire(UInt(1.W))
    val w_op1_sign = Wire(UInt(1.W))
    val w_op0_man = Wire(UInt(2.W))
    val w_op1_man = Wire(UInt(2.W))
    val man_mul_result = Wire(UInt(4.W))
    when(io.operation_mode === 0.U && io.life_count === 0.S){
        wgt_man := io.op_man1
        wgt_sign := io.op_sign1
    }
    w_op0_sign := io.op_sign0 
    w_op0_man := io.op_man0
    w_op1_sign := Mux(io.operation_mode===1.U, wgt_sign, io.op_sign1)
    w_op1_man := Mux(io.operation_mode===1.U, wgt_man, io.op_man1)
    man_mul_result := w_op0_man*w_op1_man
    io.result_man := Mux((w_op0_sign ^ w_op1_sign).asBool, -(Cat(0.U(1.W), (man_mul_result)).asSInt), Cat(0.U(1.W), (man_mul_result)).asSInt) //5 //(io.op_man0 * Mux(io.operation_mode===1.U ,wgt_man ,io.op_man1))
}
