package PE.Components

import chisel3._
import chisel3.util._

import scala.collection.mutable.ArrayBuffer
import chisel3.stage.ChiselStage

/*Stage 0*/
class Sep_exp_and_man_DPE extends Module{
  val groupSize = 16
  val io = IO(new Bundle{
    val FP_vec      = Input(Vec(groupSize, Bits(32.W)))

    val FP_sign_vec = Output(Vec(groupSize, Bits(1.W)))
    val FP_exp_vec  = Output(Vec(groupSize, Bits(8.W)))
    val FP_man_vec  = Output(Vec(groupSize, Bits((1+23).W)))
  })
  for(i <- 0 until groupSize){
    io.FP_sign_vec(i) := io.FP_vec(i)(31)
    io.FP_exp_vec(i) := io.FP_vec(i)(30,23)
    io.FP_man_vec(i) := Cat(io.FP_vec(i)(30,23).orR, io.FP_vec(i)(22,0))
  }
}

/*Stage 1*/
class Comparator_DPE extends Module{
  val groupSize =16;
  val bitWidth = 8;
  val io = IO(new Bundle{
    val values_vec = Input(Vec(groupSize, Bits(bitWidth.W)))
    val largest_values = Output(UInt(8.W))
  })
  val round_8 = Wire(Vec(8, Bits(bitWidth.W)))
  val round_4 = Wire(Vec(4, Bits(bitWidth.W)))
  val round_2 = Wire(Vec(2, Bits(bitWidth.W)))
  for (i <- 0 until 8){
    when(io.values_vec(2*i)>io.values_vec(2*i+1)){
      round_8(i) := io.values_vec(2*i)
    }.otherwise{
      round_8(i) :=  io.values_vec(2*i+1)
    }
  }
  for (i <- 0 until 4){
    when(round_8(2*i)>round_8(2*i+1)){
      round_4(i) := round_8(2*i)
    }.otherwise{
      round_4(i) := round_8(2*i+1)
    }
  }
  for (i <- 0 until 2){
    when(round_4(i)>round_4(2*i+1)){
      round_2(i) := round_4(2*i)
    }.otherwise{
      round_2(i) := round_4(2*i+1)
    }
  }
  // io.largest_values := mux(round_2(0)>round_2(1), round_2(0), round_2(1))
  when(round_2(0)>round_2(1)){
      io.largest_values := round_2(0)
  }.otherwise{
      io.largest_values := round_2(1)
  }
   

  
}

/*Stage 2*/
class Shifter_DPE extends Module{
  val groupSize = 16
  val io = IO(new Bundle{
    val FP_exp_vec = Input(Vec(groupSize, Bits(8.W)))
    val FP_man_vec = Input(Vec(groupSize, Bits((1+23).W)))
    val exp_max = Input(UInt(8.W))

    val shifted_man = Output(Vec(groupSize, Bits((1+23).W)))
  })
  val Exp_diff = Wire(Vec(groupSize, Bits(8.W))) //[max가 7]
  for(i<- 0 until groupSize){
    Exp_diff(i) := io.exp_max - io.FP_exp_vec(i)
  }
  for(i <- 0 until groupSize){
    /* Chisel don't support bit_shifting, which number is above 20.
    * So, we set 0, when shifted_num is over 8 (we support only 8 bit width. */
    when(Exp_diff(i) > 8.U){
      io.shifted_man(i) := 0.U(24.W)
    }.otherwise{
      io.shifted_man(i) := ((io.FP_man_vec(i)) >> (Exp_diff(i)))
    }
  }
}




/*Main*/
// Group_size 16
// No shared exp

class BFP_Converter_DPE extends Module{
  val groupSize = 16
  val io =IO(new Bundle{
    val fps = Input(Vec(groupSize, Bits(32.W)))

    val BFP_signs = Output(Vec(groupSize, Bits(1.W)))
    val BFP_mantissas = Output(Vec(groupSize, Bits((8).W)))
    val BFP_exponent = Output(Bits(8.W))
  })
  /* Stage0. Separate from floating point into exp and man */
  val sep_exp_and_man = Module(new Sep_exp_and_man_DPE)
  sep_exp_and_man.io.FP_vec := io.fps
  val FP_exp = Wire(Vec(groupSize, Bits(8.W)))
  val FP_man = Wire(Vec(groupSize, Bits((1+23).W)))
  val FP_sign = Wire(Vec(groupSize, Bits(1.W)))
  FP_sign := sep_exp_and_man.io.FP_sign_vec
  FP_exp := sep_exp_and_man.io.FP_exp_vec
  FP_man := sep_exp_and_man.io.FP_man_vec

  /* Stage1. Compare exps and get largest two exps. */
  val Comparator = Module(new Comparator_DPE)
  val exp_max = Wire(Bits(8.W))
  Comparator.io.values_vec := FP_exp
  exp_max := Comparator.io.largest_values

  /* Stage2. after get largest exp value, shift mantissas as difference of their exp values */
  val shifting_man = Module(new Shifter_DPE)
  shifting_man.io.FP_exp_vec := FP_exp
  shifting_man.io.FP_man_vec := FP_man
  shifting_man.io.exp_max := exp_max
  val shifted_man = Wire(Vec(groupSize, Bits((1+23).W)))
  shifted_man := shifting_man.io.shifted_man
    /* Plus, shifting mantissa is truncated by 8bit to make output
    * we support most 8 bit BFP mantissa bits. */
  val truncated_man = Wire(Vec(groupSize, Bits(8.W)))
  for(i <- 0 until groupSize){
      truncated_man(i) := shifted_man(i)(23,(23-7))
  }


  /* Stage4. finish*/
  io.BFP_signs := FP_sign
  io.BFP_mantissas := truncated_man
  io.BFP_exponent := exp_max
}
/*+-+-+-+-+-+-+-+-+-+- FINISH MODULE BELOW IS TEST +-+-+-+-+-+-+-+-+-+-*/

object Emit_Convert_DPE extends App{
  (new ChiselStage).emitVerilog(new BFP_Converter_DPE)
}