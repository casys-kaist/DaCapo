package PE.AS_FALL_DPE

import PE.Components.FloatUtils
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import PE.AS_FALL_DPE.DPE_Config._
import PE.AS_FALL_DPE._

class tb_mirror_DPE_array extends AnyFlatSpec with ChiselScalatestTester {
    "array_test" should "pass" in {
        test(new mirror_DPE_array) { Arr =>
            /*
                === Goal of this test ===
                We already check the functionality of mirror_DPE with various values
                Now we check array's data flow (3x3)
                1. Fill Weight properly, with "life_count"
                2. WS_FLOW (row 1 | row 2 3) value check
                3. reset
                4. OS (2 cycle)
                [3 2 1]     [3 3]
                [3 2 1]     [2 2]
                            [1 1]
                5. drain 
                [ 14 14 ]
                [ 14 14 ] 
            */
            Arr.io.reset.poke(0.U); 
            Arr.io.flow_direction(0).poke(1.U)
            Arr.io.flow_direction(1).poke(1.U)
            Arr.io.flow_direction(2).poke(1.U)
            Arr.io.flow_direction(3).poke(0.U)
            Arr.io.flow_direction(4).poke(0.U)


            // /* ==0== FILL WIGHT */
            // Arr.io.operation_mode.poke(0.U)
            // /*  [1 1 1]    [1 2 3] up
            //     [1 1 1] => [4 5 6] down
            //     [1 1 1]    [7 8 9] down*/
            // for (irt <- 0 until 3){
            //     for(i<-0 until dim){
            //         for(d <- 0 until 16){
            //             Arr.io.in_act_bundle_set(i).op_man(d).poke(0.U)
            //             Arr.io.in_act_bundle_set(i).op_sign(d).poke(0.U)
            //         }
            //         Arr.io.in_act_bundle_set(i).op_exp.poke(0)
            //         Arr.io.in_act_bundle_set(i).bit_width_sig.poke(0)
            //         Arr.io.in_act_bundle_set(i).is_data.poke(0.U)
            //     }
            //     for(i<-0 until dim){ /* upper 3 lines */
            //         for(d <- 0 until 16){
            //             Arr.io.in_N_wgt_bundle_set(i).op_man(d).poke((irt+1).U)
            //             Arr.io.in_N_wgt_bundle_set(i).op_sign(d).poke(0.U)   
            //         }
            //         Arr.io.in_N_wgt_bundle_set(i).op_exp.poke((127).U)
            //         Arr.io.in_N_wgt_bundle_set(i).life_count.poke((2-irt).S)
            //     }
            //     for(i<-0 until dim){ /* lower 2 lines */
            //         for(d <- 0 until 16){
            //             Arr.io.in_S_wgt_bundle_set(i).op_man(d).poke((irt+1).U)
            //             Arr.io.in_S_wgt_bundle_set(i).op_sign(d).poke(1)   
            //         }
            //         Arr.io.in_S_wgt_bundle_set(i).op_exp.poke((127).U)
            //         Arr.io.in_S_wgt_bundle_set(i).life_count.poke((1-irt).S)
            //     }
            //     Arr.clock.step(1)
            //     println("CYCLE")
            // }
            // //2. WS_FLOW (row 1 | row 2 3) value check
            // val ws_length:Int = 4
            // var bound:Array[Int] = Array(2,1,0,0,1)
            // val value_arr:Array[Int] = Array(1,2,3)
            // Arr.io.operation_mode.poke(1.U)
            // for (irt <- 0 until (ws_length + dim*2)){ 
            //     for(i<-0 until dim){
            //         if(irt >= bound(i) && irt < (bound(i)+ws_length)){
            //             for(d <- 0 until 16){
            //                 Arr.io.in_act_bundle_set(i).op_man(d).poke(value_arr((irt-bound(i))%3).U)
            //                 Arr.io.in_act_bundle_set(i).op_sign(d).poke(0.U)
            //             }
            //             Arr.io.in_act_bundle_set(i).op_exp.poke(127)
            //             Arr.io.in_act_bundle_set(i).is_data.poke(1.U)
            //         }else{
            //             for(d <- 0 until 16){
            //                 Arr.io.in_act_bundle_set(i).op_man(d).poke(0.U)
            //                 Arr.io.in_act_bundle_set(i).op_sign(d).poke(0.U)
            //             }
            //             Arr.io.in_act_bundle_set(i).op_exp.poke(0)
            //             Arr.io.in_act_bundle_set(i).is_data.poke(0.U)
            //         }
            //         Arr.io.in_act_bundle_set(i).bit_width_sig.poke(0)
                    
            //     }


            //     for(i<-0 until dim){
            //         for(d <- 0 until 16){
            //             Arr.io.in_N_wgt_bundle_set(i).op_man(d).poke(0.U)
            //             Arr.io.in_N_wgt_bundle_set(i).op_sign(d).poke(0.U)  
            //             Arr.io.in_S_wgt_bundle_set(i).op_man(d).poke(0.U)
            //             Arr.io.in_S_wgt_bundle_set(i).op_sign(d).poke(0.U)  
            //         }
            //         Arr.io.in_N_wgt_bundle_set(i).op_exp.poke(0.U)
            //         Arr.io.in_N_wgt_bundle_set(i).life_count.poke(0.S)
            //         Arr.io.in_S_wgt_bundle_set(i).op_exp.poke(0.U)
            //         Arr.io.in_S_wgt_bundle_set(i).life_count.poke(0.S)
            //     }
            //     Arr.clock.step(1)
            //     for( idx <- 0 until dim){
            //         println("N "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_N_result_set(idx).peek().litValue().toInt))
            //         println("S "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_S_result_set(idx).peek().litValue().toInt)) 
            //     } 
            //     println("=================================")
            // }
            // /*RESET*/
            // Arr.io.reset.poke(1.U)
            // Arr.clock.step(dim+1)
            
            
            /*OS*/
            var bound = Array(0,1,2,1,0)
            val os_length = 4
            Arr.io.reset.poke(0.U)
            Arr.io.operation_mode.poke(2.U)
            for (irt <- 0 until 2*dim+os_length){
                for(i<-0 until dim){
                    if(irt >= bound(i) && irt < os_length + bound(i)){
                        for(d <- 0 until 16){
                            Arr.io.in_act_bundle_set(i).op_man(d).poke(2.U)
                            Arr.io.in_act_bundle_set(i).op_sign(d).poke(0.U)
                        }
                        Arr.io.in_act_bundle_set(i).op_exp.poke(127)
                    }else{
                        for(d <- 0 until 16){
                            Arr.io.in_act_bundle_set(i).op_man(d).poke(0.U)
                            Arr.io.in_act_bundle_set(i).op_sign(d).poke(0.U)   
                        }
                        Arr.io.in_act_bundle_set(i).op_exp.poke(0)
                    }
                    Arr.io.in_act_bundle_set(i).bit_width_sig.poke(0)
                    Arr.io.in_act_bundle_set(i).is_data.poke(1.U)
                }
                for(i<-0 until dim){
                    if(irt >= i && i+os_length > irt){
                        for(d <- 0 until 16){
                            Arr.io.in_N_wgt_bundle_set(i).op_man(d).poke((2).U)
                            Arr.io.in_N_wgt_bundle_set(i).op_sign(d).poke(0.U)   
                        }
                        Arr.io.in_N_wgt_bundle_set(i).op_exp.poke((127).U)
                    }else{
                        for(d <- 0 until 16){
                            Arr.io.in_N_wgt_bundle_set(i).op_man(d).poke(0.U)
                            Arr.io.in_N_wgt_bundle_set(i).op_sign(d).poke(0.U)   
                        }
                        Arr.io.in_N_wgt_bundle_set(i).op_exp.poke(0.U)
                    }
                    Arr.io.in_N_wgt_bundle_set(i).life_count.poke((0).S)
                }
                for(i<-0 until dim){
                    if(irt >= i && i+os_length > irt){
                        for(d <- 0 until 16){
                            Arr.io.in_S_wgt_bundle_set(i).op_man(d).poke((2).U)
                            Arr.io.in_S_wgt_bundle_set(i).op_sign(d).poke(1)   
                        }
                        Arr.io.in_S_wgt_bundle_set(i).op_exp.poke((127).U)
                    }else{
                        for(d <- 0 until 16){
                            Arr.io.in_N_wgt_bundle_set(i).op_man(d).poke(0.U)
                            Arr.io.in_N_wgt_bundle_set(i).op_sign(d).poke(0.U)   
                        }
                        Arr.io.in_S_wgt_bundle_set(i).op_exp.poke(0.U)
                    }
                    Arr.io.in_S_wgt_bundle_set(i).life_count.poke(0.S)
                }
                Arr.clock.step(1)
                for( idx <- 0 until dim){
                    println("N "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_N_result_set(idx).peek().litValue().toInt))
                    println("S "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_S_result_set(idx).peek().litValue().toInt)) 
                }
                println("=================================")
            }
            for( drain_ <- 0 until dim){
                Arr.io.operation_mode.poke(3.U)
                for(idx <- 0 until dim){
                    println("N "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_N_result_set(idx).peek().litValue().toInt))
                    println("S "+idx+"\t"+java.lang.Float.intBitsToFloat(Arr.io.out_S_result_set(idx).peek().litValue().toInt)) 
                }
                println("================================= drain 0")
                Arr.clock.step(1)
            }
        }
    }
  
}
