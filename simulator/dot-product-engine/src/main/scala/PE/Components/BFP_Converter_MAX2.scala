package PE.Components

import chisel3._
import chisel3.util._

import scala.collection.mutable.ArrayBuffer


/*Stage 0*/
class Sep_exp_and_man(groupSize: Int) extends Module{
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
class Comparator(bitWidth: Int, groupSize: Int) extends Module{
  val io = IO(new Bundle{
    val values_vec = Input(Vec(groupSize, Bits(bitWidth.W)))
    val largest_values = Output(Vec(4, Vec(2, UInt(bitWidth.W))))
    val GroupSize_signal = Input(Bits(2.W))
  })
  val Stage1_MAX2 = ArrayBuffer[MAX2_EXP](); for(i<-0 until 8) { Stage1_MAX2 += Module(new MAX2_EXP) }
  val Stage2_MAX2 = ArrayBuffer[MAX2_EXP](); for(i<-0 until 4) { Stage2_MAX2 += Module(new MAX2_EXP) }
  val Stage3_MAX2 = ArrayBuffer[MAX2_EXP](); for(i<-0 until 2) { Stage3_MAX2 += Module(new MAX2_EXP) }
  val Stage4_MAX2 = Module(new MAX2_EXP)

  for(i<- 0 until 32){
    Stage1_MAX2(i/4).io.input_EXPs_4(i%4) := io.values_vec(i)
  }
  for(i<- 0 until 16){
    Stage2_MAX2(i/4).io.input_EXPs_4(i%4) := Stage1_MAX2(i/2).io.output_EXPs_2(i%2)
  }
  for(i<-0 until 8){
    Stage3_MAX2(i/4).io.input_EXPs_4(i%4) := Stage2_MAX2(i/2).io.output_EXPs_2(i%2)
  }
  for (i <- 0 until 4) {
    Stage4_MAX2.io.input_EXPs_4(i) := Stage3_MAX2(i / 2).io.output_EXPs_2(i % 2)
  }
  when(io.GroupSize_signal === 0.U){// GS: 8(00)
    io.largest_values(0)(0) := Stage2_MAX2(0).io.output_EXPs_2(0)
    io.largest_values(0)(1) := Stage2_MAX2(0).io.output_EXPs_2(1)
    io.largest_values(1)(0) := Stage2_MAX2(1).io.output_EXPs_2(0)
    io.largest_values(1)(1) := Stage2_MAX2(1).io.output_EXPs_2(1)
    io.largest_values(2)(0) := Stage2_MAX2(2).io.output_EXPs_2(0)
    io.largest_values(2)(1) := Stage2_MAX2(2).io.output_EXPs_2(1)
    io.largest_values(3)(0) := Stage2_MAX2(3).io.output_EXPs_2(0)
    io.largest_values(3)(1) := Stage2_MAX2(3).io.output_EXPs_2(1)
  }
  when(io.GroupSize_signal === 1.U){ // GS: 16(01)
    for(i<- 0 until 2){
      io.largest_values(0+i)(0) := Stage3_MAX2(0).io.output_EXPs_2(0)
      io.largest_values(0+i)(1) := Stage3_MAX2(0).io.output_EXPs_2(1)
      io.largest_values(2+i)(0) := Stage3_MAX2(1).io.output_EXPs_2(0)
      io.largest_values(2+i)(1) := Stage3_MAX2(1).io.output_EXPs_2(1)
    }

  }.otherwise{ // GS:32(11)
    for(i <- 0 until 4){
      io.largest_values(i)(0) := Stage4_MAX2.io.output_EXPs_2(0)
      io.largest_values(i)(1) := Stage4_MAX2.io.output_EXPs_2(1)
    }
  }
}

/*Stage 2*/
class Shifter(groupSize: Int) extends Module{
  val io = IO(new Bundle{
    val FP_exp_vec = Input(Vec(groupSize, Bits(8.W)))
    val FP_man_vec = Input(Vec(groupSize, Bits((1+23).W)))
    val largest_values = Input(Vec(4, Vec(2, UInt(8.W))))
    val num_shared_exp = Input(UInt(1.W)) // 0: 1개 or 1: 2개

    val shifted_man = Output(Vec(groupSize, Bits((1+23).W)))
    val exp_info = Output(Vec(groupSize, Bits(1.W)))  // max 2
  })
  val Exp_diff = Wire(Vec(groupSize, Bits(8.W))) //[max가 7]
  val Exp_info = Wire(Vec(groupSize, Bits(1.W)))
  for(i<- 0 until groupSize){
    Exp_diff(i) := 0.U
    Exp_info(i) := 0.U
  }
  val operand_of_minus = Wire(Vec(32, UInt(8.W)))
  for(g<- 0 until 4) {
    for(i <- 8*g until 8*(1+g)){ // 0 ~ 32 by g
      when(io.num_shared_exp === 0.U){ // 1개
        operand_of_minus(i) := io.largest_values(g)(io.num_shared_exp)
      }.otherwise{
        operand_of_minus(i) := io.largest_values(g)(io.num_shared_exp)
      }
      when(io.largest_values(g)(0) === io.FP_exp_vec(i)){
        Exp_diff(i) := 0.U; Exp_info(i) := 0.U
      }.otherwise{
        Exp_diff(i) := (operand_of_minus(i) - io.FP_exp_vec(i))
        Exp_info(i) := Mux(io.num_shared_exp === 1.U, 1.U, 0.U)
      }
    }
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
  io.exp_info := Exp_info
}

/*
* Stage 3
* TAKE 1 clock // not now
* now, we didn't support LFSR 8
*/
class Stochastic_rounding(groupSize: Int) extends Module{
  val io = IO(new Bundle{
    val truncated_man = Input(Vec(groupSize, Bits((8).W)))
    val rounded_man = Output(Vec(groupSize, Bits((8).W)))
  })

