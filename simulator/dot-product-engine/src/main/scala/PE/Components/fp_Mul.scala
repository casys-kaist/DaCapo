package PE.Components

import PE.FPMAC.{FPBit_bundle, from_FPBit_to_fp, from_fp_to_FPBit}
import chisel3._
import chisel3.util.{Cat, Log2}

import java.lang.Double.doubleToRawLongBits
import java.lang.Float.floatToRawIntBits
import java.math.BigInteger



object FloatUtils{
  def floatToBigInt(x: Float): BigInt = {
    val integer = floatToRawIntBits(x)
    var byte_array = new Array[Byte](5)

    byte_array(0) = 0

    for (i <- 1 to 4) {
      byte_array(i) = ((integer >> ((4 - i) * 8)) & 0xff).toByte
    }

    BigInt(new BigInteger(byte_array))
  }

  def doubleToBigInt(x: Double): BigInt = {
    val integer = doubleToRawLongBits(x)
    var byte_array = new Array[Byte](9)

    byte_array(0) = 0

    for (i <- 1 to 8) {
      byte_array(i) = ((integer >> ((8 - i) * 8)) & 0xff).toByte
    }

    BigInt(new BigInteger(byte_array))
  }
}
/*
* This module do multiplication of a pair of floating point.
* Input is not 32bit fp, but FPBit class which contain bits and some information
* That converting is done previous Module FP_Mul
*/
/* Module : FPBit_Mul */
class FPBit_Mul(expWidth: Int, manWidth: Int) extends Module{
  val io = IO(new Bundle {
    val in1 = Input(new FPBit_bundle(expWidth, manWidth))
    val in2 = Input(new FPBit_bundle(expWidth, manWidth))
    val invalidExc = Output(Bool())
    val fpOut = Output(new FPBit_bundle(expWidth, manWidth))
  })
  val man_width = manWidth.asUInt
  /*---------------------------------------------------------
  Calculation: Stage 1
  ---------------------------------------------------------*/

  val notNaN_invalidExc = (io.in1.isInf && io.in2.isZero) || (io.in1.isZero && io.in2.isInf)
  val notNaN_isInfOut = io.in1.isInf || io.in2.isInf
  val notNaN_isZeroOut = io.in1.isZero || io.in2.isZero
  val notNaN_signOut = io.in1.sign ^ io.in2.sign

  // Exponent calculation
  var raw_sExpOut = Wire(UInt((expWidth +1).W)); raw_sExpOut := 0.U
  raw_sExpOut := (Cat(0.U(1.W), io.in1.Exp.asUInt) + Cat(0.U(1.W), io.in2.Exp.asUInt))
  // Mantissa calculation
  var raw_ManOut = Wire(UInt((manWidth*2 +2).W)); raw_ManOut := 0.U
  when(io.in1.isnormal && io.in2.isnormal){
    raw_ManOut := (Cat(1.U, io.in1.Man) * Cat(1.U, io.in2.Man))(manWidth*2+1,0)
    //possible Inf
  } .elsewhen(io.in1.isdenormal && io.in2.isnormal){
    raw_ManOut := Cat(0.U(1.W), io.in1.Man * Cat(1.U, io.in2.Man))(manWidth*2+1,0)
    //possible zero
  } .elsewhen(io.in1.isnormal && io.in2.isdenormal){
    raw_ManOut := Cat(0.U(1.W), Cat(1.U, io.in1.Man) * io.in2.Man)(manWidth*2+1,0)
    //possible zero
  } .otherwise{// a, b both denormalization
    raw_ManOut := Cat(0.U(2.W),(io.in1.Man * io.in2.Man))(manWidth*2+1,0)
  }
  /*---------------------------------------------------------
    Calculation: Stage 2 (Adjust)
    ---------------------------------------------------------*/
  // exponent adjust: Check Mantissa (exp += 1 or not)
  var Adjust_raw_sExpOut = Wire(UInt((expWidth +1).W)); Adjust_raw_sExpOut := 0.U
  var Adjust_raw_ManOut = Wire(UInt((manWidth*2 + 2).W)); Adjust_raw_ManOut := 0.U

