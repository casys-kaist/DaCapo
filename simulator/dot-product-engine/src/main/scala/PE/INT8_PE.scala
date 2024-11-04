package PE
import chisel3._
import chisel3.util._

class INT8_PE extends Module {
    val io = IO(new Bundle{
        val op1 = Input(SInt(8.W))
        val op2 = Input(SInt(8.W))
        val drain_result = Input(SInt(32.W))
        val op_sig = Input(UInt(1.W))
        val out = Output(SInt(32.W))
    })

    val acc = RegInit(0.S(32.W))
    acc := Mux(io.op_sig === 1.U(1.W), io.op1*io.op2 + acc, io.drain_result)

    io.out := acc
}


object INT8_PE_emit extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new INT8_PE)
}