package som.interpreter.nodes.literals;

import som.interpreter.Inliner;
import som.interpreter.Invokable;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockNode extends LiteralNode {

  protected final SMethod  blockMethod;
  protected final Universe universe;

  public BlockNode(final SMethod blockMethod, final Universe universe,
      final SourceSection source) {
    super(source);
    this.blockMethod  = blockMethod;
    this.universe     = universe;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return universe.newBlock(blockMethod, null);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    SMethod forInlining = (SMethod) cloneMethod(inliner);
    replace(new BlockNode(forInlining, universe, getSourceSection()));
  }

  protected SInvokable cloneMethod(final Inliner inliner) {
    Invokable clonedInvokable = blockMethod.getInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());
    SInvokable forInlining = universe.newMethod(blockMethod.getSignature(),
        clonedInvokable, false, new SMethod[0]);
    return forInlining;
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SMethod blockMethod,
        final Universe universe, final SourceSection source) {
      super(blockMethod, universe, source);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.universe, node.getSourceSection());
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return universe.newBlock(blockMethod, frame.materialize());
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      SMethod forInlining = (SMethod) cloneMethod(inliner);
      replace(new BlockNodeWithContext(forInlining, universe, getSourceSection()));
    }
  }
}
