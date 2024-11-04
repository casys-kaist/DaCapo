package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer

class eight_bit_multiplier extends Module{
  val io = IO(new Bundle{
    val op_man0 = Input(Vec(16, UInt(man_unit_width.W)))
    val op_man1 = Input(Vec(16, UInt(man_unit_width.W)))
    val op_sign0 = Input(Vec(16, UInt(1.W)))
    val op_sign1 = Input(Vec(16, UInt(1.W)))
    val bit_width_sig = Input(UInt(2.W)) /* {is_eight_bit_mul, is_four_bit_mul} */
    val operation_mode = Input(UInt(3.W))
    val life_count = Input(SInt(8.W))

    val result_man = Output(UInt(eight_bit_mul_result_man_width.W))
    val result_sign = Output(UInt(1.W))
  })
  // Wires and Regs
  val result_four_bit_mul = Wire(Vec(4, SInt(9.W)))
  val psum_st1_0 = Wire(SInt(17.W))
  val psum_st1_1 = Wire(SInt(17.W))
  val result_man = Wire(SInt(17.W))

  // Modules
  val M_four_bit_mul = ArrayBuffer[four_bit_multiplier]()
  for(i <- 0 until 4){
    M_four_bit_mul += Module(new four_bit_multiplier)
  }
  // Logics
  for(i <- 0 until 4){
    M_four_bit_mul(i).io.op_man0 := io.op_man0.slice(i*4,(i+1)*4)//First trial to use slice in Vec
    M_four_bit_mul(i).io.op_man1 := io.op_man1.slice(i*4,(i+1)*4)
    M_four_bit_mul(i).io.op_sign0 := io.op_sign0.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.op_sign1 := io.op_sign1.slice(i*4, (i+1)*4)
    M_four_bit_mul(i).io.is_four_bit_mul := io.bit_width_sig(0)
    M_four_bit_mul(i).io.operation_mode := io.operation_mode
    M_four_bit_mul(i).io.life_count := io.life_count
    result_four_bit_mul(i) := M_four_bit_mul(i).io.result_man
  }
  /* Only one of two path should be used, another should be 0 */

  val shifter_op00 = Wire(SInt(9.W)); shifter_op00 := Mux(io.bit_width_sig(1).asBool, result_four_bit_mul(0).asSInt, 0.S(9.W))
  val shifter_op10 = Wire(SInt(9.W)); shifter_op10 := Mux(io.bit_width_sig(1).asBool, result_four_bit_mul(2).asSInt, 0.S(9.W))
  
  psum_st1_0 := Mux(io.bit_width_sig(1).asBool, shifter_op00 << 4, result_four_bit_mul(0).asSInt) + result_four_bit_mul(1).asSInt
  psum_st1_1 := Mux(io.bit_width_sig(1).asBool, shifter_op10 << 4, result_four_bit_mul(2).asSInt) + result_four_bit_mul(3).asSInt
  val shifter_op20 = Wire(SInt(17.W)); shifter_op20 := Mux(io.bit_width_sig(1).asBool, psum_st1_0.asSInt, 0.S(17.W))
  result_man := Mux(io.bit_width_sig(1).asBool, shifter_op20 << 4, psum_st1_0.asSInt) + psum_st1_1.asSInt
  
  io.result_sign := result_man(16).asUInt
  io.result_man := Mux(result_man(16)===1.U, (-result_man).asUInt, result_man.asUInt)
}
object Eight_mul_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new eight_bit_multiplier)
}