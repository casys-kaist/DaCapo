package PE.Components

import PE.FPMAC.{FPBit_bundle, from_FPBit_to_fp, from_fp_to_FPBit}
import chisel3._
import chisel3.util.{Cat, Log2}
class Cmp_two_unsigned_int (bit_length: Int) extends Module{
  /*
  * in1 > in2 ?
  */
  val io = IO(new Bundle {
    val in1 = Input(UInt(bit_length.W))
    val in2 = Input(UInt(bit_length.W))
    val is_in1_denormal = Input(Bool())
    val is_in2_denormal = Input(Bool())

    val in1_large = Output(Bool())
    val in2_large = Output(Bool())
    val is_same = Output(Bool())
    val difference = Output(UInt(bit_length.W))

    val invalidExc = Output(Bool())
    val debug_error = Output(Bool())
    //(infinite) > (infinite)
  })
  /*initialize*/
  io.invalidExc := false.B
  io.in1_large := false.B
  io.in2_large := false.B
  io.is_same := false.B
  io.debug_error := false.B
  io.difference := 0.U
  val adjust_in1 = Wire(UInt(bit_length.W)); adjust_in1 := io.in1 + io.is_in1_denormal.asUInt
  val adjust_in2 = Wire(UInt(bit_length.W)); adjust_in2 := io.in2 + io.is_in2_denormal.asUInt
  //비교 할 때는 inf>inf, Nan이 있다면 invalid Exc
  when(io.in1.andR === 1.U && io.in2.andR === 1.U){
    // is special value: cannot count difference
    io.invalidExc := true.B
  }.otherwise{
    // is normal value
    when(adjust_in1 > adjust_in2) {
      io.in1_large := true.B
      io.difference := adjust_in1 - adjust_in2
    }.elsewhen(adjust_in2 > adjust_in1) {
      io.in2_large := true.B
      io.difference := adjust_in2 - adjust_in1
    }.elsewhen(adjust_in1 === adjust_in2) {
      io.is_same := true.B
      io.difference := 0.U
    }.otherwise {
      io.debug_error := true.B
    }
  }
}
class Add_unsigned_int_with_sign(bit_length: Int) extends Module{
  val io = IO(new Bundle{
    val in1 = Input(UInt(bit_length.W))
    val in2 = Input(UInt(bit_length.W))
    val in1_sign = Input(Bool())
    val in2_sign = Input(Bool())

    val result_abs = Output(UInt(bit_length.W))
    val result_sign = Output(Bool())
  })
  val expand_in1 = Wire(SInt((bit_length+1).W)); expand_in1 := Cat(0.U(1.W),io.in1).asSInt
  val expand_in2 = Wire(SInt((bit_length+1).W)); expand_in2 := Cat(0.U(1.W),io.in2).asSInt
  val signed_result = Wire(SInt((bit_length+1).W)); signed_result := 0.S
  when(io.in1_sign && io.in2_sign){         // (- in1) +(- in2)
    signed_result := (- expand_in1) +(- expand_in2)
  }.elsewhen(!io.in1_sign && io.in2_sign){  // (+ in1) +(- in2)
    signed_result := (  expand_in1) +(- expand_in2)
  }.elsewhen(io.in1_sign && !io.in2_sign){  // (- in1) +(+ in2)
    signed_result := (- expand_in1) +(  expand_in2)
  }.elsewhen(!io.in1_sign && !io.in2_sign){ // (+ in1) +(+ in2)
    signed_result := (  expand_in1) +(  expand_in2)
  }.otherwise{
    //error
  }
  when(signed_result < 0.S){ //signed_result(bit_length).asUInt === 0.U(1.W)
    val tmp_wire = Wire(SInt((bit_length+1).W)); tmp_wire := (- signed_result)
    io.result_abs := (tmp_wire)(bit_length-1,0).asUInt
    io.result_sign := true.B // negative
  }.otherwise{
    io.result_abs := (signed_result)(bit_length-1,0).asUInt
    io.result_sign := false.B // positive
  }

}
class Rounding_hardware(man_length: Int) extends Module{
  val io = IO(new Bundle{
    val man_with_small_exp = Input(UInt((2*man_length+2).W))
    val mode = Input(UInt(2.W))
    val more_move_bits = Input((UInt(4.W)))
    val is_odd = Input(Bool())

    val is_rounding = Output(Bool())
  })
  io.is_rounding := false.B
  when(io.mode === 0.U(2.W)){
    io.is_rounding := false.B
  }.elsewhen(io.mode === 1.U(2.W)){
    io.is_rounding := (io.man_with_small_exp(man_length) && io.man_with_small_exp(man_length-1, 1).orR) || (io.is_odd && io.man_with_small_exp(man_length))
  }.elsewhen(io.mode === 2.U(2.W)){
    io.is_rounding := ((io.man_with_small_exp<< io.more_move_bits)(man_length-1) && (io.man_with_small_exp << io.more_move_bits)(man_length-2, 0).orR)||(io.is_odd &&(io.man_with_small_exp<< io.more_move_bits)(man_length-1))
  }.elsewhen(io.mode === 3.U(2.W)){
    io.is_rounding := ((io.man_with_small_exp<< io.more_move_bits)(man_length-1) && (io.man_with_small_exp << io.more_move_bits)(man_length-2, 0).orR)||(io.is_odd &&(io.man_with_small_exp<< io.more_move_bits)(man_length-1))
  }
}
class FPBit_Add (expWidth: Int, manWidth: Int) extends Module{
  val io = IO(new Bundle {
    val in1 = Input(new FPBit_bundle(expWidth, manWidth))
    val in2 = Input(new FPBit_bundle(expWidth, manWidth))
    val invalidExc = Output(Bool())
    val fpOut = Output(new FPBit_bundle(expWidth, manWidth))
    val test  = Output(UInt((2+manWidth*2).W))
  })
  // parameter
  val man_Width = manWidth
  io.invalidExc := false.B

