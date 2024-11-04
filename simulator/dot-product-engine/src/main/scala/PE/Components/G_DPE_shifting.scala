package PE.Components

import chisel3._
import chisel3.util.Cat
/* After G_DPE, shifting mantissa depends on exp_info */
class G_DPE_shifter(manWidth : Int, groupSize : Int) extends Module{
  val io = IO(new Bundle{
    val mantissa = Input(UInt((2 * manWidth + Integer.numberOfLeadingZeros(groupSize)).W))
    val num_shifting = Input(Bits(8.W))

    val result = Output(UInt((2 * manWidth + Integer.numberOfLeadingZeros(groupSize) + 8).W))
    // add extra 8bit to minimized mantissa values loss
  })
  val tmp_wire = Wire(UInt((2 * manWidth + Integer.numberOfLeadingZeros(groupSize) + 8).W))
  tmp_wire := Cat(io.mantissa, 0.U(8.W))

  io.result := (tmp_wire >> io.num_shifting)
}
