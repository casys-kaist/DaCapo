package PE.FPMAC

import PE.Components.{FP_Mul, fp_Accumulator}
import chisel3._

/* FPAC Module
* Input two '32 floating point'
* Output '32 floating point'
*  */
class FPMAC extends Module{
  val io = IO(new Bundle {
    val fp_H = Input(UInt((32).W))
    val fp_V = Input(UInt((32).W))
    val result = Output(UInt(32.W))
  })
  val multiplier = Module(new FP_Mul(8,23))
  val accumulator = Module(new fp_Accumulator)
  multiplier.io.in1 := io.fp_H
  multiplier.io.in2 := io.fp_V
  accumulator.io.input := multiplier.io.fpOut
  /*every time input_init set to 1 reset the accumulator value to 0
  * input_init value is used when input_init set to 1,
  * please reference Module fp_Accumulator*/
  accumulator.io.init := 0.U
  accumulator.io.input_init := 0.U

  io.result := accumulator.io.fpOut
}
object FPMAC_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new FPMAC)
}
