/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.eclipse.egit.gitflow.GitFlowConfig;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.ui.Activator;
import org.eclipse.egit.gitflow.ui.internal.JobFamilies;

import static org.eclipse.egit.gitflow.ui.internal.UIText.*;

import org.eclipse.egit.ui.test.ContextMenuHelper;
import org.eclipse.egit.ui.test.TestUtil;
import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.ui.PlatformUI;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the Team->Gitflow init
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class InitHandlerTest extends AbstractGitflowHandlerTest {
	private static final String DEVELOP_BRANCH = "a";
	private static final String MASTER_BRANCH = "b";
	private static final String FEATURE_BRANCH_PREFIX = "c";
	private static final String RELEASE_BRANCH_PREFIX = "d";
	private static final String HOTFIX_BRANCH_PREFIX = "e";
	private static final String VERSION_TAG_PREFIX = "f";

	private static final String ILLEGAL_BRANCH_NAME = "!@#$%^&*()_";

	@Test
	public void testInit() throws Exception {
		init();

		GitFlowRepository gitFlowRepository = new GitFlowRepository(repository);
		GitFlowConfig config = gitFlowRepository.getConfig();

		assertEquals(DEVELOP_BRANCH, repository.getBranch());
		assertEquals(MASTER_BRANCH, config.getMaster());
		assertEquals(FEATURE_BRANCH_PREFIX, config.getFeaturePrefix());
		assertEquals(RELEASE_BRANCH_PREFIX, config.getReleasePrefix());
		assertEquals(HOTFIX_BRANCH_PREFIX, config.getHotfixPrefix());
		assertEquals(VERSION_TAG_PREFIX, config.getVersionTagPrefix());

	}

	private void init() {
		final SWTBotTree projectExplorerTree = TestUtil.getExplorerTree();
		getProjectItem(projectExplorerTree, PROJ1).select();
		final String[] menuPath = new String[] {
				util.getPluginLocalizedValue("TeamMenu.label"),
				util.getPluginLocalizedValue("TeamGitFlowInit.name", false, Activator.getDefault().getBundle()) };
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				ContextMenuHelper.clickContextMenuSync(projectExplorerTree,
						menuPath);
			}
		});

		SWTBotText developText = bot.textWithLabel(InitDialog_developBranch);
		developText.selectAll();
		developText.typeText(ILLEGAL_BRANCH_NAME);


		SWTBotButton ok = bot.button("OK");
		assertFalse(ok.isEnabled());

		typeInto(InitDialog_developBranch, DEVELOP_BRANCH);
		typeInto(InitDialog_masterBranch, MASTER_BRANCH);
		typeInto(InitDialog_featureBranchPrefix, FEATURE_BRANCH_PREFIX);
		typeInto(InitDialog_releaseBranchPrefix, RELEASE_BRANCH_PREFIX);
		typeInto(InitDialog_hotfixBranchPrefix, HOTFIX_BRANCH_PREFIX);
		typeInto(InitDialog_versionTagPrefix, VERSION_TAG_PREFIX);

		ok.click();
		bot.waitUntil(Conditions.waitForJobs(JobFamilies.GITFLOW_FAMILY, "Git flow jobs"));
	}

	private void typeInto(String textLabel, String textInput) {
		SWTBotText developText = bot.textWithLabel(textLabel);
		developText.selectAll();
		developText.typeText(textInput);
	}
}
