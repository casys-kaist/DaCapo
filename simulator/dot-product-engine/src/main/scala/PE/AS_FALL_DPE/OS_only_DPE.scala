package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer
import PE.Components._

class OS_only_DPE extends Module{
  val io = IO(new Bundle{
    /* reset */
    val reset = Input(UInt(1.W))
    /* signal */
    val bit_width_sig = Input(UInt(2.W)) /* {is_eight_bit_mul, is_four_bit_mul} */
    val operation_mode = Input(UInt(3.W))
    /* 8_bit_mul input */
    val op_man0 = Input(Vec(16, UInt(2.W)))
    val op_man1 = Input(Vec(16, UInt(2.W)))
    val op_sign0 = Input(Vec(16, UInt(1.W)))
    val op_sign1 = Input(Vec(16, UInt(1.W)))
    val m_exp_0 = Input(Vec(16, UInt(1.W)))
    val m_exp_1 = Input(Vec(16, UInt(1.W)))
    val op_exp0 = Input(UInt(8.W))// adding micro_exp (add m_exp port)
    val op_exp1 = Input(UInt(8.W))// adding micro_exp (add m_exp port)
    /* partial sum */
    val in_psum = Input(UInt(32.W))
    val out_psum = Output(UInt(32.W))
    /*DEBUG*/
    val debug_acc_result = Output(UInt(32.W))
    val debug_fpgen_in_man =Output(UInt(32.W))
    val debug_fpgen_in_exp =Output(UInt(32.W))
    val debug_fpgen_in_sign =Output(UInt(32.W))
    val debug_fpgen_result = Output(UInt(32.W))
  })

  val psum_reg = RegInit(0.U(32.W)) // == accumulator
          /*these parameter values are not related to this module*/
  val fp_generator = Module(new DPE_fp_gen(expWidth=8, manWidth = 4, groupSize = 16)) 
  val M_man_dp = Module(new OS_only_eight_bit_multiplier)
  val fp_adder = Module(new FP_Add(expWidth=8, manWidth=23))

  /* Module "eight_bit_mul" */
  M_man_dp.io.op_man0 := io.op_man0
  M_man_dp.io.op_man1 := io.op_man1
  M_man_dp.io.op_sign0 := io.op_sign0
  M_man_dp.io.op_sign1 := io.op_sign1
  M_man_dp.io.m_exp_0 := io.m_exp_0 // adding micro_exp (add m_exp port)
  M_man_dp.io.m_exp_1 := io.m_exp_1 // adding micro_exp (add m_exp port)
  M_man_dp.io.bit_width_sig := io.bit_width_sig
  M_man_dp.io.operation_mode := io.operation_mode


  /* fp_generator <= accumulated man + exponent + sign*/
  val fp_gen_op_2bit = Wire(UInt(20.W)); fp_gen_op_2bit := Mux(io.bit_width_sig===0.U, M_man_dp.io.result_man, 0.U)
  val fp_gen_op_4bit = Wire(UInt(20.W)); fp_gen_op_4bit := Mux(io.bit_width_sig===1.U, M_man_dp.io.result_man, 0.U)
  val fp_gen_op_8bit = Wire(UInt(20.W)); fp_gen_op_8bit := Mux(io.bit_width_sig===3.U, M_man_dp.io.result_man, 0.U)
  fp_generator.io.in_man := Mux(io.bit_width_sig===0.U, fp_gen_op_2bit << 10, Mux(io.bit_width_sig===1.U, fp_gen_op_4bit << 6, fp_gen_op_8bit>>2)) // adding micro_exp (>>2 for all element) (12, 8, 0)->(10,6,-2)
  fp_generator.io.in_exp_not_minus_bias := Cat(0.U(1.W), io.op_exp0) + Cat(0.U(1.W), io.op_exp1) /*w_flow*/
  fp_generator.io.in_sign := M_man_dp.io.result_sign
  /* fp_adder */
  fp_adder.io.in1 := fp_generator.io.result
  fp_adder.io.in2 := Mux(io.operation_mode===1.U, io.in_psum, psum_reg)
  psum_reg := Mux(io.reset===1.U, 0.U, Mux(io.operation_mode===3.U ,io.in_psum ,fp_adder.io.fpOut))
  io.out_psum := psum_reg
    

  /*DEBUG*/
  io.debug_acc_result := M_man_dp.io.result_man
  io.debug_fpgen_result := fp_generator.io.result
  io.debug_fpgen_in_man := fp_generator.io.in_man
  io.debug_fpgen_in_exp := fp_generator.io.in_exp_not_minus_bias
  io.debug_fpgen_in_sign := fp_generator.io.in_sign
}
object OS_only_DPE_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new OS_only_DPE)
}