/*
 * Copyright 2016 Alexandr Evstigneev
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

package com.perl5.lang.htmlmason.parser.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.perl5.lang.htmlmason.HTMLMasonLanguage;
import com.perl5.lang.htmlmason.HTMLMasonUtils;
import com.perl5.lang.htmlmason.MasonCoreUtils;
import com.perl5.lang.htmlmason.elementType.HTMLMasonElementTypes;
import com.perl5.lang.htmlmason.idea.configuration.HTMLMasonSettings;
import com.perl5.lang.htmlmason.parser.psi.HTMLMasonFlagsStatement;
import com.perl5.lang.htmlmason.parser.stubs.HTMLMasonFlagsStatementStub;
import com.perl5.lang.htmlmason.parser.stubs.HTMLMasonFlagsStubIndex;
import com.perl5.lang.perl.PerlScopes;
import com.perl5.lang.perl.extensions.PerlImplicitVariablesProvider;
import com.perl5.lang.perl.psi.PerlVariableDeclarationWrapper;
import com.perl5.lang.perl.psi.impl.PerlFileImpl;
import com.perl5.lang.perl.psi.utils.PerlPsiUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by hurricup on 05.03.2016.
 */
public class HTMLMasonFileImpl extends PerlFileImpl implements HTMLMasonElementTypes, PerlImplicitVariablesProvider
{
	protected final List<PerlVariableDeclarationWrapper> myImplicitVariables = new ArrayList<PerlVariableDeclarationWrapper>();
	protected int myMasonChangeCounter;
	protected Map<Integer, Boolean> myPerlLinesMap = new THashMap<Integer, Boolean>();

	public HTMLMasonFileImpl(@NotNull FileViewProvider viewProvider)
	{
		super(viewProvider, HTMLMasonLanguage.INSTANCE);
	}

	@Nullable
	public VirtualFile getComponentRoot()
	{
		return HTMLMasonUtils.getComponentRoot(getProject(), getComponentVirtualFile());
	}

	public VirtualFile getComponentVirtualFile()
	{
		return MasonCoreUtils.getContainingVirtualFile(this);
	}

	/**
	 * Returns absolute path relative to the components root
	 *
	 * @return path
	 */
	@Nullable
	public String getAbsoluteComponentPath()
	{
		VirtualFile componentFile = getComponentVirtualFile();
		VirtualFile componentRoot = getComponentRoot();

		if (componentFile != null && componentRoot != null)
		{
			return '/' + VfsUtil.getRelativePath(componentFile, componentRoot);
		}
		return null;
	}

	/**
	 * Returns absolute containing dir path relative to the components root
	 *
	 * @return path
	 */
	@Nullable
	public String getAbsoluteComponentContainerPath()
	{
		VirtualFile componentFile = getComponentVirtualFile();
		VirtualFile componentRoot = getComponentRoot();

		if (componentFile != null && componentRoot != null)
		{
			return '/' + VfsUtil.getRelativePath(componentFile.getParent(), componentRoot);
		}
		return null;
	}

	public boolean isInPerlLine(PsiElement element)
	{
		if (element == null)
			return false;

		ASTNode node = element.getNode();
		int startOffset = node.getStartOffset();
//		int endOffset = startOffset + node.getTextLength();

		return isInPerlLine(startOffset); ///|| isInPerlLine(endOffset);
	}

	protected boolean isInPerlLine(int offset)
	{
		Document document = PsiDocumentManager.getInstance(getProject()).getDocument(this);
		if (document != null)
		{
			int lineNumber = document.getLineNumber(offset);

			Boolean result = myPerlLinesMap.get(lineNumber);

			if (result != null)
				return result;

			PsiElement firstElement = findElementAt(document.getLineStartOffset(lineNumber));

			result = firstElement != null && firstElement.getNode().getElementType() == HTML_MASON_LINE_OPENER;

			myPerlLinesMap.put(lineNumber, result);
			return result;
		}
		return false;
	}