  /* 1. Compare Exp value + calculate exp difference + choose larger exp value
   * plus, by using the result of Cmp_Exp, adjust mantissas by shifting one of smaller value.
   */
  val Cmp_Exp = Module(new Cmp_two_unsigned_int(bit_length = expWidth))
  Cmp_Exp.io.in1 := io.in1.Exp.asUInt
  Cmp_Exp.io.in2 := io.in2.Exp.asUInt
  Cmp_Exp.io.is_in1_denormal := io.in1.isdenormal
  Cmp_Exp.io.is_in2_denormal := io.in2.isdenormal

  val Cmp_Exp_larger_Exp = Wire(UInt(expWidth.W)); Cmp_Exp_larger_Exp := 0.U
  val Cmp_Exp_exp_differnce = Wire(UInt(expWidth.W)); Cmp_Exp_exp_differnce := Cmp_Exp.io.difference
  // 1bit for implicit 1, and more 1bit for preventing overflow by addition
  val man_in1 = Wire(UInt((2 + manWidth).W)); man_in1 := Cat(0.U(1.W),(io.in1.isnormal.asUInt), io.in1.Man).asUInt
  val man_in2 = Wire(UInt((2 + manWidth).W)); man_in2 := Cat(0.U(1.W),(io.in2.isnormal.asUInt), io.in2.Man).asUInt
  val shifted_man = Wire(UInt((2 + manWidth*2).W)); shifted_man := 0.U
  val shifted_man_sign = Wire(Bool()); shifted_man_sign := false.B
  val not_shifted_man = Wire(UInt((2 + manWidth*2).W)); not_shifted_man := 0.U
  val not_shifted_man_sign = Wire(Bool()); not_shifted_man_sign := false.B
  //val man_with_small_exp = Wire(UInt((manWidth).W))

  when(Cmp_Exp.io.in1_large){
    Cmp_Exp_larger_Exp := io.in1.Exp.asUInt
    shifted_man := (Cat(man_in2, 0.U(manWidth.W)) >> Cmp_Exp_exp_differnce).asUInt
    shifted_man_sign := io.in2.sign
    not_shifted_man := Cat(man_in1, 0.U(manWidth.W))
    not_shifted_man_sign := io.in1.sign
    //man_with_small_exp := io.in2.Man
  }.elsewhen(Cmp_Exp.io.in2_large){
    Cmp_Exp_larger_Exp := io.in2.Exp.asUInt
    shifted_man := (Cat(man_in1, 0.U(manWidth.W)) >> Cmp_Exp_exp_differnce).asUInt
    shifted_man_sign := io.in1.sign
    not_shifted_man := Cat(man_in2, 0.U(manWidth.W))
    not_shifted_man_sign := io.in2.sign
    //man_with_small_exp := io.in1.Man
  }.elsewhen(Cmp_Exp.io.is_same){
    when(io.in1.Exp.asUInt > io.in2.Exp.asUInt){
      Cmp_Exp_larger_Exp := io.in1.Exp.asUInt
    }.otherwise{
      Cmp_Exp_larger_Exp := io.in2.Exp.asUInt
    }
    shifted_man := (Cat(man_in1, 0.U(manWidth.W))).asUInt
    shifted_man_sign := io.in1.sign
    not_shifted_man := Cat(man_in2, 0.U(manWidth.W))
    not_shifted_man_sign := io.in2.sign
  }
  io.test := 0.U
  /* 2. Add two mantissa (shifted or not) */
    val adder = Module(new Add_unsigned_int_with_sign(bit_length = 2+manWidth*2))
    adder.io.in1 := shifted_man
    adder.io.in2 := not_shifted_man
    adder.io.in1_sign := shifted_man_sign
    adder.io.in2_sign := not_shifted_man_sign
    //adder.io.result_sign
    val exp_before_normalize = Wire(UInt(expWidth.W)); exp_before_normalize := Cmp_Exp_larger_Exp
    val man_before_normalize = Wire(UInt((2+manWidth*2).W)); man_before_normalize := adder.io.result_abs

