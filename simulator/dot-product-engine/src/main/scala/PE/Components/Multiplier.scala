package PE.Components

import chisel3._
import chisel3.util._

// group_size, mantissa width, shared exponents
// 16/1set BFP
/*
* manWidth x manWidth = (manWidth * 2)
*
*/
class Multiplier(manWidth : Int) extends Module{
  val io = IO(new Bundle{
    val M_from_H = Input(UInt((manWidth).W))
    val M_from_V = Input(UInt((manWidth).W))
    val S_from_H = Input(UInt(1.W))
    val S_from_V = Input(UInt(1.W))
    val mode = Input(UInt(3.W))

    val result_M = Output(UInt(((manWidth)*2).W))
    val result_S = Output(UInt(1.W))
  })
  //ENUM
  val os_dataflow :: os_get_result :: forward_ws_dataflow:: backward_ws_dataflow::fill_weight :: Nil = Enum(5)
  val weight_man = RegInit(0.U((manWidth).W))
  val weight_sign = RegInit(0.U((1).W))
  //initialize
  io.result_M := 0.U
  io.result_S := 0.U
  switch(io.mode){
    is(os_dataflow){
      io.result_M := Cat(0.U((manWidth).W), io.M_from_H) * Cat(0.U((manWidth).W), io.M_from_V)
      io.result_S := io.S_from_H ^ io.S_from_V
    }
    is(os_get_result){
      io.result_M := 0.U
      io.result_S := 0.U
    }
    is(fill_weight) {
      weight_man := io.M_from_V
      weight_sign := io.S_from_V
      io.result_M := 0.U
      io.result_S := 0.U
    }
    is(forward_ws_dataflow){
      io.result_M := Cat(0.U((manWidth).W), weight_man) * Cat(0.U((manWidth).W), io.M_from_V)
      io.result_S := weight_sign ^ io.S_from_V
    }
    is(backward_ws_dataflow){
      io.result_M := Cat(0.U((manWidth).W), weight_man) * Cat(0.U((manWidth).W), io.M_from_H)
      io.result_S := weight_sign ^ io.S_from_H
    }
  }
}
