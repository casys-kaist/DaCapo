package PE.Components

import chisel3._
import chisel3.util.{Cat, Log2, log2Floor}


// NOTE1 : (1+Integer.numberOfLeadingZeros(groupSize)) ==> 5
// Since there is limitation on amount of bits "<< operation"
class FP_generator(expWidth: Int, manWidth: Int, groupSize: Int) extends Module{
  var log_gs = log2Floor(groupSize)
  val io = IO(new Bundle{
    /* if manWidth = 4, groupSize = 16 ==> in_man is 24.W 
    and 0 x 6bit + (18bit) 1.xxx is normal case

    */
    val in_man = Input(UInt((2*manWidth+ log_gs + 8).W))
    val in_exp_not_minus_bias = Input(UInt((expWidth+1).W))
    val in_sign = Input(UInt(1.W))
    val result = Output(UInt(((1 + 8 + 23)).W))
  })

  /* Wires */
  val adjust_exp = Wire(UInt((1+expWidth).W)); adjust_exp := 0.U
  val man_move = Wire(UInt((1+expWidth).W)); man_move := 0.U
  val adjust_man = Wire(UInt((2*manWidth+ log_gs +8).W)); adjust_man := 0.U
  val exp_move = Wire(UInt(5.W)); exp_move := 0.U
  val result_man = Wire(UInt((2 * manWidth + 2 + log_gs + 8).W)); result_man := 0.U
  val result_exp = Wire(UInt(8.W)); result_exp := 0.U
  val final_man = Wire(UInt(23.W)); final_man := 0.U

  // STEP1: EXP calculation
  /* "adjust_exp" is used for exponents of final result calculate adjust_exp */
  when(io.in_exp_not_minus_bias > ((1<<(expWidth-1)) -1).asUInt){ /* normalized */
    adjust_exp := (io.in_exp_not_minus_bias - ((1<<(expWidth-1)) -1).asUInt).asUInt
    adjust_man := io.in_man
  }.otherwise{ /* denormalized */
    adjust_exp := 0.U
    man_move := (1<<(expWidth-1) -1).asUInt - io.in_exp_not_minus_bias + 1.U
    adjust_man := (io.in_man >> man_move)
  }

  // STEP2 Mantissa ADJUST
  /* lets see the adjust mantissa and change it to be fitted in floating_point format
  * if mantissa is larger than 2 --> adjust man and exp*/
  // NOTE1

  when(io.in_man === 0.U((2*manWidth+ log_gs +8).W)){
    /* case for zero: since input mantissa is sum of mantissa multiplication. if input mantissa is 0, result must be zero */
    result_man := 0.U; result_exp := 0.U
  }.otherwise{
    /*adjust_man*/
    when(Log2(adjust_man) >= (2.U * manWidth.asUInt(expWidth.W) + 8.U - 2.U)) {
      /* adjust_mantissa is larger than 1: so mantissa must be shifted right so that its value be 1.xx */
      exp_move := Log2(adjust_man) - (2 * manWidth + 8 - 2).asUInt(5.W) //NOTE1
      result_man := (adjust_man >> exp_move).asUInt
      result_exp :=  adjust_exp + exp_move
    }.otherwise {
      /* adjust_mantissa is smaller than 1: so mantissa must be shifted left so that its value be 1.xx and exp value decrease */
      exp_move := ((2 * manWidth + 8 - 2).asUInt(expWidth.W) - Log2(adjust_man))
      when(adjust_exp < exp_move) {
        /* adjust exp is smaller than exp_move : format change to denormalized format */
        result_exp := 0.U
        result_man := (adjust_man << adjust_exp).asUInt
      }.elsewhen(adjust_exp === exp_move) {
        /*adjust exp same with exp_move : format is still in normalized format */
        result_exp := 0.U
        result_man := (adjust_man << (exp_move-1.U)).asUInt
      }otherwise {
        /*adjust exp is larger than exp_move : format is still in normalized format */
        result_exp := (adjust_exp - exp_move)
        result_man := (adjust_man << exp_move).asUInt
      }
    }
  }

  // Concatenate
  io.result := 0.U(23.W)
  if(2*manWidth + 8 -2 < 23){
    final_man := Cat(result_man(2 * manWidth + 8 - 2 - 1, 0), 0.U((23 - (2 * manWidth + 8 - 2)).W))
    io.result := Cat(io.in_sign, result_exp, final_man)
  }else{
    final_man := result_man(2 * manWidth + 8 - 2 - 1, 2 * manWidth + 8 - 2 - 1 -22)
    io.result := Cat(io.in_sign, result_exp, final_man)
  }

}

object FP_gen_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new FP_generator(expWidth = 8, manWidth = 2, groupSize = 16))
}