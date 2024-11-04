package PE.Components

import chisel3._

/*
* MAX2_EXP
* get 4 exponents with 8 bit_width
* return 2 exponents (top 2)
*/
class MAX2_EXP extends Module{
  val io = IO(new Bundle{
    val input_EXPs_4 = Input(Vec(4, UInt(8.W)))
    val output_EXPs_2 = Output(Vec(2, UInt(8.W)))
  })
  io.output_EXPs_2(0) := 0.U; io.output_EXPs_2(1) := 0.U
  val fst_stage_wire = VecInit(0.U(8.W), 0.U, 0.U, 0.U)
  when(io.input_EXPs_4(0) > io.input_EXPs_4(1)){ //CMP
    fst_stage_wire(0) := io.input_EXPs_4(0); fst_stage_wire(1) := io.input_EXPs_4(1)
  }.elsewhen(io.input_EXPs_4(0) < io.input_EXPs_4(1)){
    fst_stage_wire(0) := io.input_EXPs_4(1); fst_stage_wire(1) := io.input_EXPs_4(0)
  }.otherwise {
    fst_stage_wire(0) := io.input_EXPs_4(0); fst_stage_wire(1) := 0.U
  }
  when(io.input_EXPs_4(2) > io.input_EXPs_4(3)) { //CMP
    fst_stage_wire(2) := io.input_EXPs_4(2); fst_stage_wire(3) := io.input_EXPs_4(3)
  }.elsewhen(io.input_EXPs_4(2) < io.input_EXPs_4(3)) { //CMP
    fst_stage_wire(2) := io.input_EXPs_4(3); fst_stage_wire(3) := io.input_EXPs_4(2)
  }.otherwise {
    fst_stage_wire(2) := io.input_EXPs_4(2); fst_stage_wire(3) := 0.U
  }
  val snd_stage_wire = VecInit(0.U(8.W), 0.U)
  when(fst_stage_wire(0) > fst_stage_wire(2)){ //CMP
    io.output_EXPs_2(0) := fst_stage_wire(0); snd_stage_wire(0) := fst_stage_wire(2)
  }.elsewhen(fst_stage_wire(0) < fst_stage_wire(2)) { //CMP
    io.output_EXPs_2(0) := fst_stage_wire(2); snd_stage_wire(0) := fst_stage_wire(0)
  }.otherwise{
    io.output_EXPs_2(0) := fst_stage_wire(2); snd_stage_wire(0) := 0.U
  }
  when(fst_stage_wire(1) > fst_stage_wire(3)){ //MAX
    snd_stage_wire(1) := fst_stage_wire(1)
  }.otherwise{
    snd_stage_wire(1) := fst_stage_wire(3)
  }
  when(snd_stage_wire(0) > snd_stage_wire(1)){ //MAX
    io.output_EXPs_2(1) := snd_stage_wire(0)
  }. otherwise{
    io.output_EXPs_2(1) := snd_stage_wire(1)
  }
}
