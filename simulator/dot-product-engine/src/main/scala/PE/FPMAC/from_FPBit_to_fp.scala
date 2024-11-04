package PE.FPMAC

import Chisel._

object from_FPBit_to_fp {
  def apply(fpbit: FPBit_bundle): Bits = Cat(fpbit.sign, fpbit.Exp, fpbit.Man)

}