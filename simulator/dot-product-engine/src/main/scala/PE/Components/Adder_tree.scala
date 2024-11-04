package PE.Components

import chisel3._


class Adder_tree(num_of_op: Int, num_of_bit: Int) extends Module{
  val io = IO(new Bundle{
    val operands = Input(Vec(num_of_op, UInt(num_of_bit.W)))
    val signs = Input(Vec(num_of_op, UInt(1.W)))
    val result = Output(SInt((1+num_of_bit).W))
  })
  val signed_operand = Wire(Vec(num_of_op, SInt((1+num_of_bit).W)))
  for(i<- 0 until num_of_op){
    when(io.signs(i) === 1.U){
      signed_operand(i) := (-(io.operands(i).asSInt))
    }.otherwise{
      signed_operand(i) := (io.operands(i).asSInt)
    }
  }
  io.result := signed_operand.reduceTree(_+_)
}
