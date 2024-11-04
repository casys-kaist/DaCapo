package PE.Component

import PE.Components.{FP_Add, FloatUtils}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/* The TEST : "FPAdd_no_minus_no" and "FPAdd_no_plus_no" is the main for this tb */
class FPAdd_deno_plus_deno extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_deno_plus_deno" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for(k <- 0 until 100) {
          for (i <- 38 until 45) {
            for (j <- 38 until 45) {
              var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
              var b = (scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
              val expected = a + b
              println("n-n" + ":" + a + "+" + b + "=" + expected)
              println("a=" + FloatUtils.floatToBigInt(a))
              println("b=" + FloatUtils.floatToBigInt(b))
              println("r =" + FloatUtils.floatToBigInt(a + b) +"\n")
              PE.io.in1.poke(FloatUtils.floatToBigInt(a))
              PE.io.in2.poke(FloatUtils.floatToBigInt(b))
              PE.io.test.expect(0)
              PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
            }
          }
        }
      }
  }
}
class FPAdd_deno_minus_deno extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_deno_minus_deno" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for(k <- 0 until 100) {
          for (i <- 38 until 39) {
            for (j <- 38 until 39) {
              var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
              var b = (-scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
              val expected = a + b
              println("n-n" + " : " + a + " - " + b + " = " + expected)
              println("a= " + FloatUtils.floatToBigInt(a))
              println("b= " + FloatUtils.floatToBigInt(b))
              println("r= " + FloatUtils.floatToBigInt(a + b))
              PE.io.in1.poke(FloatUtils.floatToBigInt(a))
              PE.io.in2.poke(FloatUtils.floatToBigInt(b))
              PE.io.test.expect(0)
              PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
            }
          }
        }
      }
  }
}
class FPAdd_no_plus_deno extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_no_plus_deno" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- 38 until 40) {
          for (j <- -38 until 38) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            var b = (scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            val expected = a + b
            println("n-n" + ": " + a + "+ " + b + " = " + expected)
            println("a= " + FloatUtils.floatToBigInt(a))
            println("b= " + FloatUtils.floatToBigInt(b))
            println("r= " + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
          }
        }
      }
  }
}
class FPAdd_no_minus_deno extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_no_minus_deno" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- 38 until 40) {
          for (j <- -38 until 38) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            var b = (-scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            val expected = a + b
            println("n-n" + " : " + a + " - " + b + " = " + expected)
            println("a= " + FloatUtils.floatToBigInt(a))
            println("b= " + FloatUtils.floatToBigInt(b))
            println("r= " + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            // PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
            var result = java.lang.Float.intBitsToFloat(PE.io.fpOut.peek().litValue().toInt)
          }
        }
      }
  }
}
/*MAIN*/
class FPAdd_no_plus_no extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_no_plus_no" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- -38 until 38) {
          for (j <- -38 until 38) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            var b = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            val expected = a + b
            println("SW_golden_result" + " : " + a + " + " + b + " = " + expected)
            // println("a= " + FloatUtils.floatToBigInt(a))
            // println("b= " + FloatUtils.floatToBigInt(b))
            // println("r= " + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            // PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
            var result = java.lang.Float.intBitsToFloat(PE.io.fpOut.peek().litValue().toInt)
            println(f"FPAdd result[Chisel] = $result\n")

          }
        }
      }
  }
}
/*MAIN*/
class FPAdd_no_minus_no extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_no_minus_no" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- -38 until 38) {
          for (j <- -38 until 38) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            var b = (-scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            val expected = a + b
            println("SW_golden_result" + " : " + a + " + " + b + " = " + expected)
            // println("a= " + FloatUtils.floatToBigInt(a))
            // println("b= " + FloatUtils.floatToBigInt(b))
            // println("r= " + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            // PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
            var result = java.lang.Float.intBitsToFloat(PE.io.fpOut.peek().litValue().toInt)
            println(f"FPAdd result[Chisel] = $result\n")
          }
        }
      }
  }
}
class FPAdd_total_except_special extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_total_except_special" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- -38 until 45) {
          for (j <- -38 until 45) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            var b = (scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            val expected = a + b
            println("n-n" + ":" + a + "-" + b + "=" + expected)
            println("a=" + FloatUtils.floatToBigInt(a))
            println("b=" + FloatUtils.floatToBigInt(b))
            println("r =" + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
          }
        }
      }
  }
}
class FPAdd_special_value extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_special_value" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        for (i <- List(-50, -30, -10, 0, 10, 30, 50)) {
          for (j <- List(-50, -30, -10, 0, 10, 30, 50)) {
            var a = (scala.util.Random.nextFloat() / Math.pow(10, i).floatValue())
            var b = (scala.util.Random.nextFloat() / Math.pow(10, j).floatValue())
            val expected = a + b
            println("n-n" + ":" + a + "-" + b + "=" + expected)
            println("a=" + FloatUtils.floatToBigInt(a))
            println("b=" + FloatUtils.floatToBigInt(b))
            println("r =" + FloatUtils.floatToBigInt(a + b))
            PE.io.in1.poke(FloatUtils.floatToBigInt(a))
            PE.io.in2.poke(FloatUtils.floatToBigInt(b))
            PE.io.test.expect(0)
            PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))
          }
        }
      }
  }
}

class FPAdd_individual_test extends AnyFlatSpec with
  ChiselScalatestTester{
  "FPAdd_indiv_test" should "pass" in {
    test(new FP_Add(8,23))
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>

        var a = 1.7656893E-38.floatValue()
        var b = 9.717776E-39.floatValue()
        val expected = a + b
        println("n-n" + ":" + a + "-" + b + "=" + expected)
        println("a=" + FloatUtils.floatToBigInt(a))
        println("b=" + FloatUtils.floatToBigInt(b))
        println("r =" + FloatUtils.floatToBigInt(a + b))
        PE.io.in1.poke(FloatUtils.floatToBigInt(a))
        PE.io.in2.poke(FloatUtils.floatToBigInt(b))
        PE.io.test.expect(0)
        PE.io.fpOut.expect(FloatUtils.floatToBigInt(expected))

      }
  }
}
