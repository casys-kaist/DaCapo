package PE.AS_FALL_DPE
import scala.util.Random


object functions {
  val random = new Random
    def four_bit_op1 (x: Array[Int]): Array[Int] = {
        var array: Array[Int] = Array((x(0)&12)>>2, (x(0)&12)>>2, x(0)&3, x(0)&3, (x(1)&12)>>2, (x(1)&12)>>2, x(1)&3, x(1)&3, (x(2)&12)>>2, (x(2)&12)>>2, x(2)&3, x(2)&3, (x(3)&12)>>2, (x(3)&12)>>2, x(3)&3, x(3)&3)
        array
    }
    def four_bit_op2 (x: Array[Int]): Array[Int] = {
        var array: Array[Int] = Array((x(0)&12)>>2, x(0)&3, (x(0)&12)>>2, x(0)&3, (x(1)&12)>>2, x(1)&3, (x(1)&12)>>2, x(1)&3, (x(2)&12)>>2, x(2)&3, (x(2)&12)>>2, x(2)&3, (x(3)&12)>>2, x(3)&3, (x(3)&12)>>2, x(3)&3)
        array
    }
    def eight_bit_op1 (x: Int): Array[Int] = {
        var array: Array[Int] = Array((x&192)>>6, (x&48)>>4, (x&192)>>6, (x&48)>>4, (x&12)>>2, (x&3), (x&12)>>2, (x&3), (x&192)>>6, (x&48)>>4, (x&192)>>6, (x&48)>>4, (x&12)>>2, (x&3), (x&12)>>2, (x&3)) 
        array
    }
    def eight_bit_op2 (x: Int): Array[Int] = {
        var array: Array[Int] = Array((x&192)>>6, (x&192)>>6, (x&48)>>4, (x&48)>>4, (x&192)>>6, (x&192)>>6, (x&48)>>4, (x&48)>>4, (x&12)>>2, (x&12)>>2, (x&3), (x&3), (x&12)>>2, (x&12)>>2, (x&3), (x&3)) 
        array
    }
    def two_bit_fp (): Array[Int] = {
        Array.fill(16)(random.nextInt(1<<2))
    } // 2bit x 16
    def four_bit_fp (): Array[Int] = {
        Array.fill(4)(random.nextInt(1<<4))
    } // 4bit x 4
    def eight_bit_fp (): Int = {
        (random.nextInt(1<<8))
    } // 8bit x 1

    def random_float_2D (row: Int, col: Int) : Array[Array[Float]] = {
        val array: Array[Array[Float]] = Array.ofDim[Float](row, col)
        val random = new Random()
        for(i <- 0 until row){
            for(j <- 0 until col){
                array(i)(j) = random.nextFloat()
            }
        }
        array
    }
    // def generate_100_fp () : Unit = {
    //     val floatingPointValues = (1 to 100).map(_ => random.nextFloat())

    //     val outputFile = "floating_point_values.txt"
    //     val writer = new PrintWriter(new File(outputFile))
    //     floatingPointValues.foreach(value => writer.println(value))
    //     writer.close()
    // }
    // def get_man_sign_1_arr(bit_sig: Int, file_name: String) : (Array[Int], Array[Int]) = {
    //     val inputFile = file_name
    //     val source = scala.io.Source.fromFile(inputFile)
    //     val readValues = source.getLines().map(_.toFloat).toList
    //     print(readValues)
    //     source.close()
    //     if(bit_sig == 0){
    //         FloatUtils.floatToBigInt(readValues(0))
    //     }else if(bit_sig == 1){

    //     }else if(bit_sig == 3){

    //     }
    //     readValues.foreach(println)
    // }
}
