package PE.AS_FALL_DPE

import PE.Components.FloatUtils
import scala.util.Random
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.util.log2Floor

class tb_mirror_DPE_golden_groupsize_16 extends AnyFlatSpec with ChiselScalatestTester {
    "tb_mirror_DPE_golden" should "pass" in {
        test(new OS_only_mirror_DPE).withAnnotations(Seq(WriteVcdAnnotation)) {M_DPE =>
            for (iter <- 0 until 5){
                var golden_fp = 0.0
                var golden_fp_result = 0.0
                /* {is_eight_bit_mul, is_four_bit_mul} */
                var mx_signal = 0
                println("TEST start with random vlaue -5 ~ 5")
                for (cycle <- 0 until 1){
                    println("===== cycle ======" + cycle)
                    /* 1. prepare FP values */
                    var op1_fp = new Array[Float](16)
                    var op1_sign_bundle = new Array[Int](16)
                    var op1_exp_bundle = new Array[Int](16)
                    var op1_man_bundle = new Array[Int](16)
                    
                    for(i <- 0 until 16){
                        op1_fp(i) = ((Random.nextFloat()-0.5)).floatValue()
                        var op1_read_num = FloatUtils.floatToBigInt(op1_fp(i))
                        op1_sign_bundle(i) = ((op1_read_num>>31)&0x1).toInt
                        op1_exp_bundle(i) = ((op1_read_num>>23)&0xff).toInt
                        op1_man_bundle(i) = (1<<23) +(op1_read_num&0x007fffff).toInt
                    }
                    var op2_fp = new Array[Float](16)
                    var op2_sign_bundle = new Array[Int](16)
                    var op2_exp_bundle = new Array[Int](16)
                    var op2_man_bundle = new Array[Int](16)
                    for(i <- 0 until 16){
                        op2_fp(i) = ((Random.nextFloat()-0.5)).floatValue()
                        var op2_read_num = FloatUtils.floatToBigInt(op2_fp(i))
                        op2_sign_bundle(i) = ((op2_read_num>>31)&0x1).toInt
                        op2_exp_bundle(i) = ((op2_read_num>>23)&0xff).toInt
                        op2_man_bundle(i) = (1<<23) + (op2_read_num&0x007fffff).toInt
                    }
                    golden_fp_result += (op1_fp zip op2_fp).map { case (a, b) => a * b }.sum
                    println("golden fp result = " + golden_fp_result)
                    /* 2. SW golden result */
                    var psum = 0.0
                    val op1_maxVal = op1_exp_bundle.max
                    val op2_maxVal = op2_exp_bundle.max
                    val op1_differences = op1_exp_bundle.map(element => op1_maxVal - element)
                    val op2_differences = op2_exp_bundle.map(element => op2_maxVal - element)
                    var op1_mx_me = new Array[Int](8)
                    var op2_mx_me = new Array[Int](8)
                    var op1_mx_man = new Array[Int](16)
                    var op2_mx_man = new Array[Int](16)
                    var op1_mx_sman = new Array[Int](16)
                    var op2_mx_sman = new Array[Int](16)

                    for(i<- 0 until 16){
                        op1_mx_me(i/2) =if(op1_differences((i/2)*2) >= 1 && op1_differences((i/2)*2+1) >= 1 ) 1 else 0
                        op2_mx_me(i/2) =if(op2_differences((i/2)*2) >= 1 && op2_differences((i/2)*2+1) >= 1 ) 1 else 0
                        if(op1_differences(i).toInt > 3){
                            op1_mx_man(i) = 0
                        }else{
                            op1_mx_man(i) = ((op1_man_bundle(i)) >> (22 - op1_mx_me(i/2) + op1_differences(i).toInt)) 
                        }
                        if(op2_differences(i).toInt > 3){
                            op2_mx_man(i) = 0
                        }else{
                            op2_mx_man(i) = ((op2_man_bundle(i)) >> (22 - op2_mx_me(i/2) + op2_differences(i).toInt))
                        }
                        
                        op1_mx_sman(i) = ((op1_man_bundle(i)>>(22 - op1_mx_me(i/2) + op1_differences(i).toInt))<<(1-op1_mx_me(i/2))) * math.pow(-1, op1_sign_bundle(i)).toInt
                        op2_mx_sman(i) = ((op2_man_bundle(i)>>(22 - op2_mx_me(i/2) + op2_differences(i).toInt))<<(1-op2_mx_me(i/2))) * math.pow(-1, op2_sign_bundle(i)).toInt
                    }
                    var golden_man = (op1_mx_sman zip op2_mx_sman).map { case (a, b) => a * b }.sum
                    
                    var golden_sign = 0
                    if(golden_man < 0){
                        golden_sign = 1
                        golden_man = - golden_man
                    }
                    val golden_shift = (log2Floor(golden_man) - 4)
                    val golden_exp = op1_maxVal + op2_maxVal - ((1<<7)-1) + golden_shift
                    val golden_man_ = golden_man << (19 - golden_shift)
                    println(golden_man_)
                    golden_fp += java.lang.Float.intBitsToFloat((golden_sign<<31 | golden_exp << 23 | (golden_man_ & 0x007fffff).toInt))
                    println("golden MX result = " + golden_fp)
                    /* 3. HW input */
                    M_DPE.io.reset.poke(0.U)
                    /* {is_eight_bit_mul, is_four_bit_mul} */
                    M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                    /* 1 == up, 0 == down */
                    M_DPE.io.flow_direction.poke(0.U) 
                    /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                    M_DPE.io.operation_mode.poke(2.U)
                    M_DPE.io.in_act_bundle.is_data.poke(1.U)

                    for( i <- 0 until 16 ){
                        M_DPE.io.in_act_bundle.op_man(i).poke(op1_mx_man(i))
                        M_DPE.io.in_N_wgt_bundle.op_man(i).poke(op2_mx_man(i))
                        M_DPE.io.in_S_wgt_bundle.op_man(i).poke(op2_mx_man(i)) 
                        M_DPE.io.in_act_bundle.op_sign(i).poke(op1_sign_bundle(i))
                        M_DPE.io.in_N_wgt_bundle.op_sign(i).poke(op2_sign_bundle(i))
                        M_DPE.io.in_S_wgt_bundle.op_sign(i).poke(op2_sign_bundle(i))
                        M_DPE.io.in_act_bundle.m_exp(i).poke(op1_mx_me(i/2))
                        M_DPE.io.in_N_wgt_bundle.m_exp(i).poke(op2_mx_me(i/2))
                        M_DPE.io.in_S_wgt_bundle.m_exp(i).poke(op2_mx_me(i/2))
                    }
                    M_DPE.io.in_act_bundle.op_exp.poke(op1_maxVal)       
                    M_DPE.io.in_N_wgt_bundle.op_exp.poke(op2_maxVal)    
                    M_DPE.io.in_S_wgt_bundle.op_exp.poke(op2_maxVal)   
                    

                    M_DPE.io.in_N_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue())) /* In case of 'flow_direction == 0' */
                    M_DPE.io.in_S_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue()))
                    M_DPE.clock.step(1)
                    
