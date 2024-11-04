package PE.Components

import chisel3._
import chisel3.util._

class Exp_Add(expWidth: Int, num_shared_exp: Int) extends Module{
  val io = IO(new Bundle{
    val V_Exp = Input(Vec(num_shared_exp, UInt(expWidth.W)))
    val H_Exp = Input(Vec(num_shared_exp, UInt(expWidth.W)))
    val mode = Input(UInt(3.W))
    val sum_Exp = Output(UInt((1+expWidth).W))
  })
  // ENUM
  val os_dataflow :: os_get_result :: forward_ws_dataflow :: backward_ws_dataflow :: fill_weight :: Nil = Enum(5)
  //initializing
  val exp_reg = RegInit(0.U(expWidth.W))
  io.sum_Exp := 0.U

  switch(io.mode) {
    is(fill_weight) {
      exp_reg := io.V_Exp(0)
      io.sum_Exp := 0.U((1 + expWidth).W)
    }
    is(forward_ws_dataflow) {
      io.sum_Exp := Cat(0.U(1.W), exp_reg) + Cat(0.U(1.W), io.V_Exp(0))
    }
    is(backward_ws_dataflow) {
      io.sum_Exp := Cat(0.U(1.W), io.H_Exp(0)) + Cat(0.U(1.W), exp_reg)
    }
    is(os_dataflow) {
      io.sum_Exp := Cat(0.U(1.W), io.H_Exp(0)) + Cat(0.U(1.W), io.V_Exp(0))
    }
    is(os_get_result) {
      io.sum_Exp := 0.U((1 + expWidth).W)
    }
  }
}
