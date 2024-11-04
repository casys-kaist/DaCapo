package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer

class OS_only_four_bit_multiplier extends Module{
    val io = IO(new Bundle{
        val op_man0= Input(Vec(4, UInt(man_unit_width.W)))
        val op_man1= Input(Vec(4, UInt(man_unit_width.W)))
        val op_sign0 = Input(Vec(4, UInt(1.W)))
        val op_sign1 = Input(Vec(4, UInt(1.W)))
        val is_four_bit_mul = Input(UInt(1.W))
        val operation_mode = Input(UInt(3.W))
        val m_exp_0 = Input(Vec(4, UInt(1.W))) // adding micro_exp (refer to input of two_bit multipluer)
        val m_exp_1 = Input(Vec(4, UInt(1.W))) // adding micro_exp (refer to input of two_bit multipluer)  

        val result_man = Output(SInt((4*2+1+2).W)) //9+2 = 11 // adding micro_exp (refer to output of two_bit multipluer) 
    })
    //wires and regs
    val result_two_bit_mul = Wire(Vec(4, SInt((2*2+1+2).W)))   // adding micro_exp (refer to output of two_bit multipluer) 
    val psum_st1_0 = Wire(SInt((9+2).W)) // adding micro_exp (refer to output of two_bit multipluer) 
    val psum_st1_1 = Wire(SInt((9+2).W)) // adding micro_exp (refer to output of two_bit multipluer) 
    //Modules
    // OS_only_two_bit_multiplier or two_bit_multiplier
    val M_two_bit_mul = ArrayBuffer[OS_only_two_bit_multiplier]()
    for(i <- 0 until 4){
        M_two_bit_mul += Module(new OS_only_two_bit_multiplier)
    }
    //Logic
    for(i <- 0 until 4){
        M_two_bit_mul(i).io.op_man0 := io.op_man0(i)
        M_two_bit_mul(i).io.op_man1 := io.op_man1(i)
        M_two_bit_mul(i).io.op_sign0 := io.op_sign0(i)
        M_two_bit_mul(i).io.op_sign1 := io.op_sign1(i)
        M_two_bit_mul(i).io.m_exp_0 := io.m_exp_0(i)
        M_two_bit_mul(i).io.m_exp_1 := io.m_exp_1(i)
        M_two_bit_mul(i).io.operation_mode := io.operation_mode

        result_two_bit_mul(i) := M_two_bit_mul(i).io.result_man 
    }
    /* Only one of two path should be used, another should be 0 */

    val shifter_op00 = Wire(SInt((2*2+1+2).W)); shifter_op00 := Mux(io.is_four_bit_mul.asBool ,result_two_bit_mul(0).asSInt, 0.S((2*2+1+2).W)) // adding micro_exp (refer to output of two_bit multipluer) 
    val shifter_op10 = Wire(SInt((2*2+1+2).W)); shifter_op10 := Mux(io.is_four_bit_mul.asBool ,result_two_bit_mul(2).asSInt, 0.S((2*2+1+2).W)) // adding micro_exp (refer to output of two_bit multipluer) 
    psum_st1_0 := Mux(io.is_four_bit_mul.asBool, shifter_op00 << 2, result_two_bit_mul(0).asSInt) + result_two_bit_mul(1).asSInt
    psum_st1_1 := Mux(io.is_four_bit_mul.asBool, shifter_op10 << 2, result_two_bit_mul(2).asSInt) + result_two_bit_mul(3).asSInt
    val shifter_op20 = Wire(SInt((9+2).W)); shifter_op20 := Mux(io.is_four_bit_mul.asBool, psum_st1_0.asSInt, 0.S(11.W)) // adding micro_exp (refer to output of two_bit multipluer) 
    io.result_man := Mux(io.is_four_bit_mul.asBool, shifter_op20 << 2, psum_st1_0.asSInt) + psum_st1_1.asSInt
    
}