  when(raw_ManOut(manWidth * 2 + 1) === 1.U(1.W)) { // adjust Exp
    Adjust_raw_sExpOut := (raw_sExpOut + 1.U)(expWidth, 0)
    Adjust_raw_ManOut := Cat(0.U(1.W), (raw_ManOut >> 1).asUInt)
  }. otherwise{
    Adjust_raw_sExpOut := raw_sExpOut
    Adjust_raw_ManOut := raw_ManOut
  }
  // overflow
  var overflow_sExpOut = Wire(Bool());
  overflow_sExpOut := false.B
  when(Adjust_raw_sExpOut(expWidth) === 1.U(1.W)) {
    overflow_sExpOut := true.B
  }.otherwise {
    overflow_sExpOut := false.B
  }
  /*---------------------------------------------------------
      check denormal : Stage 3
    ---------------------------------------------------------*/
  var denormal_in_cal = Wire(Bool()); denormal_in_cal := false.B
  var denormal_man_move = Wire(UInt(expWidth.W)); denormal_man_move := 0.U
  denormal_man_move := (((1 << (expWidth - 1)) - 1).asUInt((expWidth+1).W) - Adjust_raw_sExpOut.asUInt)

  when(Adjust_raw_sExpOut(expWidth-1) === 0.U(1.W) && !overflow_sExpOut){
    denormal_in_cal := true.B
  }.otherwise {
    denormal_in_cal := false.B
  }
  /*---------------------------------------------------------
    special value(Inf) check : exp value check
  ---------------------------------------------------------*/
  var Inf_in_cal = Wire(Bool()); Inf_in_cal :=false.B
  when(((Adjust_raw_sExpOut + 2.U)(expWidth) === 1.U(1.W)) && ((Adjust_raw_sExpOut + 2.U)(expWidth-1) === 1.U(1.W)) ){//((3.U << (expWidth - 1)).asUInt)) {
    Inf_in_cal := true.B
  }. elsewhen(notNaN_isInfOut) {
    Inf_in_cal := true.B
  }. otherwise{
    Inf_in_cal := false.B
  }
  /*---------------------------------------------------------
    Calculation: Stage 3
    ---------------------------------------------------------*/

  var no_rounding_raw_ManOut = Wire(UInt((manWidth + 1).W));
  no_rounding_raw_ManOut := Adjust_raw_ManOut(manWidth * 2, manWidth)

  /*---------------------------------------------------------
    get result[final] Man, Exp value : Stage 4
  ---------------------------------------------------------*/
  var result_sExpOut = Wire(SInt(expWidth.W))
  var result_ManOut = Wire(UInt((manWidth+2).W)); result_ManOut := 0.U
  val is_denormal_normal_mul = (io.in1.isdenormal && io.in2.isnormal) || (io.in1.isnormal && io.in2.isdenormal)
  var rounding_result_ManOut = Wire(UInt((manWidth).W)); rounding_result_ManOut := 0.U
  //var test =Wire(UInt(1.W)); test := 0.U
  when(notNaN_invalidExc === true.B) {
    /* Case 1: NaN */
    result_ManOut := (1 << (manWidth)).U((manWidth+2).W)
    result_sExpOut := (-1).S((expWidth).W)
    //test:=1.U(1.W)
  }. elsewhen(Inf_in_cal === true.B) {
    /* Case 2-1: Infinite */
    result_ManOut := 0.U(manWidth.W)
    result_sExpOut := (-1).S((expWidth).W)
    //test:=1.U(1.W)
  }.elsewhen(notNaN_isZeroOut === true.B) {
    /* Case 2-2: Zero */
    result_ManOut := 0.U(manWidth.W)
    result_sExpOut := (0).S((expWidth).W)
    //test:=1.U(1.W)
  }. elsewhen(!is_denormal_normal_mul&&denormal_in_cal === true.B){        /* denormalize, Zero */
    /* Case 3: denormal * denormal, get denormalized result or Zero*/
    /* denormalize value mantissa rounding */
    result_ManOut := (no_rounding_raw_ManOut>>(denormal_man_move.asUInt))
    //test:= 1.U(1.W)
    result_sExpOut := 0.S
  }. elsewhen(is_denormal_normal_mul&&denormal_in_cal === true.B) {
    /* Case 4: normal * denormal ==> denormal value */
    /* denormalize value mantissa rounding */
    result_ManOut := (no_rounding_raw_ManOut >> (denormal_man_move-1.U)).asUInt

    //test:= 1.U(1.W)
    result_sExpOut := 0.S
  }. elsewhen(is_denormal_normal_mul &&denormal_in_cal === false.B) { //normal x denormal ==> normal
    /* Case 5: normal * denormal ==> normal value */
    //Exp -(manWidth.U - Log2(no_rounding_raw_ManOut)) +1 ,  Mantissa << (manWidth.U - Log2(no_rounding_raw_ManOut))
    //test:= 1.U(1.W)
    var how_many_man_move = (manWidth.U - Log2(no_rounding_raw_ManOut) + 1.U).asSInt //good
    var how_many_exp_sub = (how_many_man_move - 1.S)
    var be_denoral = Wire(Bool()); be_denoral := false.B
    var case5_exp = Wire(SInt((expWidth+2).W)); case5_exp := Cat(0.S(1.W), (Adjust_raw_sExpOut.asUInt + ((1 << (expWidth - 1)) + 1).asUInt)(expWidth, 0)).asSInt
    var case5_man = Wire(UInt((manWidth*2+2).W)); case5_man := Adjust_raw_ManOut//(manWidth*2,manWidth)
    when(how_many_exp_sub > Cat(0.S(1.W),case5_exp(expWidth-1, 0)).asSInt){
      be_denoral := true.B
    }
    var tmp_check = Wire(SInt((expWidth+1).W)) ;tmp_check := (how_many_exp_sub - case5_exp).asSInt
    //test :=true.B
    when(be_denoral=== true.B){
      result_sExpOut := 0.S
      result_ManOut := (case5_man<<(case5_exp(expWidth-1, 0)).asUInt)(manWidth*2,manWidth-1).asUInt
    }.otherwise{
      result_sExpOut := case5_exp.asSInt - how_many_exp_sub + 1.S
      result_ManOut := (case5_man << (how_many_man_move-1.S).asUInt)(manWidth*2,manWidth-1).asUInt//(Adjust_raw_ManOut<<(manWidth.U - Log2(no_rounding_raw_ManOut))).asUInt(manWidth*2,manWidth-1)
    }
  }.otherwise{                                    /* normalize value */
    /* Case 6: normalized value */
    result_ManOut := Adjust_raw_ManOut(manWidth * 2, manWidth-1)
    result_sExpOut := (Adjust_raw_sExpOut.asUInt + ((1 << (expWidth - 1)) + 1).asUInt)(expWidth - 1, 0).asSInt
    //test := 1.U
  }
  // mantissa rounding
  //when(test === 1.U(1.W)){
    when(result_ManOut(0) === 1.U(1.W)) {
      rounding_result_ManOut := ((result_ManOut >> 1.U).asUInt + 1.U)
    }.otherwise {
      rounding_result_ManOut := (result_ManOut >> 1.U).asUInt
    }
  //}



