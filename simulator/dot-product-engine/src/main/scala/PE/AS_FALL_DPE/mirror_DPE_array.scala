package PE.AS_FALL_DPE
import chisel3._
import chisel3.util._
import PE.AS_FALL_DPE.DPE_Config._
import scala.collection.mutable.ArrayBuffer

class mirror_DPE_array extends Module {
    val io = IO(new Bundle{
        val in_act_bundle_set = Input(Vec(dim, act_bundle))
        val in_N_wgt_bundle_set = Input(Vec(dim, wgt_bundle))
        val in_S_wgt_bundle_set = Input(Vec(dim, wgt_bundle))
        val out_N_result_set = Output(Vec(dim, UInt(32.W)))
        val out_S_result_set = Output(Vec(dim, UInt(32.W)))
        /*GLOBAL signal*/
        val reset = Input(UInt(1.W))
        val flow_direction = Input(Vec(dim, UInt(1.W))) //1 == up, 0 == down
        val operation_mode = Input(UInt(3.W))
    })

    val m_DPE_array = ArrayBuffer[ArrayBuffer[mirror_DPE]]()
    for( h <- 0 until dim){
        val m_DPE_line = ArrayBuffer[mirror_DPE]()
        for( v <- 0 until dim){
            m_DPE_line += Module(new mirror_DPE)
        }
        m_DPE_array += m_DPE_line
    }

    for( h <- 0 until dim){
        for( v <- 0 until dim){
            if(v == 0){
                m_DPE_array(h)(v).io.in_act_bundle := io.in_act_bundle_set(h)
            } else{
                m_DPE_array(h)(v).io.in_act_bundle := m_DPE_array(h)(v-1).io.out_act_bundle
            }
            if(h == 0){
                m_DPE_array(h)(v).io.in_N_wgt_bundle := io.in_N_wgt_bundle_set(v)
                //out_wgt
                io.out_N_result_set(v) := m_DPE_array(h)(v).io.out_N_psum
                m_DPE_array(h)(v).io.in_N_psum := 0.U

                m_DPE_array(h)(v).io.in_S_wgt_bundle := m_DPE_array(h+1)(v).io.out_N_wgt_bundle
                m_DPE_array(h+1)(v).io.in_N_wgt_bundle := m_DPE_array(h)(v).io.out_S_wgt_bundle
                m_DPE_array(h)(v).io.in_S_psum := m_DPE_array(h+1)(v).io.out_N_psum
                m_DPE_array(h+1)(v).io.in_N_psum := m_DPE_array(h)(v).io.out_S_psum
            }else if(h == (dim-1)){
                m_DPE_array(h)(v).io.in_N_wgt_bundle := m_DPE_array(h-1)(v).io.out_N_wgt_bundle
                m_DPE_array(h-1)(v).io.in_S_wgt_bundle := m_DPE_array(h)(v).io.out_N_wgt_bundle
                m_DPE_array(h)(v).io.in_N_psum := m_DPE_array(h-1)(v).io.out_S_psum
                m_DPE_array(h-1)(v).io.in_S_psum := m_DPE_array(h)(v).io.out_N_psum

                m_DPE_array(h)(v).io.in_S_wgt_bundle := io.in_S_wgt_bundle_set(v)
                //  s out wgt
                m_DPE_array(h)(v).io.in_S_psum := 0.U
                io.out_S_result_set(v) := m_DPE_array(h)(v).io.out_S_psum                
            } else {
                m_DPE_array(h)(v).io.in_N_wgt_bundle := m_DPE_array(h-1)(v).io.out_S_wgt_bundle
                m_DPE_array(h-1)(v).io.in_S_wgt_bundle := m_DPE_array(h)(v).io.out_N_wgt_bundle
                m_DPE_array(h)(v).io.in_N_psum := m_DPE_array(h-1)(v).io.out_S_psum
                m_DPE_array(h-1)(v).io.in_S_psum := m_DPE_array(h)(v).io.out_N_psum
                

                m_DPE_array(h)(v).io.in_S_wgt_bundle := m_DPE_array(h+1)(v).io.out_N_wgt_bundle
                m_DPE_array(h+1)(v).io.in_N_wgt_bundle := m_DPE_array(h)(v).io.out_S_wgt_bundle
                m_DPE_array(h)(v).io.in_S_psum := m_DPE_array(h+1)(v).io.out_N_psum
                m_DPE_array(h+1)(v).io.in_N_psum := m_DPE_array(h)(v).io.out_S_psum
            }
            m_DPE_array(h)(v).io.reset := io.reset
            m_DPE_array(h)(v).io.flow_direction := io.flow_direction(h)
            m_DPE_array(h)(v).io.operation_mode := io.operation_mode
        }
    }


    
}
