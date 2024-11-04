package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer

class OS_only_eight_bit_multiplier extends Module{
  val io = IO(new Bundle{
    val op_man0 = Input(Vec(16, UInt(man_unit_width.W)))
    val op_man1 = Input(Vec(16, UInt(man_unit_width.W)))
    val op_sign0 = Input(Vec(16, UInt(1.W)))
    val op_sign1 = Input(Vec(16, UInt(1.W)))
    val m_exp_0 = Input(Vec(16, UInt(1.W)))
    val m_exp_1 = Input(Vec(16, UInt(1.W)))
    val bit_width_sig = Input(UInt(2.W)) /* {is_eight_bit_mul, is_four_bit_mul} */
    val operation_mode = Input(UInt(3.W))

    val result_man = Output(UInt(18.W)) // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
    val result_sign = Output(UInt(1.W))
  })
  // Wires and Regs
  val result_four_bit_mul = Wire(Vec(4, SInt(11.W)))  // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
  val psum_st1_0 = Wire(SInt(19.W))                   // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
  val psum_st1_1 = Wire(SInt(19.W))                   // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
  val result_man = Wire(SInt(19.W))                   // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)

  // Modules
  val M_four_bit_mul = ArrayBuffer[OS_only_four_bit_multiplier]()
  for(i <- 0 until 4){
    M_four_bit_mul += Module(new OS_only_four_bit_multiplier)
  }
  // Logics
  for(i <- 0 until 4){
    M_four_bit_mul(i).io.op_man0 := io.op_man0.slice(i*4,(i+1)*4)//First trial to use slice in Vec
    M_four_bit_mul(i).io.op_man1 := io.op_man1.slice(i*4,(i+1)*4)
    M_four_bit_mul(i).io.op_sign0 := io.op_sign0.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.op_sign1 := io.op_sign1.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.m_exp_0 := io.m_exp_0.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.m_exp_1 := io.m_exp_1.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.is_four_bit_mul := io.bit_width_sig(0)
    M_four_bit_mul(i).io.operation_mode := io.operation_mode
    result_four_bit_mul(i) := M_four_bit_mul(i).io.result_man
  }
  /* Only one of two path should be used, another should be 0 */

  val shifter_op00 = Wire(SInt(11.W)); shifter_op00 := Mux(io.bit_width_sig(1).asBool, result_four_bit_mul(0).asSInt, 0.S(11.W)) // adding micro_exp (add 2 width on wire (9->11):refer to output of four_bit multipluer) 
  val shifter_op10 = Wire(SInt(11.W)); shifter_op10 := Mux(io.bit_width_sig(1).asBool, result_four_bit_mul(2).asSInt, 0.S(11.W)) // adding micro_exp (add 2 width on wire (9->11) :refer to output of four_bit multipluer)
  
  psum_st1_0 := Mux(io.bit_width_sig(1).asBool, shifter_op00 << 4, result_four_bit_mul(0).asSInt) + result_four_bit_mul(1).asSInt
  psum_st1_1 := Mux(io.bit_width_sig(1).asBool, shifter_op10 << 4, result_four_bit_mul(2).asSInt) + result_four_bit_mul(3).asSInt
  val shifter_op20 = Wire(SInt(19.W)); shifter_op20 := Mux(io.bit_width_sig(1).asBool, psum_st1_0.asSInt, 0.S(19.W)) // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
  result_man := Mux(io.bit_width_sig(1).asBool, shifter_op20 << 4, psum_st1_0.asSInt) + psum_st1_1.asSInt
  
  io.result_sign := result_man(18).asUInt // adding micro_exp (add 2 width on wire :refer to output of four_bit multipluer)
  io.result_man := Mux(result_man(18)===1.U, (-result_man).asUInt, result_man.asUInt)
}
object OS_only_Eight_mul_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new OS_only_eight_bit_multiplier)
}