  /*---------------------------------------------------------
    Insert Value to <<Output>>
  ---------------------------------------------------------*/
  // Variables
  val Zero_in_cal = (result_ManOut.orR === 0.U(1.W) && result_sExpOut.asUInt.orR === 0.U(1.W))

  //Output: invalidExc
  io.invalidExc := io.in1.isSigNan() || io.in2.isSigNan() || notNaN_invalidExc
  //Output: fpOut
  //special value
  io.fpOut.isNaN := io.in1.isNaN || io.in2.isNaN
  io.fpOut.isInf := notNaN_isInfOut           || Inf_in_cal
  io.fpOut.isZero := notNaN_isZeroOut || Zero_in_cal
  //normal, denormal
  io.fpOut.isdenormal := ((result_sExpOut === 0.S) && !(result_ManOut === 0.U))
  io.fpOut.isnormal := !(result_sExpOut === 0.S) && !(io.fpOut.isNaN || io.fpOut.isInf)
  // contents
  io.fpOut.sign := notNaN_signOut||notNaN_invalidExc
  io.fpOut.Exp := result_sExpOut
  io.fpOut.Man := rounding_result_ManOut
}



/* FP_Mul Module it contain FPBit_Mul
* This process is for convert pure 32fp to container which have
* not only bits but also information making calculation simple
* */
class FP_Mul(expWidth: Int, manWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in1 = Input(UInt((1 + expWidth + manWidth).W))
    val in2 = Input(UInt((1 + expWidth + manWidth).W))
    val invalidExc = Output(Bool())
    val fpOut = Output(UInt((1 + expWidth + manWidth).W))
  })
  val input1 = from_fp_to_FPBit(expWidth, manWidth, io.in1)
  val input2 = from_fp_to_FPBit(expWidth, manWidth, io.in2)

  val inner_PE = Module(new FPBit_Mul(expWidth= expWidth, manWidth= manWidth))

  inner_PE.io.in1 := input1
  inner_PE.io.in2 := input2
  io.invalidExc := inner_PE.io.invalidExc

  val result = from_FPBit_to_fp(inner_PE.io.fpOut).asUInt
  io.fpOut := result
}
