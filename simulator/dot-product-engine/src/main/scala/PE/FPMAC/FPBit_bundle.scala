package PE.FPMAC

//import chisel3._
import Chisel._

class FPBit_bundle(val expWidth: Int, val manWidth: Int) extends Bundle {
  // special value
  val isNaN = Bool()
  val isInf = Bool()
  val isZero= Bool()
  // normalized or denormalized
  val isdenormal = Bool()
  val isnormal = Bool()
  // contents
  val sign  = Bool()
  val Exp = SInt(width = expWidth)
  val Man = UInt(width = manWidth) // mantissa bit + sign bit (1 bit)

  def isSigNan(): Bool = { //Nan에는 sigNaN과 QuietNaN 두종류가 있다.
    //sigNaN은 0xx...x1(23)이고 잘못된 연산에서 예외를 발생시킨다.
    //Quiet Nan은 1xx...xx(23)이다. 잘못된 연산에서 걍 Nan을 발생시키고 계산 지속
    isNaN && !Man(manWidth -1)
  }
}


