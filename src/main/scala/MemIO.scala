// MemIO.scala -- CHISEL convenience Bundles for memory port definitions
// Author: Brian Richards 9/13/2011

package Mem {

import Chisel._;

abstract class MemoryPort[T <: Data](address: Num, dataProto: T) extends Bundle {
}

class ReadMemoryPort[T <: Data](address: Num, dataProto: T) extends MemoryPort[T](address, dataProto) {
  val addr = address.asInput;
  val readData = dataProto.clone.asOutput;
  val oen = Bool('input)
  val cs = Bool('input)
}

class WriteMemoryPort[T <: Data](address: Num, dataProto: T) extends MemoryPort[T](address, dataProto) {
  val writeData = dataProto.clone.asInput;
  val we = Bool('input);
  val cs = Bool('input);
}

class RWMemoryPort[T <: Data](address: Num, dataProto: T) extends MemoryPort[T](address, dataProto) {
  val readData = dataProto.clone.asOutput;
  val writeData = dataProto.clone.asInput;
  val oen = Bool('input);
  val we = Bool('input);
  val cs = Bool('input);
}

}