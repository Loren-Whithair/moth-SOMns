package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageAccessor.AbstractPrimitiveAccessor;
import som.vm.constants.Nil;
import som.vmobjects.SObject;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.FieldRead;


/**
 * Field read operations are modeled as normal message sends in Newspeak.
 * As a consequence, we use <code>CachedSlotRead</code> to handle some of the
 * variability encountered for field reads.
 *
 * <p>
 * Currently, we handle here the cases for unwritten object slots, slots that
 * contain object or primitive values, as well as the distinction for primitive
 * slots of whether they have been always set to a value before or not.
 * This allows for a small optimization of handling the bit that indicates for
 * primitive slot whether it is set to `nil` or an actual value.
 */
@GenerateWrapper
public abstract class CachedSlotRead extends AbstractDispatchNode {
  @Child protected AbstractDispatchNode nextInCache;
  @Child protected TypeCheckNode        typeCheck;

  protected final CheckSObject guardForRcvr;
  protected final SlotAccess   type;

  public CachedSlotRead(final SlotAccess type, final CheckSObject guardForRcvr,
      final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
    super(nextInCache.sourceSection);
    this.type = type;
    this.guardForRcvr = guardForRcvr;
    this.typeCheck = typeCheck;
    this.nextInCache = nextInCache;
    assert nextInCache != null;
  }

  /**
   * For wrapped nodes only.
   */
  protected CachedSlotRead() {
    super(null);
    this.type = null;
    this.guardForRcvr = null;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    try {
      if (guardForRcvr.entryMatches(arguments[0], null)) {
        return read(guardForRcvr.cast(arguments[0]));
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(frame, arguments);
    }
  }

  public abstract Object read(SObject rcvr);

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == ClassRead.class) {
      return type == SlotAccess.CLASS_READ;
    } else if (tag == FieldRead.class) {
      return type == SlotAccess.FIELD_READ;
    } else {
      return false;
    }
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    if (getParent() instanceof ClassSlotAccessNode) {
      return new CachedSlotReadWrapper(this, probe);
    } else {
      return new AbstractDispatchNodeWrapper(this, probe);
    }
  }

  public enum SlotAccess {
    FIELD_READ, CLASS_READ
  }

  public static final class UnwrittenSlotRead extends CachedSlotRead {

    public UnwrittenSlotRead(final SlotAccess type, final CheckSObject guardForRcvr,
        final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
      super(type, guardForRcvr, typeCheck, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      return Nil.nilObject;
    }
  }

  public static final class ObjectSlotRead extends CachedSlotRead {
    private final AbstractObjectAccessor accessor;

    public ObjectSlotRead(final AbstractObjectAccessor accessor,
        final SlotAccess type, final CheckSObject guardForRcvr,
        final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
      super(type, guardForRcvr, typeCheck, nextInCache);
      this.accessor = accessor;
    }

    @Override
    public Object read(final SObject rcvr) {
      return accessor.read(rcvr);
    }
  }

  private abstract static class PrimSlotRead extends CachedSlotRead {
    protected final AbstractPrimitiveAccessor accessor;
    protected final IntValueProfile           primMarkProfile;

    PrimSlotRead(final AbstractPrimitiveAccessor accessor, final SlotAccess type,
        final CheckSObject guardForRcvr, final TypeCheckNode typeCheck,
        final AbstractDispatchNode nextInCache) {
      super(type, guardForRcvr, typeCheck, nextInCache);
      this.accessor = accessor;
      this.primMarkProfile = IntValueProfile.createIdentityProfile();
    }
  }

  public static final class LongSlotReadSetOrUnset extends PrimSlotRead {

    public LongSlotReadSetOrUnset(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guardForRcvr,
        final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
      super(accessor, type, guardForRcvr, typeCheck, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readLong(rcvr);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class LongSlotReadSet extends PrimSlotRead {

    public LongSlotReadSet(final AbstractPrimitiveAccessor accessor, final SlotAccess type,
        final CheckSObject guardForRcvr, final TypeCheckNode typeCheck,
        final AbstractDispatchNode nextInCache) {
      super(accessor, type, guardForRcvr, typeCheck, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readLong(rcvr);
      } else {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        replace(new LongSlotReadSetOrUnset(accessor, type, guardForRcvr, typeCheck,
            nextInCache));
        return Nil.nilObject;
      }
    }
  }

  public static final class DoubleSlotReadSetOrUnset extends PrimSlotRead {

    public DoubleSlotReadSetOrUnset(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guardForRcvr,
        final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
      super(accessor, type, guardForRcvr, typeCheck, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readDouble(rcvr);
      } else {
        return Nil.nilObject;
      }
    }
  }

  public static final class DoubleSlotReadSet extends PrimSlotRead {

    public DoubleSlotReadSet(final AbstractPrimitiveAccessor accessor,
        final SlotAccess type, final CheckSObject guardForRcvr,
        final TypeCheckNode typeCheck, final AbstractDispatchNode nextInCache) {
      super(accessor, type, guardForRcvr, typeCheck, nextInCache);
    }

    @Override
    public Object read(final SObject rcvr) {
      if (accessor.isPrimitiveSet(rcvr, primMarkProfile)) {
        return accessor.readDouble(rcvr);
      } else {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        replace(new DoubleSlotReadSetOrUnset(accessor, type, guardForRcvr, typeCheck,
            nextInCache));
        return Nil.nilObject;
      }
    }
  }
}
