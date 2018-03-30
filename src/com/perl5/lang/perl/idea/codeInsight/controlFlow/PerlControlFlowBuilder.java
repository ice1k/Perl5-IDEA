/*
 * Copyright 2015-2017 Alexandr Evstigneev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.perl5.lang.perl.idea.codeInsight.controlFlow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilCore;
import com.perl5.lang.perl.psi.*;
import com.perl5.lang.perl.psi.mixins.PerlStatementMixin;
import com.perl5.lang.perl.psi.utils.PerlPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.perl5.lang.perl.lexer.PerlElementTypesGenerated.*;

public class PerlControlFlowBuilder extends ControlFlowBuilder {
  private static final TokenSet LOOP_MODIFIERS = TokenSet.create(
    FOR_STATEMENT_MODIFIER,
    UNTIL_STATEMENT_MODIFIER,
    WHILE_STATEMENT_MODIFIER
  );

  private Instruction myLastModifierExpressionInstruction;

  public ControlFlow build(PsiElement element) {
    return super.build(new PerlControlFlowVisitor(), element);
  }

  public static ControlFlow getFor(@NotNull PsiElement element) {
    return CachedValuesManager
      .getCachedValue(element, () -> CachedValueProvider.Result.create(new PerlControlFlowBuilder().build(element), element));
  }

  private class PerlControlFlowVisitor extends PerlRecursiveVisitor {
    @Override
    public void visitAssignExpr(@NotNull PsiPerlAssignExpr o) {
      PsiElement rightSide = o.getLastChild();
      while (true) {
        PsiElement operator = PerlPsiUtil.getPrevSignificantSibling(rightSide);
        if (operator == null) {
          return;
        }
        PsiElement leftSide = PerlPsiUtil.getPrevSignificantSibling(operator);
        if (leftSide == null) {
          return;
        }
        startNode(rightSide);
        addNodeAndCheckPending(new PerlAssignInstuction(PerlControlFlowBuilder.this, o, leftSide, rightSide, operator));
        rightSide = leftSide;
      }
    }

    @Override
    public void visitStatement(@NotNull PsiPerlStatement o) {
      if (o instanceof PerlStatementMixin) {
        PsiPerlStatementModifier modifier = ((PerlStatementMixin)o).getModifier();
        Instruction modifierExpression = null;
        if (modifier != null) {
          modifier.accept(this);
          modifierExpression = myLastModifierExpressionInstruction;
        }
        PsiPerlExpr expr = o.getExpr();
        if (expr != null) {
          expr.accept(this);
        }

        if (LOOP_MODIFIERS.contains(PsiUtilCore.getElementType(modifier))) {
          addEdge(prevInstruction, modifierExpression);
          prevInstruction = modifierExpression;
        }
      }
      else {
        super.visitStatement(o);
      }
    }

    @Override
    public void visitCallArguments(@NotNull PsiPerlCallArguments o) {
      processChildrenBackwardsAndElement(o);
    }

    @Override
    public void visitArrayIndex(@NotNull PsiPerlArrayIndex o) {
      processChildrenBackwardsAndElement(o);
    }

    @Override
    public void visitHashIndex(@NotNull PsiPerlHashIndex o) {
      processChildrenBackwardsAndElement(o);
    }

    @Override
    public void visitExpr(@NotNull PsiPerlExpr o) {
      processChildrenBackwardsAndElement(o);
    }

    @Override
    public void visitBlock(@NotNull PsiPerlBlock o) {
      super.visitElement(o);
    }

    @Override
    public void visitPrintExpr(@NotNull PsiPerlPrintExpr o) {
      PsiPerlParenthesisedCallArguments arguments = o.getParenthesisedCallArguments();
      processChildrenBackwardsAndElement(arguments == null ? o : arguments, o);
    }

    private void processChildrenBackwardsAndElement(@NotNull PsiElement psiElement) {
      processChildrenBackwardsAndElement(psiElement, psiElement);
    }

    private void processChildrenBackwards(@NotNull PsiElement psiElement) {
      processChildrenBackwardsAndElement(psiElement);
    }

    @Override
    public void visitReturnExpr(@NotNull PsiPerlReturnExpr o) {
      PsiPerlExpr returnValueExpr = o.getReturnValueExpr();
      if (returnValueExpr != null) {
        returnValueExpr.accept(this);
      }
      startNode(o);
      addPendingEdge(o.getReturnScope(), prevInstruction);
      flowAbrupted();
    }

    /**
     * Iterates composite children of {@code parentElement}, creates nodes for them and than,
     * creates node for {@code realParent} if specified
     */
    private void processChildrenBackwardsAndElement(@NotNull PsiElement parentElement,
                                                    @Nullable PsiElement realParentElement) {
      PsiElement[] children = parentElement.getChildren();
      for (int i = children.length - 1; i >= 0; i--) {
        children[i].accept(this);
      }
      if (realParentElement != null) {
        startNode(realParentElement);
      }
    }

    @Override
    public void visitPerlStatementModifier(@NotNull PerlStatementModifier o) {
      PsiPerlExpr condition = o.getExpr();
      if (condition != null) {
        condition.accept(this);
        myLastModifierExpressionInstruction = prevInstruction;
      }
      addPendingEdge(o.getParent(), prevInstruction);
      startConditionalNode(o, condition, true);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!(element instanceof LeafPsiElement)) {
        startNode(element);
        super.visitElement(element);
      }
    }
  }
}