	@NotNull
	@Override
	public List<PerlVariableDeclarationWrapper> getImplicitVariables()
	{
		if (myMasonChangeCounter != HTMLMasonSettings.getInstance(getProject()).getChangeCounter())
		{
			fillImplicitVariables();
		}
		return myImplicitVariables;
	}

	protected void fillImplicitVariables()
	{
		myImplicitVariables.clear();

		if (isValid())
		{
			HTMLMasonSettings settings = HTMLMasonSettings.getInstance(getProject());
			MasonCoreUtils.fillVariablesList(this, myImplicitVariables, settings.globalVariables);
			myMasonChangeCounter = settings.getChangeCounter();
		}
	}

	@Nullable
	public HTMLMasonFileImpl getParentComponent()
	{
		String parentComponentPath = getParentComponentPath();
		HTMLMasonSettings settings = HTMLMasonSettings.getInstance(getProject());
		VirtualFile parentFile = null;

		if (parentComponentPath == null) // autohandler
		{
			VirtualFile containingFile = getComponentVirtualFile();
			if (containingFile != null)
			{
				VirtualFile startDir = containingFile.getParent();
				if (StringUtil.equals(containingFile.getName(), settings.autoHandlerName))
				{
					startDir = startDir.getParent();
				}

				VirtualFile componentRoot = HTMLMasonUtils.getComponentRoot(getProject(), startDir);
				if (componentRoot != null)
				{
					while (VfsUtil.isAncestor(componentRoot, startDir, false))
					{
						if ((parentFile = startDir.findFileByRelativePath(settings.autoHandlerName)) != null)
						{
							break;
						}
						startDir = startDir.getParent();
					}
				}
			}
		}
		else if (!StringUtil.equals(parentComponentPath, HTMLMasonFlagsStatement.UNDEF_RESULT)) // Specific component
		{
			if (StringUtil.startsWith(parentComponentPath, "/")) // absolute path
			{
				parentComponentPath = parentComponentPath.substring(1);
				for (VirtualFile root : settings.getComponentsRootsVirtualFiles())
				{
					if ((parentFile = root.findFileByRelativePath(parentComponentPath)) != null)
					{
						break;
					}

				}
			}
			else // relative path
			{
				VirtualFile containingVirtualFile = getComponentVirtualFile();
				if (containingVirtualFile != null)
				{
					VirtualFile containingDir = containingVirtualFile.getParent();
					if (containingDir != null)
					{
						parentFile = containingDir.findFileByRelativePath(parentComponentPath);
					}
				}
			}
		}

		if (parentFile != null)
		{
			PsiFile file = PsiManager.getInstance(getProject()).findFile(parentFile);
			if (file instanceof HTMLMasonFileImpl)
			{
				return (HTMLMasonFileImpl) file;
			}
		}

		return null;
	}

	@NotNull
	public List<HTMLMasonFileImpl> getChildComponents()
	{
		final List<HTMLMasonFileImpl> result = new ArrayList<HTMLMasonFileImpl>();
		VirtualFile containingFile = getComponentVirtualFile();

		if (containingFile != null)
		{
			VirtualFile componentRoot = getComponentRoot();

			if (componentRoot != null)
			{
				final String relativePath = VfsUtil.VFS_SEPARATOR_CHAR + VfsUtil.getRelativePath(containingFile, componentRoot);
				final Project project = getProject();
				final GlobalSearchScope scope = PerlScopes.getProjectAndLibrariesScope(project);
				final HTMLMasonFileImpl currentFile = this;
				HTMLMasonSettings settings = HTMLMasonSettings.getInstance(project);

				// indexed children
				StubIndex.getInstance().processAllKeys(
						HTMLMasonFlagsStubIndex.KEY,
						new Processor<String>()
						{
							@Override
							public boolean process(String parentPath)
							{
								boolean isEquals = StringUtil.equals(relativePath, parentPath);
								boolean isRelative = parentPath.length() == 0 || parentPath.charAt(0) != VfsUtil.VFS_SEPARATOR_CHAR;

								for (HTMLMasonFlagsStatement statement : StubIndex.getElements(
										HTMLMasonFlagsStubIndex.KEY,
										parentPath,
										project,
										scope,
										HTMLMasonFlagsStatement.class
								))
								{
									PsiFile file = statement.getContainingFile();
									if (file instanceof HTMLMasonFileImpl)
									{
										if (isEquals || isRelative && currentFile.equals(((HTMLMasonFileImpl) file).getParentComponent()))
										{
											result.add((HTMLMasonFileImpl) file);
										}
									}
								}

								return true;
							}
						},
						scope,
						null
				);

				// implicit autohandled children
				if (StringUtil.equals(containingFile.getName(), settings.autoHandlerName))
				{
					collectAuthoHandledFiles(PsiManager.getInstance(project), containingFile.getParent(), result, settings.autoHandlerName, null);
				}
			}
		}

		return result;
	}

