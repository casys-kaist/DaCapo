package PE.Components

import chisel3._
import chisel3.util.Enum
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class fp_Accumulator extends Module {
  val io = IO(new Bundle {
    val input = Input(UInt((1 + 8 + 23).W))
    val input_init = Input(UInt((1 + 8 + 23).W))
    val fpOut = Output(UInt((1 + 8 + 23).W))
    val init = Input(UInt(1.W))
  })
  io.fpOut := 0.U
  val adder = Module(new FP_Add(expWidth=8, manWidth=23))
  val accumulator = RegInit(0.U((1 + 8 + 23).W))
  val os_dataflow :: os_get_result :: forward_ws_dataflow :: backward_ws_dataflow :: fill_weight :: Nil = Enum(5)
  when(io.init === 1.U(1.W)){
    adder.io.in1 := io.input
    adder.io.in2 := io.input_init //**
    accumulator := adder.io.fpOut
    io.fpOut := accumulator
  }.otherwise{
    adder.io.in1 := io.input
    adder.io.in2 := accumulator
    accumulator := adder.io.fpOut
    io.fpOut := accumulator
  }

}
class Accumulator extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPMul" should "pass" in {
    test((new fp_Accumulator))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        var expected = 0.0.floatValue()
        var past = 0.0.floatValue()
        for (i <- 0 until 100) {
            var a = scala.util.Random.nextFloat() // Math.pow(10, j).floatValue())
            expected = expected + a
            println(i+"a=" + a)
            println("r =" + expected)
            PE.io.input.poke(FloatUtils.floatToBigInt(a))
            PE.io.fpOut.expect(FloatUtils.floatToBigInt(past))
            past = expected
            PE.clock.step()
        }
      }
  }
}