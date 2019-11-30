package firrtl
package transforms

import firrtl.ir._
import firrtl.Mappers._

import firrtl.Utils.{isCast, NodeMap}

object InlineCastsTransform {

  /** Recursively replace [[WRef]]s with new [[Expression]]s
    *
    * @param replace a '''mutable''' HashMap mapping [[WRef]]s to values with which the [[WRef]]
    * will be replaced. It is '''not''' mutated in this function
    * @param expr the Expression being transformed
    * @return Returns expr with [[WRef]]s replaced by values found in replace
    */
  def onExpr(replace: NodeMap)(expr: Expression): Expression = {
    expr.map(onExpr(replace)) match {
      case e @ WRef(name, _,_,_) =>
        replace.get(name)
               .filter(isCast)
               .getOrElse(e)
      case e @ DoPrim(op, Seq(WRef(name, _,_,_)), _,_) if isCast(op) =>
        replace.getOrElse(name, e)
      case other => other // Not a candidate
    }
  }

  /** Inline casts in a Statement
    *
    * @param netlist a '''mutable''' HashMap mapping references to [[firrtl.ir.DefNode DefNode]]s to their connected
    * [[firrtl.ir.Expression Expression]]s. This function '''will''' mutate it if stmt is a [[firrtl.ir.DefNode
    * DefNode]] with a value that is a cast [[PrimOp]]
    * @param stmt the Statement being searched for nodes and transformed
    * @return Returns stmt with casts inlined
    */
  def onStmt(netlist: NodeMap)(stmt: Statement): Statement =
    stmt.map(onStmt(netlist)).map(onExpr(netlist)) match {
      case node @ DefNode(_, name, value) =>
        netlist(name) = value
        node
      case other => other
    }

  /** Replaces truncating arithmetic in a Module */
  def onMod(mod: DefModule): DefModule = mod.map(onStmt(new NodeMap))
}

/** Inline nodes that are simple casts */
class InlineCastsTransform extends Transform {
  def inputForm = LowForm
  def outputForm = LowForm

  def execute(state: CircuitState): CircuitState = {
    val modulesx = state.circuit.modules.map(InlineCastsTransform.onMod(_))
    state.copy(circuit = state.circuit.copy(modules = modulesx))
  }
}
