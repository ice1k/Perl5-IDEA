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

package com.perl5.lang.perl.idea.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hurricup on 30.08.2015.
 */
@State(
		name = "Perl5Settings",
		storages = {
				@Storage(file = StoragePathMacros.PROJECT_FILE),
				@Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/perl5.xml", scheme = StorageScheme.DIRECTORY_BASED)
		}
)

public class Perl5Settings implements PersistentStateComponent<Perl5Settings>
{
	public List<String> libRoots = new ArrayList<String>();
	private Project myProject; // shouldn't be serialized

	public Perl5Settings()
	{
	}

	public Perl5Settings(Project myProject)
	{
		this.myProject = myProject;
	}

	public static Perl5Settings getInstance(@NotNull Project project)
	{
		Perl5Settings persisted = ServiceManager.getService(project, Perl5Settings.class);
		if (persisted != null)
			persisted.setProject(project);
		else
			persisted = new Perl5Settings(project);
		return persisted;

	}

	public void setProject(Project project)
	{
		myProject = project;
	}

	@Nullable
	@Override
	public Perl5Settings getState()
	{
		return this;
	}

	@Override
	public void loadState(Perl5Settings state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}

	public List<String> getLibRoots()
	{
		return libRoots;
	}
}
