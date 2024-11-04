package PE.Components

import chisel3._
import chisel3.util._
class MAX4 extends Module{
  private val in_exp_num = 8
  private val exp_bits = 8

  val io = IO(new Bundle{
    val in_exps = Input(Vec(in_exp_num, Bits(exp_bits.W)))
    val max4_exp = Output(Vec(4, Bits(exp_bits.W)))
  })

  val wire1 = Wire(Vec(in_exp_num, Bits(exp_bits.W)))
  wire1(0) := Mux((io.in_exps(0) > io.in_exps(1)), io.in_exps(0), io.in_exps(1))
  wire1(1) := Mux((io.in_exps(0) > io.in_exps(1)), io.in_exps(1), Mux(io.in_exps(0) === io.in_exps(1), 0.U(exp_bits.W), io.in_exps(0)))
  wire1(2) := Mux((io.in_exps(2) > io.in_exps(3)), io.in_exps(2), io.in_exps(3))
  wire1(3) := Mux((io.in_exps(2) > io.in_exps(3)), io.in_exps(3), Mux(io.in_exps(2) === io.in_exps(3), 0.U(exp_bits.W), io.in_exps(2)))
  wire1(4) := Mux((io.in_exps(4) > io.in_exps(5)), io.in_exps(4), io.in_exps(5))
  wire1(5) := Mux((io.in_exps(4) > io.in_exps(5)), io.in_exps(5), Mux(io.in_exps(4) === io.in_exps(5), 0.U(exp_bits.W), io.in_exps(4)))
  wire1(6) := Mux((io.in_exps(6) > io.in_exps(7)), io.in_exps(6), io.in_exps(7))
  wire1(7) := Mux((io.in_exps(6) > io.in_exps(7)), io.in_exps(7), Mux(io.in_exps(6) === io.in_exps(7), 0.U(exp_bits.W), io.in_exps(6)))

  val wire2 = Wire(Vec(in_exp_num, Bits(exp_bits.W)))
  wire2(0) := Mux((wire1(0) > wire1(2)), wire1(0), wire1(2))
  wire2(1) := Mux((wire1(0) > wire1(2)), wire1(2), Mux((wire1(0) === wire1(2)), 0.U(exp_bits.W), wire1(0)) )
  wire2(2) := Mux((wire1(1) > wire1(3)), wire1(1), wire1(3))
  wire2(3) := Mux((wire1(1) > wire1(3)), wire1(3), Mux((wire1(1) === wire1(3)), 0.U(exp_bits.W), wire1(1)) )
  wire2(4) := Mux((wire1(4) > wire1(6)), wire1(4), wire1(6))
  wire2(5) := Mux((wire1(4) > wire1(6)), wire1(6), Mux((wire1(4) === wire1(6)), 0.U(exp_bits.W), wire1(4)))
  wire2(6) := Mux((wire1(5) > wire1(7)), wire1(5), wire1(7))
  wire2(7) := Mux((wire1(5) > wire1(7)), wire1(7), Mux((wire1(5) === wire1(7)), 0.U(exp_bits.W), wire1(5)))

  val wire3 = Wire(Vec(5, Bits(exp_bits.W)))
  io.max4_exp(0) := Mux(wire2(0)>wire2(4), wire2(0), wire2(4))
  wire3(0) := Mux(wire2(0) > wire2(4), wire2(4), Mux(wire2(0) === wire2(4), 0.U(exp_bits.W), wire2(0)))
  wire3(1) := Mux(wire2(1) > wire2(3), wire2(1), wire2(3))
  wire3(2) := Mux(wire2(1) > wire2(3), wire2(3), Mux(wire2(1) === wire2(3), 0.U(exp_bits.W), wire2(1)))
  wire3(3) := Mux(wire2(5) > wire2(7), wire2(5), wire2(7))
  wire3(4) := Mux(wire2(5) > wire2(7), wire2(7), Mux(wire2(5) === wire2(7), 0.U(exp_bits.W), wire2(5)))

  val wire4 = Wire(Vec(5, Bits(exp_bits.W)))
  wire4(0) := Mux(wire3(0) > wire2(2), wire3(0), wire2(2))
  wire4(1) := Mux(wire3(0) > wire2(2), wire2(2), Mux(wire3(0) === wire2(2), 0.U(exp_bits.W), wire3(0)))
  wire4(2) := Mux(wire3(1) > wire2(6), wire3(1), wire2(6))
  wire4(3) := Mux(wire3(1) > wire2(6), wire2(6), Mux(wire3(1) === wire2(6), 0.U(exp_bits.W), wire3(1)))
  wire4(4) := Mux(wire3(2) > wire3(4), wire3(2), wire3(4))

  val wire5 = Wire(Vec(6, Bits(exp_bits.W)))
  wire5(0) := Mux(wire4(0) > wire4(2), wire4(0), wire4(2))
  wire5(1) := Mux(wire4(0) > wire4(2), wire4(2), Mux(wire4(0) === wire4(2), 0.U(exp_bits.W), wire4(0)))
  wire5(2) := Mux(wire4(1) > wire4(3), wire4(1), wire4(3))
  wire5(3) := Mux(wire4(1) > wire4(3), wire4(3), Mux(wire4(1) === wire4(3), 0.U(exp_bits.W), wire4(1)))
  wire5(4) := Mux(wire3(3) > wire4(4), wire3(3), wire4(4))
  wire5(5) := Mux(wire3(3) > wire4(4), wire4(4), Mux(wire3(3) === wire4(4), 0.U(exp_bits.W), wire3(3)))

