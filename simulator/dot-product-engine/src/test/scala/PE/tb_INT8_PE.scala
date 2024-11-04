
import PE._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random
class tb_INT8_PE extends AnyFlatSpec with ChiselScalatestTester {
    "INT_PE" should "pass" in {
        test(new INT8_PE).withAnnotations(Seq(WriteVcdAnnotation))  {PE =>
            val random = new Random
            var acc =0
            println("INT8_PE is PE for output stationary")
            println("If op_sig == 0, drain accumulated output. if op_sig == 1, accumulate op1 * op2 with pre-accumulated result")
            println("you can change mode in tb_INT8_PE.scala")
            for(i <- 0 until 10){
                val op1 = random.nextInt((1<<5) -1)
                val op2 = random.nextInt((1<<5) -1)
                val psum = random.nextInt((1<<5) -1)
                
                PE.io.op1.poke(op1)
                PE.io.op2.poke(op2)
                PE.io.drain_result.poke(psum)
                PE.io.op_sig.poke(1) // op_sig == 0 or 1
                
                acc += (op1*op2) // if op_sig == 1
                // acc = psum // if op_sig == 0
                
                PE.clock.step()
                println("golden result = " + acc)
                println("result from PE = " + PE.io.out.peek()+"\n")
            }
        }
    }
}