  /* 3. Normalize the mantissa and exponents */
  val sig_move_left = Wire(Bool()); sig_move_left := (man_before_normalize(manWidth*2 + 1).asUInt === 1.U(1.W))
   //ASSUME: Log2(man_Width) < expWidth
  val exp_after_normalize = Wire(UInt(expWidth.W))
  val man_after_normalize = Wire(UInt((2 + manWidth).W))
  val sig_how_move = Wire(UInt(expWidth.W)); sig_how_move := 0.U

  val more_move_bits = Wire(UInt(4.W)); more_move_bits := 0.U
  //val is_shift = Wire(UInt(1.W)); is_shift := false.B
  val mode = Wire(UInt(2.W)); mode := 3.U(2.W)

  when(exp_before_normalize=== 0.U){ //denormal + denormal
    exp_after_normalize := (man_before_normalize)(manWidth * 2).asUInt
    man_after_normalize := (man_before_normalize).asUInt(manWidth * 2 - 1, manWidth)
    mode := 0.U
    more_move_bits := 0.U
  }.otherwise{
    when(sig_move_left) {
      exp_after_normalize := exp_before_normalize + 1.U
      man_after_normalize := (man_before_normalize >> 1).asUInt(manWidth * 2 - 1, manWidth)
      mode := 1.U
      more_move_bits := 1.U
    }.otherwise {
      sig_how_move := (manWidth * 2).asUInt(expWidth.W) - (Log2(adder.io.result_abs).asUInt) //ASSUME: Log2(man_Width) < expWidth
      when(exp_before_normalize > sig_how_move) { //normalized
        exp_after_normalize := exp_before_normalize - sig_how_move
        man_after_normalize := (man_before_normalize << sig_how_move).asUInt(manWidth * 2 - 1, manWidth)
        more_move_bits := sig_how_move
        mode := 2.U
      }.otherwise { //denormalized
        exp_after_normalize := 0.U
        man_after_normalize := (man_before_normalize << (exp_before_normalize - 1.U)).asUInt(manWidth * 2 - 1, manWidth)
        more_move_bits := exp_before_normalize -1.U
        mode := 3.U
      }
    }
  }

  /* 4. rounding stage : Module[Rounding_hardware] */
  val check_rounding = Module(new Rounding_hardware(man_length= man_Width))
  check_rounding.io.mode := mode
  check_rounding.io.man_with_small_exp := man_before_normalize
  check_rounding.io.more_move_bits := more_move_bits
  check_rounding.io.is_odd := man_after_normalize(0)
  val man_rounding = Wire(UInt(manWidth.W)); man_rounding := man_after_normalize + check_rounding.io.is_rounding

  /* 5. Insert result to FPBit */
  io.fpOut.isNaN := io.in1.isNaN || io.in2.isNaN
  io.fpOut.isInf := io.in1.isInf || io.in1.isInf // + 계산중에
  io.fpOut.isZero := false.B //TODO
  // normalized or denormalized
  io.fpOut.isdenormal := false.B // TODO
  io.fpOut.isnormal := false.B // TODO
  // contents
  io.fpOut.sign := adder.io.result_sign
  io.fpOut.Exp := exp_after_normalize.asSInt
  io.fpOut.Man := man_rounding // mantissa bit + sign bit (1 bit)

//  /*check special value */
  val isInf_in_cal = Wire(Bool()); isInf_in_cal := (exp_after_normalize).andR
  when(io.in1.isNaN || io.in2.isNaN || ((io.in1.sign ^ io.in2.sign) && io.in1.isInf && io.in2.isInf)) {
    //result is Nan
    io.fpOut.isNaN := true.B
    io.fpOut.sign := 0.U
    io.fpOut.Exp := (-1).asSInt(expWidth.W)
    io.fpOut.Man := (1 << (manWidth - 1)).asUInt(manWidth.W)
  }.elsewhen(io.in1.isInf || io.in2.isInf || isInf_in_cal){
    //result is Infinite
    io.fpOut.isInf := true.B
    io.fpOut.sign := 0.U
    io.fpOut.Exp := (-1).asSInt(expWidth.W)
    io.fpOut.Man := 0.U(manWidth.W)
  }
}
class FP_Add(expWidth: Int, manWidth: Int) extends Module{
  val io = IO(new Bundle{
    val in1 = Input(UInt((1 + expWidth + manWidth).W))
    val in2 = Input(UInt((1 + expWidth + manWidth).W))
    val invalidExc = Output(Bool())
    val fpOut = Output(UInt((1 + expWidth + manWidth).W))
    val test  = Output(UInt((manWidth*2+2).W))
  })
  val input1 = from_fp_to_FPBit(expWidth, manWidth, io.in1)
  val input2 = from_fp_to_FPBit(expWidth, manWidth, io.in2)

  val inner_PE = Module(new FPBit_Add(expWidth = expWidth, manWidth = manWidth))

  inner_PE.io.in1 := input1
  inner_PE.io.in2 := input2
  io.invalidExc := inner_PE.io.invalidExc

  val result = from_FPBit_to_fp(inner_PE.io.fpOut).asUInt
  io.fpOut := result
  io.test := inner_PE.io.test

}


object FP_add_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new FP_Add(expWidth = 8, manWidth = 23))
}
