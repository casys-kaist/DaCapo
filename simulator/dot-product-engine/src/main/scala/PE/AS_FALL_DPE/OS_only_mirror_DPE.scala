package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer
import PE.Components._

class OS_only_mirror_DPE extends Module{
  val io = IO(new Bundle{
    /*SIGNAL*/
    val reset = Input(UInt(1.W))
    val flow_direction = Input(UInt(1.W)) //1 == up, 0 == down
    val operation_mode = Input(UInt(3.W))
    /* 8_bit_mul input */
    val in_act_bundle = Input(act_bundle)
    val out_act_bundle = Output(act_bundle)
    val in_N_wgt_bundle = Input(OS_wgt_bundle)
    val in_S_wgt_bundle = Input(OS_wgt_bundle)
    val out_N_wgt_bundle = Output(OS_wgt_bundle)
    val out_S_wgt_bundle = Output(OS_wgt_bundle)
    /*partial sum*/
    val in_N_psum = Input(UInt(32.W))
    val in_S_psum = Input(UInt(32.W))
    val out_N_psum = Output(UInt(32.W))
    val out_S_psum = Output(UInt(32.W)) 
    
  })
  /*IO description*/
  /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain 4: IDLE */

  val inner_pe = Module(new OS_only_DPE)
  val act_pipe = Reg(act_bundle)
  val wgt_pipe = Reg(OS_wgt_bundle)
  val wgt_w = Wire(OS_wgt_bundle)
  
  wgt_w := Mux(io.flow_direction.asBool, io.in_N_wgt_bundle, io.in_S_wgt_bundle)
  inner_pe.io.reset := io.reset
  inner_pe.io.op_man0 := io.in_act_bundle.op_man
  inner_pe.io.op_man1 := wgt_w.op_man
  inner_pe.io.bit_width_sig := io.in_act_bundle.bit_width_sig
  inner_pe.io.op_sign0 := io.in_act_bundle.op_sign
  inner_pe.io.op_sign1 := wgt_w.op_sign
  inner_pe.io.op_exp0 := io.in_act_bundle.op_exp
  inner_pe.io.op_exp1 := wgt_w.op_exp 
  inner_pe.io.m_exp_0 := io.in_act_bundle.m_exp // adding micro_exp (add port)
  inner_pe.io.m_exp_1 := wgt_w.m_exp // adding micro_exp (add port)
  inner_pe.io.in_psum := Mux(io.flow_direction.asBool, io.in_S_psum, io.in_N_psum)
  inner_pe.io.operation_mode := io.operation_mode
  io.out_N_psum := Mux(io.operation_mode===0.U || !io.in_act_bundle.is_data, 0.U, Mux(io.flow_direction.asBool, inner_pe.io.out_psum, 0.U))
  io.out_S_psum := Mux(io.operation_mode===0.U || !io.in_act_bundle.is_data, 0.U, Mux(io.flow_direction.asBool, 0.U, inner_pe.io.out_psum))
  /* activation stream pipeline */
  
  act_pipe := io.in_act_bundle
  io.out_act_bundle := act_pipe
  /*wgt_bundle*/
  wgt_pipe.op_man := wgt_w.op_man
  wgt_pipe.op_sign := wgt_w.op_sign
  wgt_pipe.op_exp := wgt_w.op_exp
  io.out_N_wgt_bundle := wgt_pipe
  io.out_S_wgt_bundle := wgt_pipe
}
object OS_only_mirror_DPE_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new OS_only_mirror_DPE)
}