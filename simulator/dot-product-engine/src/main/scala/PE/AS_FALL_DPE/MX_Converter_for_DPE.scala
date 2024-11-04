package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._

import scala.math._
class MX_Converter_for_DPE extends Module{
  val io = IO(new Bundle{
    val fps = Input(Vec(16, UInt(32.W)))

    val signs = Output(Vec(16, UInt(1.W)))
    val max_exp = Output(UInt(8.W))
    val scale_factor = Output(Vec(8, UInt(1.W)))
    val shifted_mans =Output(Vec(16, UInt(7.W)))
  })
  /*STEP0: slice the fp into sign,exp,man*/
  val in_exps = Wire(Vec(16,UInt(8.W)))
  val in_mans = Wire(Vec(16,UInt(7.W)))
  for(i <- 0 until 16){
    io.signs(i) := io.fps(i)(31)
    in_exps(i) := io.fps(i)(30,23)
    in_mans(i) := Cat(1.U, io.fps(i)(22,17))//1+6bit
  }
  /*STEP1: get max exp*/
  io.max_exp := in_exps.reduceTree((a,b)=>Mux(a > b, a, b))


  /*get scale_factor*/
  val max_exp_subgW = Wire(Vec(8, UInt(8.W)))
  val scale_factorW = Wire(Vec(8, UInt(1.W)))
  for (i <- 0 until 8){
    max_exp_subgW(i) := Mux(in_exps(i*2)>in_exps(i*2+1),in_exps(i*2),in_exps(i*2+1))
    scale_factorW(i) := Mux(io.max_exp > max_exp_subgW(i), 1.U(1.W), 0.U(1.W))
    io.scale_factor(i) := scale_factorW(i)
  }

  /*shifting mantissa*/
  val exp_diff = Wire(Vec(16, UInt(8.W)))
  val shifted_manW = Wire(Vec(16, UInt(7.W)))
  for(i <- 0 until 16){
    exp_diff(i) := io.max_exp - (in_exps(i) + Cat(0.U(7.W),scale_factorW(i/2)))
    shifted_manW(i) := in_mans(i)>>exp_diff(i)
    io.shifted_mans(i) := shifted_manW(i)
  }
}
object MX_Converter_for_DPE_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MX_Converter_for_DPE)
}