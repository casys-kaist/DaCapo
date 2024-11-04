package PE.FPMAC

//import Chisel._
import chisel3._


object from_fp_to_FPBit
{
  def apply(expWidth: Int, manWidth: Int, in: Bits): FPBit_bundle =
  {
    //sign, exp, mantissa bit
    val sign = in(expWidth + manWidth)
    val expIn = in(expWidth + manWidth - 1, manWidth)
    val mantissaIn = in(manWidth - 1, 0)
    //Zero Exp , Zero Mantissa
    val isZeroExpIn = (expIn === 0.U)
    val isZeromantissaIn = (mantissaIn === 0.U)

    // Special value: Zero
    val isZero = isZeroExpIn && isZeromantissaIn
    // Special value: Infinite
    val isInf = expIn.andR && isZeromantissaIn   // or (expIn === UInt(2<<expWidth - 1))
    // Special value: Nan
    val isNan = expIn.andR && !isZeromantissaIn
    // normalized? or not
    val isdenormal = isZeroExpIn && !isZeromantissaIn
    val isnormal = !isZeroExpIn && !expIn.andR
    val out = Wire(new FPBit_bundle(expWidth = expWidth, manWidth = manWidth))
    out.isNaN  := isNan
    out.isInf  := isInf
    out.isZero := isZero
    out.isdenormal := isdenormal
    out.isnormal := isnormal
    out.sign   := sign
    out.Exp   := expIn.asSInt
    out.Man := mantissaIn
    out
  }
}