	protected void collectAuthoHandledFiles(
			PsiManager manager,
			VirtualFile dir,
			List<HTMLMasonFileImpl> result,
			String autoHandlerName,
			@Nullable Set<VirtualFile> recursionMap
	)
	{
		if (dir != null)
		{
			if (recursionMap == null) // first iteration
			{
				recursionMap = new THashSet<VirtualFile>();
			}
			else // non-first iteration
			{
				VirtualFile autohandlerVirtualFile = dir.findChild(autoHandlerName);
				if (autohandlerVirtualFile != null)
				{
					PsiFile autohandlerPsiFile = manager.findFile(autohandlerVirtualFile);
					if (autohandlerPsiFile instanceof HTMLMasonFileImpl && this.equals(((HTMLMasonFileImpl) autohandlerPsiFile).getParentComponent()))
					{
						result.add((HTMLMasonFileImpl) autohandlerPsiFile);
					}
					return;
				}
			}

			recursionMap.add(dir);

			// all iterations
			for (VirtualFile file : dir.getChildren())
			{
				if (file.isDirectory() && !recursionMap.contains(file))
				{
					collectAuthoHandledFiles(manager, file, result, autoHandlerName, recursionMap);
				}
				else if (!StringUtil.equals(file.getName(), autoHandlerName)) // non-autohandler file
				{
					PsiFile psiFile = manager.findFile(file);
					if (psiFile instanceof HTMLMasonFileImpl && this.equals(((HTMLMasonFileImpl) psiFile).getParentComponent()))
					{
						result.add((HTMLMasonFileImpl) psiFile);
					}
				}
			}
		}
	}

	@Nullable
	protected String getParentComponentPath()
	{
		HTMLMasonFlagsStatement statement = getFlagsStatement();
		return statement == null ? null : statement.getParentComponentPath();
	}

	@Nullable
	public HTMLMasonFlagsStatement getFlagsStatement()
	{
		StubElement stub = getStub();
		FlagsStatementSeeker seeker = null;

		if (stub != null)
		{
			seeker = new FlagsStatementStubSeeker();
			//noinspection unchecked
			PerlPsiUtil.processElementsFromStubs(stub, seeker, null);
		}
		else
		{
			seeker = new FlagsStatementPsiSeeker();
			//noinspection unchecked
			PerlPsiUtil.processElementsFromPsi(this, seeker, null);
		}
		return seeker.getResult();
	}

	protected abstract static class FlagsStatementSeeker<T> implements Processor<T>
	{
		protected HTMLMasonFlagsStatement myResult = null;

		public HTMLMasonFlagsStatement getResult()
		{
			return myResult;
		}
	}

	protected static class FlagsStatementStubSeeker extends FlagsStatementSeeker<Stub>
	{
		@Override
		public boolean process(Stub stub)
		{
			if (stub instanceof HTMLMasonFlagsStatementStub)
			{
				myResult = ((HTMLMasonFlagsStatementStub) stub).getPsi();
				return false;
			}
			return true;
		}
	}

	protected static class FlagsStatementPsiSeeker extends FlagsStatementSeeker<PsiElement>
	{
		@Override
		public boolean process(PsiElement element)
		{
			if (element instanceof HTMLMasonFlagsStatement)
			{
				myResult = (HTMLMasonFlagsStatement) element;
				return false;
			}
			return true;
		}
	}
}