                    println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                    println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                   
                }
                M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                M_DPE.io.operation_mode.poke(3.U)
                M_DPE.clock.step(5)
                M_DPE.io.reset.poke(1.U)
                M_DPE.clock.step(1)
            }  
        }
    }
  
}

class tb_mirror_DPE_golden_groupsize_4 extends AnyFlatSpec with ChiselScalatestTester {
    "tb_mirror_DPE_golden" should "pass" in {
        test(new OS_only_mirror_DPE).withAnnotations(Seq(WriteVcdAnnotation)) {M_DPE =>
            for (iter <- 0 until 5){
                var golden_fp = 0.0
                var golden_fp_result = 0.0
                /* {is_eight_bit_mul, is_four_bit_mul} */
                var mx_signal = 1
                println("TEST start with random vlaue -5 ~ 5")
                for (cycle <- 0 until 1){
                    println("===== cycle ======" + cycle)
                    /* 1. prepare FP values */
                    var op1_fp = new Array[Float](4)
                    var op1_sign_bundle = new Array[Int](4)
                    var op1_exp_bundle = new Array[Int](4)
                    var op1_man_bundle = new Array[Int](4)
                    
                    for(i <- 0 until 4){
                        op1_fp(i) = ((Random.nextFloat()-0.5)).floatValue()
                        var op1_read_num = FloatUtils.floatToBigInt(op1_fp(i))
                        op1_exp_bundle(i)  = ((op1_read_num>>23)&0xff).toInt
                        op1_man_bundle(i) = (1<<23) + (op1_read_num&0x007fffff).toInt
                        op1_sign_bundle(i) = ((op1_read_num>>31)&0x1).toInt
                    }
                    var op2_fp = new Array[Float](4)
                    var op2_sign_bundle = new Array[Int](4)
                    var op2_exp_bundle = new Array[Int](4)
                    var op2_man_bundle = new Array[Int](4)
                    for(i <- 0 until 4){
                        op2_fp(i) = ((Random.nextFloat()-0.5)).floatValue()
                        var op2_read_num = FloatUtils.floatToBigInt(op2_fp(i))
                        op2_exp_bundle(i) = ((op2_read_num>>23)&0xff).toInt
                        op2_man_bundle(i) = (1<<23) + (op2_read_num&0x007fffff).toInt
                        op2_sign_bundle(i) = ((op2_read_num>>31)&0x1).toInt
                    }

                    golden_fp_result += (op1_fp zip op2_fp).map { case (a, b) => a * b }.sum
                    println("golden fp result = " + golden_fp_result)
                    /* 2. SW golden result */
                    var psum = 0.0
                    val op1_maxVal = op1_exp_bundle.max
                    val op2_maxVal = op2_exp_bundle.max
                    val op1_differences = op1_exp_bundle.map(element => op1_maxVal - element)
                    val op2_differences = op2_exp_bundle.map(element => op2_maxVal - element)
                    var op1_mx_me = new Array[Int](2)
                    var op2_mx_me = new Array[Int](2)
                    var op1_mx_man = new Array[Int](16)
                    var op2_mx_man = new Array[Int](16)
                    var op1_mx_sman = new Array[Int](4)
                    var op2_mx_sman = new Array[Int](4)

                    for(i<- 0 until 4){
                        op1_mx_me(i/2) =if(op1_differences((i/2)*2) >= 1 && op1_differences((i/2)*2+1) >= 1 ) 1 else 0
                        op2_mx_me(i/2) =if(op2_differences((i/2)*2) >= 1 && op2_differences((i/2)*2+1) >= 1 ) 1 else 0
                    }
                    for(i<- 0 until 16){
                        if(op1_differences(i/4).toInt > 3){
                            op1_mx_man(i) = 0
                        }else{
                            op1_mx_man(i) = ((op1_man_bundle(i/4)) >> (22 - 2*(i%2) - op1_mx_me(i/8) + op1_differences(i/4).toInt))&0x3 
                        }
                        if(op2_differences(i/4).toInt > 3){
                            op2_mx_man(i) = 0
                        }else{
                            op2_mx_man(i) = ((op2_man_bundle(i/4)) >> (22 - 2*((i/2)%2) - op2_mx_me(i/8) + op2_differences(i/4).toInt))&0x3
                        }
                    }
                    for (i<- 0 until 4){
                        op1_mx_sman(i) = ((op1_man_bundle(i)>>(20 - op1_mx_me(i/2) + op1_differences(i).toInt))<<(1-op1_mx_me(i/2))) * math.pow(-1, op1_sign_bundle(i)).toInt
                        op2_mx_sman(i) = ((op2_man_bundle(i)>>(20 - op2_mx_me(i/2) + op2_differences(i).toInt))<<(1-op2_mx_me(i/2))) * math.pow(-1, op2_sign_bundle(i)).toInt
                    }
                    var golden_man = (op1_mx_sman zip op2_mx_sman).map { case (a, b) => a * b }.sum
                    
                    var golden_sign = 0
                    if(golden_man < 0){
                        golden_sign = 1
                        golden_man = - golden_man
                    }
                    val golden_shift = (log2Floor(golden_man) - 8)
                    val golden_exp = op1_maxVal + op2_maxVal - ((1<<7)-1) + golden_shift
                    val golden_man_ = golden_man << (15 - golden_shift)
                    println(golden_man_)
                    golden_fp += java.lang.Float.intBitsToFloat((golden_sign<<31 | golden_exp << 23 | (golden_man_ & 0x007fffff).toInt))
                    println("golden MX result = " + golden_fp)
                    /* 3. HW input */
                    M_DPE.io.reset.poke(0.U)
                    /* {is_eight_bit_mul, is_four_bit_mul} */
                    M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                    /* 1 == up, 0 == down */
                    M_DPE.io.flow_direction.poke(0.U) 
                    /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                    M_DPE.io.operation_mode.poke(2.U)
                    M_DPE.io.in_act_bundle.is_data.poke(1.U)

                    for( i <- 0 until 16 ){
                        M_DPE.io.in_act_bundle.op_man(i).poke(op1_mx_man(i))
                        M_DPE.io.in_N_wgt_bundle.op_man(i).poke(op2_mx_man(i))
                        M_DPE.io.in_S_wgt_bundle.op_man(i).poke(op2_mx_man(i)) 
                        M_DPE.io.in_act_bundle.op_sign(i).poke(op1_sign_bundle(i/4))
                        M_DPE.io.in_N_wgt_bundle.op_sign(i).poke(op2_sign_bundle(i/4))
                        M_DPE.io.in_S_wgt_bundle.op_sign(i).poke(op2_sign_bundle(i/4))
                        M_DPE.io.in_act_bundle.m_exp(i).poke(op1_mx_me(i/8))
                        M_DPE.io.in_N_wgt_bundle.m_exp(i).poke(op2_mx_me(i/8))
                        M_DPE.io.in_S_wgt_bundle.m_exp(i).poke(op2_mx_me(i/8))
                    }
                    M_DPE.io.in_act_bundle.op_exp.poke(op1_maxVal)    
                    M_DPE.io.in_N_wgt_bundle.op_exp.poke(op2_maxVal)    
                    M_DPE.io.in_S_wgt_bundle.op_exp.poke(op2_maxVal)    
                    
                    M_DPE.io.in_N_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue())) /* In case of 'flow_direction == 0' */
                    M_DPE.io.in_S_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue()))
                    M_DPE.clock.step(1)
                    
