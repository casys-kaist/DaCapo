package PE.AS_FALL_DPE
import chisel3._
import chisel3.stage.ChiselStage
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random
import PE.Components.FloatUtils

import scala.math._

class tb_MX_Converter_for_VCD extends AnyFlatSpec with ChiselScalatestTester{
    "ISCA_MX_CONV" should "pass" in {
        test(new MX_Converter_for_DPE).withAnnotations(Seq(WriteVcdAnnotation)){M =>
        for(cycle <- 0 until 1){
            var sign_bundle = new Array[BigInt](16)
            var exp_bundle = new Array[BigInt](16)
            var man_bundle = new Array[BigInt](16)
            for(i <- 0 until 16){
                var read_num = FloatUtils.floatToBigInt((Random.nextFloat()*1000).floatValue())
                M.io.fps(i).poke(read_num)
                sign_bundle(i) = (read_num>>31)
                exp_bundle(i) = (read_num>>23)
                man_bundle(i) = (1<<23) +(read_num&0x007fffff)
                // println(fp)
            }
            /* Software golden result */
            val maxVal = exp_bundle.max
            val differences = exp_bundle.map(element => maxVal - element)
            
            println("Compare expected[scala] values and simulated[chisel] values\n")
            for(i<- 0 until 16){
                var me =if(differences((i/2)*2) >= 1 && differences((i/2)*2+1) >= 1 ) 1 else 0
                // -6 : Mantissa bit width of MX9 is 7
                println ("DEBUG exp :" + exp_bundle(i))
                println( i + ": MAN  [chisel] " + M.io.shifted_mans(i).peek().toString + "[scala]" + (man_bundle(i)>>(23 - 6 - me + differences(i).toInt)))
                println( i + ": ME [chisel]" + M.io.scale_factor(i/2).peek() + "[scala]" + me)
                println()
            }
            println("EXP [chisel] " + M.io.max_exp.peek().toString + "[scala]" + maxVal)
            M.clock.step(1)
        }
    }
}}