  val wire6 = Wire(Vec(2, Bits(exp_bits.W)))
  io.max4_exp(1) := Mux(wire5(0) > wire5(4), wire5(0), wire5(4))
  wire6(0) := Mux(wire5(0) > wire5(4), wire5(4), Mux(wire5(0) === wire5(4), 0.U(exp_bits.W), wire5(0)))
  wire6(1) := Mux(wire5(3) > wire5(5), wire5(3), wire5(5))

  val wire7 = Wire(Vec(4, Bits(exp_bits.W)))
  wire7(0) := Mux(wire6(0) > wire5(1), wire6(0), wire5(1))
  wire7(1) := Mux(wire6(0) > wire5(1), wire5(1), Mux(wire6(0) === wire5(1), 0.U(exp_bits.W), wire6(0)))
  wire7(2) := Mux(wire5(2) > wire6(1), wire5(2), wire6(1))
  wire7(3) := Mux(wire5(2) > wire6(1), wire6(1), Mux(wire5(2) === wire6(1), 0.U(exp_bits.W), wire5(2)))

  val wire8 = Wire(Vec(2, Bits(exp_bits.W)))
  io.max4_exp(2) := Mux(wire7(0) > wire7(2), wire7(0), wire7(2))
  wire8(0) := Mux(wire7(0) > wire7(2), wire7(2), Mux(wire7(0) === wire7(2), 0.U(exp_bits.W), wire7(0)))
  wire8(1) := Mux(wire7(1) > wire7(3), wire7(1), wire7(3))

  io.max4_exp(3) := Mux(wire8(0) > wire8(1), wire8(0), wire8(1))
}
class BFP_Converter_MAX4(groupSize : Int) extends Module{
  private val fp_bits = 32
  private val num_partition = 4
  private val max_sh_exps = 4
  private val max_man_bit = 8
  private val exp_bits = 8
  private val exp_info_bit = 2
  val io = IO(new Bundle{
    val fps = Input(Vec(groupSize, Bits(fp_bits.W)))
    val shared_exp_signal = Input(Bits(2.W)) /* 0 : 1개, 1: 2개, 3: 4개 */
    val GroupSize_signal = Input(Bits(2.W))  /* 0 : GS8, 1: GS16, 3: GS32 */

    val BFP_signs = Output(Vec(groupSize, Bits(1.W)))
    val BFP_mantissas = Output(Vec(groupSize, Bits(max_man_bit.W)))
    val BFP_exponent = Output(Vec(num_partition, Vec(max_sh_exps, Bits(exp_bits.W))))
    val BFP_exp_info = Output(Vec(groupSize, Bits(exp_info_bit.W)))
  })
  /* shared_exp_signal --> num of shared exponent */
  val num_shared_exp = Wire(UInt(2.W)); num_shared_exp := 0.U
  switch(io.shared_exp_signal) {
    is(0.U) {
      num_shared_exp := 1.U
    }
    is(1.U) {
      num_shared_exp := 2.U
    }
    is(2.U){
      num_shared_exp := 4.U
    }
  }

  /* Stage0. Separate from floating point into exp and man */
  val sep_exp_and_man = Module(new Sep_exp_and_man(groupSize))
  sep_exp_and_man.io.FP_vec := io.fps
  val FP_exp = Wire(Vec(groupSize, Bits(exp_bits.W)))
  val FP_man = Wire(Vec(groupSize, Bits((1+23).W)))
  val FP_sign = Wire(Vec(groupSize, Bits(1.W)))
  FP_sign := sep_exp_and_man.io.FP_sign_vec
  FP_exp := sep_exp_and_man.io.FP_exp_vec
  FP_man := sep_exp_and_man.io.FP_man_vec

  /* Stage1. Compare exps and get largest four exps */
  val Comparator = Module(new Comparator_Max4(exp_bits, groupSize))
  val shared_exps = Wire(Vec(num_partition, Vec(max_sh_exps, Bits(exp_bits.W))))
  Comparator.io.values_vec := FP_exp
  Comparator.io.GroupSize_signal := io.GroupSize_signal
  shared_exps := Comparator.io.largest_values

  /* Stage2. After get largest exp values, shift mantissas as difference of their exp values */
  val shifting_man = Module(new Shifter_sh_exp_4(groupSize))
  shifting_man.io.FP_exp_vec := FP_exp
  shifting_man.io.FP_man_vec := FP_man
  shifting_man.io.largest_values := shared_exps
  shifting_man.io.num_shared_exp := io.shared_exp_signal
  val shifted_man = Wire(Vec(groupSize, Bits((1+23).W)))
  val exp_info = Wire(Vec(groupSize, Bits(exp_info_bit.W)))
  shifted_man := shifting_man.io.shifted_man
  exp_info := shifting_man.io.exp_info

  val truncated_man = Wire(Vec(groupSize, Bits(8.W)))
  for(i<- 0 until groupSize){
    truncated_man(i) := shifted_man(i)(23,(23-7))
  }

  /* Stage3. Stocastic rounding: add value from LFSR to shifted mantissa */
  val stoch_round = Module(new Stochastic_rounding(groupSize))
  stoch_round.io.truncated_man := truncated_man
  val rounded_man = Wire(Vec(groupSize, Bits(8.W)))
  rounded_man := stoch_round.io.rounded_man

  /* Stage4. finish*/
  io.BFP_signs := FP_sign
  io.BFP_mantissas := rounded_man
  io.BFP_exponent := shared_exps
  io.BFP_exp_info := exp_info
}