                    println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                    println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                   
                }
                M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                M_DPE.io.operation_mode.poke(3.U)
                M_DPE.clock.step(5)
                M_DPE.io.reset.poke(1.U)
                M_DPE.clock.step(1)
            }  
        }
    }
  
}


class tb_mirror_DPE_golden_groupsize_1 extends AnyFlatSpec with ChiselScalatestTester {
    "tb_mirror_DPE_golden" should "pass" in {
        test(new OS_only_mirror_DPE).withAnnotations(Seq(WriteVcdAnnotation)) {M_DPE =>
            for (iter <- 0 until 5){
                var golden_fp = 0.0
                /* {is_eight_bit_mul, is_four_bit_mul} */
                var mx_signal = 3//arr(Random.nextInt(arr.length))
                var golden_fp_result = 0.0
                for (cycle <- 0 until 1){
                    println("===== cycle ======" + cycle)
                    /* 1. prepare FP values */
                    var op1_fp = (Random.nextFloat()-0.5).floatValue()
                    var op1_read_num = FloatUtils.floatToBigInt(op1_fp)
                    var op1_sign_bundle = (op1_read_num>>31).toInt
                    var op1_exp_bundle = ((op1_read_num>>23)&0xff).toInt
                    var op1_man_bundle = (1<<23) +(op1_read_num&0x007fffff).toInt
                    

                    var op2_fp = (Random.nextFloat()-0.5).floatValue()
                    var op2_read_num = FloatUtils.floatToBigInt(op2_fp)
                    var op2_sign_bundle = (op2_read_num>>31).toInt
                    var op2_exp_bundle = ((op2_read_num>>23)&0xff).toInt
                    var op2_man_bundle = (1<<23) + (op2_read_num&0x007fffff).toInt

                    golden_fp_result = golden_fp_result + op1_fp * op2_fp
                    println("golden fp result = " + golden_fp_result)
                    /* 2. SW golden result */
                    var psum = 0.0
                    val op1_maxVal = op1_exp_bundle
                    val op2_maxVal = op2_exp_bundle
                    var op1_mx_me = new Array[Int](8)
                    var op2_mx_me = new Array[Int](8)
                    var op1_mx_man = new Array[Int](16)
                    var op2_mx_man = new Array[Int](16)

                    for(i<- 0 until 16){
                        op1_mx_me(i/2) = 0
                        op2_mx_me(i/2) = 0
                        op1_mx_man(i) = ((op1_man_bundle ) >> (22 - 2*((i/2)%4))) & 0x3
                        op2_mx_man(i) = ((op2_man_bundle ) >> (22 - 2*(2*(i/8)+i%2))) & 0x3
                    }
                    var golden_man = ((op1_man_bundle) >> (16)) *((op2_man_bundle) >> 16) * math.pow(-1, op1_sign_bundle).toInt * math.pow(-1, op2_sign_bundle).toInt
                    var golden_sign = 0
                    if(golden_man < 0){
                        golden_sign = 1
                        golden_man = - golden_man
                    }
                    val golden_shift = (log2Floor(golden_man) - 14)
                    val golden_exp = op1_maxVal + op2_maxVal - ((1<<7)-1) + golden_shift
                    val golden_man_ = golden_man << (9 - golden_shift)
                    golden_fp += java.lang.Float.intBitsToFloat((golden_sign<<31 | golden_exp << 23 | (golden_man_ & 0x007fffff).toInt))
                    println("golden MX result = " + golden_fp)
                    /* 3. HW input */
                    M_DPE.io.reset.poke(0.U)
                    /* {is_eight_bit_mul, is_four_bit_mul} */
                    M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                    /* 1 == up, 0 == down */
                    M_DPE.io.flow_direction.poke(0.U) 
                    /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                    M_DPE.io.operation_mode.poke(2.U)
                    M_DPE.io.in_act_bundle.is_data.poke(1.U)

                    for( i <- 0 until 16 ){
                        M_DPE.io.in_act_bundle.op_man(i).poke(op1_mx_man(i))
                        M_DPE.io.in_N_wgt_bundle.op_man(i).poke(op2_mx_man(i))
                        M_DPE.io.in_S_wgt_bundle.op_man(i).poke(op2_mx_man(i)) 
                        M_DPE.io.in_act_bundle.op_sign(i).poke(op1_sign_bundle)
                        M_DPE.io.in_N_wgt_bundle.op_sign(i).poke(op2_sign_bundle)
                        M_DPE.io.in_S_wgt_bundle.op_sign(i).poke(op2_sign_bundle)
                        M_DPE.io.in_act_bundle.m_exp(i).poke(op1_mx_me(i/2))
                        M_DPE.io.in_N_wgt_bundle.m_exp(i).poke(op2_mx_me(i/2))
                        M_DPE.io.in_S_wgt_bundle.m_exp(i).poke(op2_mx_me(i/2))
                    }
                    M_DPE.io.in_act_bundle.op_exp.poke(op1_maxVal)  
                    M_DPE.io.in_N_wgt_bundle.op_exp.poke(op2_maxVal) 
                    M_DPE.io.in_S_wgt_bundle.op_exp.poke(op2_maxVal) 
                    
                    M_DPE.io.in_N_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue())) /* In case of 'flow_direction == 0' */
                    M_DPE.io.in_S_psum.poke(FloatUtils.floatToBigInt((0.0).floatValue()))
                    M_DPE.clock.step(1)
                    
                    println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                    println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                   
                }
                M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                M_DPE.io.operation_mode.poke(3.U)
                println("===== iteration ======" + iter)
                println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                println("\n\n\n\n")
                M_DPE.clock.step(5)
                M_DPE.io.reset.poke(1.U)
                M_DPE.clock.step(1)
            }  
        }
    }
  
}
class tb_mirror_DPE_for_VCD extends AnyFlatSpec with ChiselScalatestTester {
    "mirror_DPE_for_VCD" should "pass" in {
        test(new OS_only_mirror_DPE).withAnnotations(Seq(WriteVcdAnnotation)) {M_DPE =>
            for (iter <- 0 until 1){
                val arr = Array(1,3)
                var mx_signal = arr(Random.nextInt(arr.length))
                for (cycle <- 0 until 5){
                    M_DPE.io.reset.poke(0.U)
                    /* {is_eight_bit_mul, is_four_bit_mul} */
                    M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                    /* 1 == up, 0 == down */
                    M_DPE.io.flow_direction.poke(0.U) 
                    /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                    M_DPE.io.operation_mode.poke(2.U)
                    M_DPE.io.in_act_bundle.is_data.poke(1.U)

                    for( i <- 0 until 16 ){
                        M_DPE.io.in_act_bundle.op_man(i).poke(Random.nextInt(4))
                        M_DPE.io.in_N_wgt_bundle.op_man(i).poke(Random.nextInt(4))
                        M_DPE.io.in_S_wgt_bundle.op_man(i).poke(Random.nextInt(4)) 
                        M_DPE.io.in_act_bundle.op_sign(i).poke(Random.nextInt(2))
                        M_DPE.io.in_N_wgt_bundle.op_sign(i).poke(Random.nextInt(2))
                        M_DPE.io.in_S_wgt_bundle.op_sign(i).poke(Random.nextInt(2))
                        M_DPE.io.in_act_bundle.m_exp(i).poke(Random.nextInt(2))
                        M_DPE.io.in_N_wgt_bundle.m_exp(i).poke(Random.nextInt(2))
                        M_DPE.io.in_S_wgt_bundle.m_exp(i).poke(Random.nextInt(2))
                    }
                    M_DPE.io.in_act_bundle.op_exp.poke(Random.between(122,132))   
                    M_DPE.io.in_N_wgt_bundle.op_exp.poke(Random.between(122,132)) 
                    M_DPE.io.in_S_wgt_bundle.op_exp.poke(Random.between(122,132))   
                    

                    M_DPE.io.in_N_psum.poke(FloatUtils.floatToBigInt((Random.between(-100,100)).floatValue())) /* In case of 'flow_direction == 0' */
                    M_DPE.io.in_S_psum.poke(FloatUtils.floatToBigInt((Random.between(-100,100)).floatValue()))
                    println("cycle ======" + cycle)
                    println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                    println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                    M_DPE.clock.step(1)
                }
                M_DPE.io.in_act_bundle.bit_width_sig.poke(mx_signal)
                /* mode 0:w_fill 1:w_flow 2:os_flow 3:os_drain */
                M_DPE.io.operation_mode.poke(3.U)
                println("iteration ======" + iter)
                println(" result_N1 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_N_psum.peek().litValue().toInt))
                println(" result_S0 " + java.lang.Float.intBitsToFloat(M_DPE.io.out_S_psum.peek().litValue().toInt))
                M_DPE.clock.step(5)
                M_DPE.io.reset.poke(1.U)
                M_DPE.clock.step(1)
            }  
        }
    }
  
}
object tb_mirror_DPE_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new mirror_DPE)
}