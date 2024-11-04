package PE.MX_base_DPE
import chisel3._
import chisel3.util._
import PE.MX_base_DPE.MX_config._

import scala.math._
class MX_Converter extends Module{
  val io = IO(new Bundle{
    val in_exps = Input(Vec(num_in_converter, UInt(fp32_exp_width.W)))
    val in_mans = Input(Vec(num_in_converter, UInt(man_width_max.W)))
    val group_size = Input(UInt(6.W))

    val max_exp = Output(Vec(num_partition_max, UInt(fp32_exp_width.W)))
    val scale_factor = Output(Vec(num_scale_factor, UInt(scaling_factor_width.W)))
    val shifted_mans =Output(Vec(num_in_converter, UInt(man_width_max.W)))
  })

  /*STEP1: get max exp*/
  val G0 = Wire(Vec(num_in_one_partition, UInt(fp32_exp_width.W)))
  val G1 = Wire(Vec(num_in_one_partition, UInt(fp32_exp_width.W)))
  val G2 = Wire(Vec(num_in_one_partition, UInt(fp32_exp_width.W)))
  val G3 = Wire(Vec(num_in_one_partition, UInt(fp32_exp_width.W)))
  val max_expW = Wire(Vec(num_partition_max, UInt(fp32_exp_width.W)))
  val max_exp_finalW = Wire(Vec(num_partition_max, UInt(fp32_exp_width.W)))

  for(i<- 0 until num_in_one_partition){
    G0(i) := io.in_exps(i)
    G1(i) := io.in_exps(num_partition_max + i)
    G2(i) := io.in_exps(num_partition_max*2 + i)
    G3(i) := io.in_exps(num_partition_max*3 + i)
  }

  max_expW(0) := G0.reduceTree((a,b)=>Mux(a > b, a, b))
  max_expW(1) := G1.reduceTree((a,b)=>Mux(a > b, a, b))
  max_expW(2) := G2.reduceTree((a,b)=>Mux(a > b, a, b))
  max_expW(3) := G3.reduceTree((a,b)=>Mux(a > b, a, b))

  when(io.group_size === 8.U){
    max_exp_finalW(0) := max_expW(0); max_exp_finalW(1) := max_expW(1);
    max_exp_finalW(2) := max_expW(2); max_exp_finalW(3) := max_expW(3);
  }.elsewhen(io.group_size === 16.U){
    max_exp_finalW(0) := Mux(max_expW(0)>max_expW(1), max_expW(0), max_expW(1)); max_exp_finalW(1) := Mux(max_expW(0)>max_expW(1), max_expW(0), max_expW(1));
    max_exp_finalW(2) := Mux(max_expW(2)>max_expW(3), max_expW(2), max_expW(3)); max_exp_finalW(3) := Mux(max_expW(2)>max_expW(3), max_expW(2), max_expW(3));
  }.otherwise{
    val a = Mux(max_expW(0)>max_expW(1), max_expW(0), max_expW(1))
    val b = Mux(max_expW(2)>max_expW(3), max_expW(2), max_expW(3))
    max_exp_finalW(0) := Mux(a>b, a, b);
    max_exp_finalW(1) := Mux(a>b, a, b);
    max_exp_finalW(2) := Mux(a>b, a, b);
    max_exp_finalW(3) := Mux(a>b, a, b);
  }
  io.max_exp(0) := max_exp_finalW(0)
  io.max_exp(1) := max_exp_finalW(1)
  io.max_exp(2) := max_exp_finalW(2)
  io.max_exp(3) := max_exp_finalW(3)

  /*get scale_factor*/
  val max_exp_subgW = Wire(Vec(num_scale_factor, UInt(fp32_exp_width.W)))
  val scale_factorW = Wire(Vec(num_scale_factor, UInt(scaling_factor_width.W)))
  for (i <- 0 until num_scale_factor){
    max_exp_subgW(i) := Mux(io.in_exps(i*2)>io.in_exps(i*2+1),io.in_exps(i*2),io.in_exps(i*2+1))
    scale_factorW(i) := Mux(max_exp_finalW(i/4) > max_exp_subgW(i), 1.U(scaling_factor_width.W), 0.U(scaling_factor_width.W))
    io.scale_factor(i) := scale_factorW(i)
  }

  /*shifting mantissa*/
  val exp_diff = Wire(Vec(num_in_converter, UInt(fp32_exp_width.W)))
  val shifted_manW = Wire(Vec(num_in_converter, UInt(man_width_max.W)))
  for(i <- 0 until num_in_converter){
    exp_diff(i) := max_exp_finalW(i/num_in_one_partition) - (io.in_exps(i) + Cat(0.U(7.W),scale_factorW(i/2)))
    shifted_manW(i) := io.in_mans(i)>>exp_diff(i)
    io.shifted_mans(i) := shifted_manW(i)
  }
}
