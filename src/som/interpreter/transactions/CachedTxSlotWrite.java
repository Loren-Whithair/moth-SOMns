package som.interpreter.transactions;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SMutableObject;


public final class CachedTxSlotWrite extends CachedSlotWrite {
  @Child protected CachedSlotWrite write;

  public CachedTxSlotWrite(final CachedSlotWrite write, final CheckSObject guardForRcvr,
      final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
    super(guardForRcvr, typeCheck, nextInCache);
    this.write = write;
  }

  @Override
  public void doWrite(final SObject obj, final Object value) {
    SMutableObject workingCopy = Transactions.workingCopy((SMutableObject) obj);
    write.doWrite(workingCopy, value);
  }
}
