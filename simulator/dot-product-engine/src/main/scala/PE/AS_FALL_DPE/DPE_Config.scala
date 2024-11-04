package PE.AS_FALL_DPE
import chisel3.util._
import chisel3._
/*
  Description
  You don't need to modify this.
  If you want to change systolic array dimension, change "dim".
*/
object DPE_Config {
  /* TEST parameter START */
  val dim = 5 
  /* TEST parameter END */
  /* bit width paprameter */
  val man_unit_width = 2;
  val eight_bit_mul_result_man_width = man_unit_width*8 //16
  val four_bit_mul_result_man_width = man_unit_width*4;//8
  val two_bit_mul_result_man_width = man_unit_width*2 //4
  val num_comb_of_four_bit_mul = 4

  //val flow_direction = UInt(1.W) //1 == up, 0 == down
  val act_bundle = new Bundle {
    val op_man = Vec(16, UInt(man_unit_width.W))
    val op_sign = Vec(16, UInt(1.W))
    val op_exp = UInt(8.W)
    val bit_width_sig = UInt(2.W) /* {is_eight_bit_mul, is_four_bit_mul} */
    val is_data = UInt(1.W)
    val m_exp = Vec(16, UInt(1.W))
  }
  val wgt_bundle = new Bundle {
    val op_man = Vec(16, UInt(man_unit_width.W))
    val op_sign = Vec(16, UInt(1.W))
    val op_exp = UInt(8.W)
    val life_count = SInt(8.W) // dim <= 256
  }
  val OS_wgt_bundle = new Bundle {
    val op_man = Vec(16, UInt(man_unit_width.W))
    val op_sign = Vec(16, UInt(1.W))
    val op_exp = UInt(8.W)
    val m_exp = Vec(16, UInt(1.W))
  }
}
