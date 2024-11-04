package PE.Component

import PE.Components._
import chisel3._
import chisel3.stage.ChiselStage
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Tag

import scala.math._


class BFP_TEST_DPE extends AnyFlatSpec with
  ChiselScalatestTester{
  "Sep_exp_and_man" should "pass" in {
    test(new BFP_Converter_DPE)
      .withAnnotations (Seq( WriteVcdAnnotation )) { PE =>
        /* 
          This convert sets of floating point values to BFP for 1 iteration.
          You can change 1 to the number you want.
        */
        for(_ <- 0 until 1){
          val rand = new scala.util.Random
          val seed = (rand.between(1,(1<<8)-1) +1<<8) / Math.pow(2, 8).floatValue()
          var rand_num: BigInt = 0
          var exp_bundle = new Array[BigInt](16)
          var man_bundle = new Array[BigInt](16)
          for(i <- 0 until 16){
            rand_num = FloatUtils.floatToBigInt(seed * Math.pow(2, rand.between(-4,4)).floatValue())
            PE.io.fps(i).poke(rand_num)
            exp_bundle(i) = (rand_num>>23)
            man_bundle(i) = (1<<23) +(rand_num&0x007fffff)
            println("Get exponent and mantissa for each element of a groups of floating point values.\n")
            println("EXP: " + i + ": " + (rand_num>>23).toString)
            println("MAN: " + i + ": " + ((1<<23) + (rand_num & 0x007fffff)).toString)
            println()
          }
          /*-----------------------------------------------------------*/
          val maxVal = exp_bundle.max
          val differences = exp_bundle.map(element => maxVal - element)
          println("Compare expected[scala] values and simulated[chisel] values\n")
          for(i<- 0 until 16){
            println( i + ": MAN  [chisel] " + PE.io.BFP_mantissas(i).peek ().toString + "[scala]" + (man_bundle(i)>>(23 - 7 + differences(i).toInt)))
            println()
          }
          println("EXP [chisel] " + PE.io.BFP_exponent.peek().toString + "[scala]" + maxVal)
          }
          PE.clock.step()
      }
  }
}