  val LFSR = Module(new LFSR8) // 1적은게 맞다
  for(i <- 0 until groupSize){
    when(LFSR.io.sign === 1.U){
      io.rounded_man(i) := io.truncated_man(i) //- LFSR.io.output  // TODO UInt라서 output값이 더 크면 문제된다.
    }.otherwise{
      io.rounded_man(i) := io.truncated_man(i) //+ LFSR.io.output  // TODO Test를 위해 LFSR더해주는것 잠시 지움
    }
  }
}
class LFSR8 extends Module{
  val io = IO(new Bundle{
    val output = Output(UInt(8.W))
    val sign = Output(Bits(1.W))
  })
  //1 + X^7 + X^5 + X^4 + X^3
  //https://www.digitalxplore.org/up_proc/pdf/91-1406198475105-107.pdf
  val taps = Wire(UInt(1.W));
  val Reg = RegInit(97.U(8.W))

  taps := 1.U ^ (Reg(7) ^ Reg(5) ^ Reg(4) ^ Reg(3))
  Reg := Cat(taps,Reg(8-1,1))
  io.sign := (Reg(3) ^ Reg(2)) ^ Reg(1)
  io.output := Reg
}




/*Main*/
//input parameter groupSize is HW parameter the maximum numbers that it can handle
class BFP_Converter(groupSize: Int) extends Module{
  val io =IO(new Bundle{
    val fps = Input(Vec(groupSize, Bits(32.W)))
    val shared_exp_signal = Input(Bits(1.W)) /* bits(0) : num_shared_exp(1), bits(1) : num_shared_exp(2) */
    val GroupSize_signal = Input(Bits(2.W))  /* bits(00): GS(8) , bits(01): GS(16), bits(11): GS(32)     */

    val BFP_signs = Output(Vec(groupSize, Bits(1.W)))
    val BFP_mantissas = Output(Vec(groupSize, Bits((8).W)))
    val BFP_exponent = Output(Vec(4 ,Vec(2 ,Bits(8.W))))
    val BFP_exp_info = Output(Vec(groupSize, Bits(1.W)))
  })
  /* shared_exp_signal --> num of shared exponent */
  val num_shared_exp = Wire(UInt(2.W)); num_shared_exp := 0.U
  switch(io.shared_exp_signal){
    is(0.U){
      num_shared_exp := 1.U
    }
    is(1.U){
      num_shared_exp := 2.U
    }
//    is(2.U){
//      num_shared_exp := 4.U
//    } // This is blocked since this is not support NOW (if you add this, must change bits of num_shared_exp)
  }

  /* Stage0. Separate from floating point into exp and man */
  val sep_exp_and_man = Module(new Sep_exp_and_man(groupSize))
  sep_exp_and_man.io.FP_vec := io.fps
  val FP_exp = Wire(Vec(groupSize, Bits(8.W)))
  val FP_man = Wire(Vec(groupSize, Bits((1+23).W)))
  val FP_sign = Wire(Vec(groupSize, Bits(1.W)))
  FP_sign := sep_exp_and_man.io.FP_sign_vec
  FP_exp := sep_exp_and_man.io.FP_exp_vec
  FP_man := sep_exp_and_man.io.FP_man_vec

  /* Stage1. Compare exps and get largest two exps. */
  val Comparator = Module(new Comparator(8, 32))
  val shared_exps = Wire(Vec(4, Vec(2, UInt(8.W)))) /* 4x2 */
  Comparator.io.values_vec := FP_exp
  Comparator.io.GroupSize_signal := io.GroupSize_signal
  shared_exps := Comparator.io.largest_values

  /* Stage2. after get largest exp value, shift mantissas as difference of their exp values */
  val shifting_man = Module(new Shifter(groupSize))
  shifting_man.io.FP_exp_vec := FP_exp
  shifting_man.io.FP_man_vec := FP_man
  shifting_man.io.largest_values := shared_exps
  shifting_man.io.num_shared_exp := io.shared_exp_signal
  val shifted_man = Wire(Vec(groupSize, Bits((1+23).W)))
  val exp_info = Wire(Vec(groupSize, Bits(1.W)))
  shifted_man := shifting_man.io.shifted_man
  exp_info := shifting_man.io.exp_info
    /* Plus, shifting mantissa is truncated by 8bit to make output
    * we support most 8 bit BFP mantissa bits. */
  val truncated_man = Wire(Vec(groupSize, Bits(8.W)))
  for(i <- 0 until groupSize){
      truncated_man(i) := shifted_man(i)(23,(23-7))
  }

  /* Stage3. Stochastic rounding: add value from LFSR to shifted mantissa */
  val stoch_round = Module(new Stochastic_rounding(groupSize))
  stoch_round.io.truncated_man := truncated_man
  val rounded_man = Wire(Vec(groupSize, Bits((8).W)))
  rounded_man := stoch_round.io.rounded_man

  /* Stage4. finish*/
  io.BFP_signs := FP_sign
  io.BFP_mantissas := rounded_man
  io.BFP_exponent := shared_exps
  io.BFP_exp_info := exp_info
}
/*+-+-+-+-+-+-+-+-+-+- FINISH MODULE BELOW IS TEST +-+-+-+-+-+-+-+-+-+-*/