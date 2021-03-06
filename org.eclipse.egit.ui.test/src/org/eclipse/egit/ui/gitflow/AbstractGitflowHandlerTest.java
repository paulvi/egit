/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.gitflow;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.op.FeatureCheckoutOperation;
import org.eclipse.egit.gitflow.op.FeatureStartOperation;
import org.eclipse.egit.ui.common.LocalRepositoryTestCase;
import org.eclipse.egit.ui.test.TestUtil;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Tests for the Team->Gitflow
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public abstract class AbstractGitflowHandlerTest extends LocalRepositoryTestCase {
	protected static final String DEVELOP = "develop";
	protected static final String FEATURE_NAME = "myFeature";

	protected Repository repository;

	@Before
	public void setup() throws Exception {
		File repositoryFile = createProjectAndCommitToRepository();
		repository = lookupRepository(repositoryFile);
	}

	protected RevCommit setContentAddAndCommit(String newContent) throws Exception, GitAPIException, NoHeadException,
	NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException,
	AbortedByHookException, IOException {
		setTestFileContent(newContent);

		Git git = Git.wrap(repository);
		git.add().addFilepattern(".").call();
		CommitCommand commit = git.commit().setMessage(newContent);
		commit.setAuthor(TestUtil.TESTCOMMITTER_NAME, TestUtil.TESTCOMMITTER_EMAIL);
		commit.setCommitter(TestUtil.TESTCOMMITTER_NAME, TestUtil.TESTCOMMITTER_EMAIL);
		return commit.call();
	}

	protected void createFeature(String featureName) throws CoreException {
		new FeatureStartOperation(new GitFlowRepository(repository),
				featureName).execute(null);
	}

	protected void checkoutFeature(String featureName) throws CoreException {
		new FeatureCheckoutOperation(new GitFlowRepository(repository),
				featureName).execute(null);
	}
}
