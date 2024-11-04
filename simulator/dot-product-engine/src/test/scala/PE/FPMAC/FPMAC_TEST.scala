package PE.FPMAC

import PE.Components.FloatUtils
import chisel3.stage.ChiselStage
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/* This is for making verilog file */
object FPMAC_V extends App{
  (new ChiselStage).emitVerilog(new FPMAC)
}
/* This is simple Test class */
/* Test compare between the result of FPMAC and one of value operated with * */
class FPMAC_TEST extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPMAC_TEST" should "pass" in {
    test((new FPMAC))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        (new ChiselStage).emitVerilog(new FPMAC)
        var expected = 0.0.floatValue()
        val iter = 100
        println("This test do 100 MAC operation, and operands are (0.0 ~ 1.0) float values")
        for (i <- 0 until iter) {
          var a = scala.util.Random.nextFloat()
          var b = scala.util.Random.nextFloat()
          expected = expected + a*b
          //println(i + "th " + "a*b = " + a*b)
          println(f"$i th expected result = " + expected)
          PE.io.fp_H.poke(FloatUtils.floatToBigInt(a))
          PE.io.fp_V.poke(FloatUtils.floatToBigInt(b))
          PE.clock.step()
          var result = java.lang.Float.intBitsToFloat(PE.io.result.peek().litValue().toInt)
          println(f"$i th PE result = " + result)
          var error = math.abs((expected - result)/expected)*100.toFloat
          println(f"$i th error : $error\n")
          
        }
      }
  }
}
