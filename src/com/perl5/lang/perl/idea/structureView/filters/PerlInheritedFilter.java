/*
 * Copyright 2015 Alexandr Evstigneev
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

package com.perl5.lang.perl.idea.structureView.filters;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.perl5.lang.perl.idea.structureView.elements.PerlStructureViewElement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by hurricup on 15.08.2015.
 */
public class PerlInheritedFilter implements Filter
{
	public static final PerlInheritedFilter INSTANCE = new PerlInheritedFilter();
	private static final String ID = "SHOW_INHERITED";

	@Override
	public boolean isVisible(TreeElement treeElement)
	{
		return !(treeElement instanceof PerlStructureViewElement && ((PerlStructureViewElement) treeElement).isInherited());
	}

	@Override
	public boolean isReverted()
	{
		return true;
	}

	@NotNull
	@Override
	public ActionPresentation getPresentation()
	{
		return new ActionPresentationData("Show inherited", null, AllIcons.Hierarchy.Supertypes);
	}

	@NotNull
	@Override
	public String getName()
	{
		return ID;
	}
}
