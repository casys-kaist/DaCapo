package PE.Components

import chisel3._

import scala.collection.mutable.ArrayBuffer

class Comparator_Max4(bitWidth: Int, groupSize: Int) extends Module {
  private val max_sh_exps = 4
  private val num_partition = 4
  val io = IO(new Bundle{
    val values_vec = Input(Vec(groupSize, Bits(bitWidth.W)))
    val largest_values = Output(Vec(num_partition, Vec(max_sh_exps, UInt(bitWidth.W))))
    val GroupSize_signal = Input(Bits(2.W))
  })
  val Stage1_MAX4 = ArrayBuffer[MAX4](); for(i<-0 until 4){Stage1_MAX4 += Module(new MAX4)}
  val Stage2_MAX4 = ArrayBuffer[MAX4](); for(i<-0 until 2){Stage2_MAX4 += Module(new MAX4)}
  val Stage3_MAX4 = Module(new MAX4)
  for(i<- 0 until num_partition*max_sh_exps){
    io.largest_values(i/4)(i%4) := 0.U
  }
  for(i<- 0 until 32){
    Stage1_MAX4(i/8).io.in_exps(i%8) := io.values_vec(i)
  }
  for(i<- 0 until 16){
    Stage2_MAX4(i/8).io.in_exps(i%8) := Stage1_MAX4(i/4).io.max4_exp(i%4)
  }
  for(i<- 0 until 8){
    Stage3_MAX4.io.in_exps(i%8) := Stage2_MAX4(i/4).io.max4_exp(i%4)
  }
  when(io.GroupSize_signal === 0.U){        //GS 8
    for(i<- 0 until 16) {
      io.largest_values(i / 4)(i % 4) := Stage1_MAX4(i / 4).io.max4_exp(i % 4)
    }
  }.elsewhen(io.GroupSize_signal ===1.U){   //GS 16
    for(i<- 0 until 8){
      io.largest_values((i/4)*2)(i%4) := Stage2_MAX4(i/4).io.max4_exp(i%4)
      io.largest_values(1+(i/4)*2)(i%4) := Stage2_MAX4(i/4).io.max4_exp(i%4)
    }
  }.elsewhen(io.GroupSize_signal === 3.U){  //GS 32
    for(i<- 0 until 16){
      io.largest_values((i/4))(i%4) := Stage3_MAX4.io.max4_exp(i%4)
    }
  }
}

class Shifter_sh_exp_4(groupSize: Int) extends Module{
  val io = IO(new Bundle{
    val FP_exp_vec = Input(Vec(groupSize, Bits(8.W)))
    val FP_man_vec = Input(Vec(groupSize, Bits((1+23).W)))
    val largest_values = Input(Vec(4, Vec(4, UInt(8.W))))
    val num_shared_exp = Input(UInt(2.W))

    val shifted_man = Output(Vec(groupSize, Bits((1+23).W)))
    val exp_info = Output(Vec(groupSize, Bits(2.W)))
  })
  val Exp_diff = Wire(Vec(groupSize, Bits(8.W)))
  val Exp_info = Wire(Vec(groupSize, Bits(2.W)))
  for(i<- 0 until groupSize){
    Exp_diff(i) := 0.U; Exp_info(i) := 0.U
  }
  val Exp_info_tmp = Wire(Vec(groupSize, Bits(2.W)))
  for (g <- 0 until 4){
    for(i <- 8*g until 8*(1+g)){
      Exp_info_tmp(i) := Mux(io.largest_values(g)(2) >= io.FP_exp_vec(i), Mux(io.largest_values(g)(3)>=io.FP_exp_vec(i), 3.U, 2.U), Mux(io.largest_values(g)(1)>=io.FP_exp_vec(i), 1.U, 0.U))
      Exp_info(i) := Mux(Exp_info_tmp(i) > io.num_shared_exp, io.num_shared_exp, Exp_info_tmp(i))
      Exp_diff(i) := io.largest_values(g)(Exp_info(i)) - io.FP_exp_vec(i)
    }
    for(i<- 0 until groupSize){
      when(Exp_diff(i) > 8.U){
        io.shifted_man(i) := 0.U(24.W)
      }.otherwise{
        io.shifted_man(i) := ((io.FP_man_vec(i))) >> (Exp_diff(i))
      }
    }
    io.exp_info := Exp_info
  }
}