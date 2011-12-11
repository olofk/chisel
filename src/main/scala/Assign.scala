// author: jonathan bachrach
package Chisel {

import scala.collection.mutable.HashMap;
import scala.collection.mutable.ListBuffer;
import Node._;
import ChiselError._;

object Assign {
  def apply[T <: Data]()(gen: => T): T = {
    val junctioncell = new AssignCell[T](gen, -1)(gen);
    junctioncell.io.out;
  }
  def apply[T <: Data](width: Int)(gen: =>T): T = {
    val junctioncell = new AssignCell[T](gen, width)(gen);
    junctioncell.io.out;
  }
  def apply[T <: Data](default: T): T = {
    val junctioncell = new AssignCell[T](default, -1)(default.clone.asInstanceOf[T]);
    junctioncell(default);
    junctioncell.io.out;
  }
  def cell[T <: Data]()(gen: => T): AssignCell[T] = {
    val junctioncell = new AssignCell[T](gen, -1)(gen);
    junctioncell;
  }
  def cell[T <: Data](width: Int)(gen: =>T): AssignCell[T] = {
    val junctioncell = new AssignCell[T](gen, width)(gen);
    junctioncell;
  }
  def cell[T <: Data](default: T): AssignCell[T] = {
    val junctioncell = new AssignCell[T](default, -1)(default.clone.asInstanceOf[T]);
    junctioncell(default);
    junctioncell;
  }
}


class AssignCell[T <: Data](data: T, width: Int)(gen: => T){
  val io = new Bundle(){
    val out = gen.asOutput();
  }
  io.setIsCellIO;
  val primitiveNode = new Assign(this);
  var port_id = 0;
  val assign_ports = new ListBuffer[AssignPort[T]];
  if(width > -1) {
    primitiveNode.init("primitiveNode", width)
  } else {
    primitiveNode.init("primitiveNode", widthOf(0));
  }
  val fb = io.out.fromNode(primitiveNode).asInstanceOf[T] 
  fb.setIsCellIO;
  fb ^^ io.out;
  io.out.comp = primitiveNode.asInstanceOf[Assign[T]];
  primitiveNode.nameHolder = io.out;

  def apply(value:     T,
            max_index: UFix,
            min_index: UFix,
            condition: Bool): Unit = {
    val assign_port = new AssignPort(this, value, max_index, min_index, condition);
    assign_ports += assign_port;
  }
  def apply(value: T): Unit = apply(value, null, null, null);
  def apply(value: T, max_index: Int, min_index: Int, condition: Bool): Unit = apply(value, UFix(max_index), UFix(min_index), condition);
  def apply(value: T, max_index: UFix, min_index: UFix): Unit = apply(value, max_index, min_index, null);
  def apply(value: T, max_index: Int,  min_index: Int): Unit = apply(value, UFix(max_index), UFix(min_index), null);
  def apply(value: T, bit_index: UFix, condition: Bool): Unit = apply(value, bit_index, null, condition);
  def apply(value: T, bit_index: Int,  condition: Bool): Unit = apply(value, UFix(bit_index), null, condition);
  def apply(value: T, bit_index: UFix): Unit = apply(value, bit_index, null, null);
  def apply(value: T, bit_index: Int): Unit = apply(value, UFix(bit_index), null, null);

  // Dynamic port assignment support:
  // The input_map may be redundant with Node.setName (TODO)
  val input_map = new HashMap[String, Int];
  def add_input(name: String, node: Data) = {
    if (input_map contains name) {
      println("[warning] Assign port "+name+" has already been added");
    } else {
      val node_port = node.clone.asInput;
      val node_offset = primitiveNode.inputs.length;
      input_map(name) = node_offset;
      node_port.setName(name);
      io += node_port;
      primitiveNode.inputs += node_port
      node_port <> node;
    }
  }
  def get_input(name: String): Node = {
    if (input_map contains name) {
      primitiveNode.inputs(input_map(name));
    } else {
      null;
    }
  }
  def has_input(name: String) = input_map contains name;
}

class AssignPort[T <: Data](cell: AssignCell[T],
                            value:     T,
                            max_index: UFix,
                            min_index: UFix,
                            condition: Bool = null) {
  val target = cell.primitiveNode;
  val id = cell.port_id;
  cell.port_id += 1;
  cell.add_input("val"+id, value);
  if (!(max_index == null)) cell.add_input("max"+id, max_index);
  if (!(min_index == null)) cell.add_input("min"+id, min_index);
  if (!(condition == null)) cell.add_input("cond"+id, condition);

  def getValue    =  cell.get_input("val"+id);
  def getMaxIndex =  cell.get_input("max"+id);
  def getMinIndex =  cell.get_input("min"+id);
  def getCondition = cell.get_input("cond"+id);

  def emitValue = (if (getValue == null) "null" else getValue.emitRef);
  def emitMaxIndex =  (if (getMaxIndex == null) "nullMaxIndex" else getMaxIndex.emitRef);
  def emitMinIndex =  (if (getMinIndex == null) "nullMinIndex" else getMinIndex.emitRef);

  def emitDefAssign: String = {
    var res = cell.primitiveNode.emitTmp;
    if (getMinIndex == null && getMaxIndex == null) {
      res += " = ";
    } else if (getMinIndex == null) {
      res += "["+emitMaxIndex+"] = ";
    } else {
      res += "["+emitMaxIndex+":"+emitMinIndex+"] = ";
    }
    if (getValue == null) {
      // Undefined behavior!
      println("[error] Unconnected assignment in "+cell);
      res = "// "+res+"UNCONNECTED_ASSIGN";
    } else {
      res += getValue.emitRef;
    }
    res;
  }
  def emitDefIf: String = {
    (if (getCondition == null) "" else "if ("+getCondition.emitRef+")");
  }

  def emitDefAssignLoC: String = {
    var res = cell.primitiveNode.emitTmp;
    if (getMinIndex == null && getMaxIndex == null) {
      res += " = "+emitValue+";\n";
    } else if (getMinIndex == null) {
      res += ".inject("+emitValue+","+emitMaxIndex+","+emitMaxIndex+");\n";
    } else {
      res += ".inject("+emitValue+","+emitMaxIndex+","+emitMinIndex+");\n";
    }
    res;
  }
  def emitDefIfLoC: String = {
    (if (getCondition == null) "" else "if ("+getCondition.emitRef+")");
  }
}

class Assign[T <: Data](cell: AssignCell[T]) extends Data with proc {
  // override def toString: String = "W(" + name + ")"
  var assigned = false;
  def default: Node = if (inputs.length < 1 || inputs(0) == null) null else inputs(0);
  override def toNode = this;
  override def fromNode(src: Node) = {
    println("[error] Assign.fromNode not implemented");
    //val res = new Assign(cell).asInstanceOf[this.type];
    //res assign src;
    this;
  }
  def procAssign(src: Node) = {
    if (assigned) {
      ChiselErrors += IllegalState("reassignment to Node", 3);
    } else {
      var res = Lit(true);
      for (i <- 0 until conds.length)
        res = conds(i) && res;
      updates.push((res, src));
    }
  }
  override def toString: String = name
  override def emitDec: String = "  reg" + (if (isSigned) " signed " else "") + emitWidth + " " + emitRef + ";\n";
  override def emitDef: String = {
    var res = "  always@(*) begin\n";
    if (inputs.length == 0) {
      println("[error] No assignments made to "+emitTmp);
      return res;
    }
    var active_condition = cell.assign_ports(0).getCondition;
    var inside_if_body = false;
    var indent = "    ";
    for (ap <- cell.assign_ports) {
      if (ap.getCondition == active_condition) {
        // Add an assignment to the current body:
      } else if (ap == null) {
        // Set the default value, outside of IF:
        active_condition = null;
        if (inside_if_body == true) {
          res += "    end\n";
          indent = "    ";
          inside_if_body = false;
        }
        res += indent+ap.emitDefAssign+";\n";
      } else {
        // This is a new IF condition:
        active_condition = ap.getCondition;
        if (!inside_if_body) {
          res += "    "+ap.emitDefIf+" begin\n";
          indent = "      ";
          inside_if_body = true;
        } else {
          res += "    end else "+ap.emitDefIf+" begin\n";
        }
      }
      res += indent+ap.emitDefAssign+";\n";
    }
    if (inside_if_body) res += "    end\n";
    res += "  end\n";
    res;
  }
  override def emitDefLoC: String = {
    var res = "";
    if (inputs.length == 0) {
      println("[error] No assignments made to "+emitTmp);
      return res;
    }
    var active_condition = cell.assign_ports(0).getCondition;
    var inside_if_body = false;
    var indent = "  ";
    for (ap <- cell.assign_ports) {
      if (ap.getCondition == active_condition) {
        // Add an assignment to the current body:
      } else if (ap == null) {
        // Set the default value, outside of IF:
        active_condition = null;
        if (inside_if_body == true) {
          res += "  }\n";
          indent = "  ";
          inside_if_body = false;
        }
      } else {
        // This is a new IF condition:
        active_condition = ap.getCondition;
        if (!inside_if_body) {
          res += "  "+ap.emitDefIfLoC+" {\n";
          indent = "      ";
          inside_if_body = true;
        } else {
          res += "  } else "+ap.emitDefIfLoC+" {\n";
        }
      }
      res += indent+ap.emitDefAssignLoC;
    }
    if (inside_if_body) res += "  }\n";
    res;
  }

  override def assign(src: Node) = {
    src match {
      case data: Data => {
        cell(data.asInstanceOf[T]);
      }
      case any => {
        println("[error] Unrecognized assign data type");
      }
    }
    //if(assigned || inputs(0) != null)
    //  ChiselErrors += IllegalState("reassignment to Assign", 3);
    //else { assigned = true; super.assign(src)}
  }
  def assign_from_extract(extract: Extract, src: Bits) = {
    println("[info] Trying assign_from_extract");
    cell(src.asInstanceOf[T], extract.inputs(1).asInstanceOf[UFix], extract.inputs(2).asInstanceOf[UFix]);
  }
}

}
