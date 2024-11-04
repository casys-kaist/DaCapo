package PE.MX_base_DPE
import chisel3.util.{Enum, Log2, log2Floor}
import chisel3._
object MX_config {
  // Enum
      /* Mode ENUM */
  val os_dataflow :: os_get_result :: forward_ws_dataflow :: backward_ws_dataflow :: fill_weight :: Nil = Enum(5)
/*-----------------------------------------------*/
  //config for converter and PE
  val group_size = 8;       //{8, 16, 32}(fixed)
  val man_width_V = 7       //{2, 4, 6, 8}
  val man_width_H = 7       //{2, 4, 6, 8}
  /*-----------------------------------------------*/
  val num_in_converter = 32 //(fixed)
  val num_in_PE = 32        //(fixed)
  val sub_group_size = 2    //(fixed)
  // PE array

  // Basic setting (fixed)
  val fp32_width = 32
  val fp32_exp_width = 8
  val man_width_max = 8
  val num_scale_factor = num_in_PE/sub_group_size
  val num_partition_max = 4 //(num_in_converter or PE / group size(8))
  val num_in_one_partition = 8
  val PE_num_exp = 4
  //bit_width
  val scaling_factor_width = 1
  val sign_width = 1
  val mode_width = 3
  val group_size_width = 6 //(max 32)
  val mul_result_width = 2*man_width_max
  val shifted_result_width = mul_result_width + 2
  val adder_tree_result_width = shifted_result_width + 5 //(5 must be fixed)//(max sum 32 operand)
  val man_diff_width = ((math.log(man_width_max)/math.log(2))+1).toInt
  var log_gs = log2Floor(32) //max group_size : 32
  //TODO
  val tmp_bit_for_fp_gen = 6

  // Data Bundle
  val Data_bundle = new Bundle {
    val in_exp = Input(Vec(PE_num_exp, UInt(fp32_exp_width.W)))
    val scale_factor = Input(Vec(num_scale_factor, UInt(scaling_factor_width.W)))
    val in_man = Input(Vec(num_in_PE, UInt(man_width_max.W)))
    val in_sign = Input(Vec(num_in_PE, UInt(sign_width.W)))
    val prev_result = Input(UInt(fp32_width.W))
    val mode = Input(UInt(mode_width.W))
  }
  val Data_bundle1 = new Bundle {
    val in_exp = Input(Vec(PE_num_exp, UInt(fp32_exp_width.W)))
    val scale_factor = Input(Vec(num_scale_factor, UInt(scaling_factor_width.W)))
    val in_man = Input(Vec(num_in_PE, UInt(man_width_max.W)))
    val in_sign = Input(Vec(num_in_PE, UInt(sign_width.W)))
    val prev_result = Input(UInt(fp32_width.W))
    val mode = Input(UInt(mode_width.W))
  }

}
