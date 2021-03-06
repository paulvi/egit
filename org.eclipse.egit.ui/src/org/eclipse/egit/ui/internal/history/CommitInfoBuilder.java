/*******************************************************************************
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2011, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2011, Stefan Lay <stefan.lay@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.ui.internal.history;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.history.CommitMessageViewer.ObjectLink;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;

/**
 * Class to build and format commit info in History View
 */
public class CommitInfoBuilder {

	private static final String SPACE = " "; //$NON-NLS-1$

	private static final String LF = "\n"; //$NON-NLS-1$

	private static final int MAXBRANCHES = 20;

	private final DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

	private PlotCommit<?> commit;

	private final Repository db;

	private final boolean fill;

	private Color linkColor;

	private Color darkGrey;

	private final Collection<Ref> allRefs;

	/**
	 * @param db the repository
	 * @param commit the commit the info should be shown for
	 * @param fill whether to fill the available space
	 * @param allRefs all Ref's to examine regarding marge bases
	 */
	public CommitInfoBuilder(Repository db, PlotCommit commit, boolean fill,
			Collection<Ref> allRefs) {
		this.db = db;
		this.commit = commit;
		this.fill = fill;
		this.allRefs = allRefs;
	}

	/**
	 * set colors for formatting
	 *
	 * @param linkColor
	 * @param darkGrey
	 */
	public void setColors(Color linkColor, Color darkGrey) {
		this.linkColor = linkColor;
		this.darkGrey = darkGrey;
	}

	/**
	 * Format the commit info
	 *
	 * @param styles styles for text formatting
	 * @param monitor
	 * @return formatted commit info
	 * @throws IOException
	 */
	public String format(final List<StyleRange> styles,
			IProgressMonitor monitor) throws IOException {
		boolean trace = GitTraceLocation.HISTORYVIEW.isActive();
		if (trace)
			GitTraceLocation.getTrace().traceEntry(
					GitTraceLocation.HISTORYVIEW.getLocation());
		monitor.setTaskName(UIText.CommitMessageViewer_FormattingMessageTaskName);
		final StringBuilder d = new StringBuilder();
		final PersonIdent author = commit.getAuthorIdent();
		final PersonIdent committer = commit.getCommitterIdent();
		d.append(UIText.CommitMessageViewer_commit);
		d.append(SPACE);
		d.append(commit.getId().name());
		d.append(LF);

		if (author != null) {
			d.append(UIText.CommitMessageViewer_author);
			d.append(": "); //$NON-NLS-1$
			d.append(author.getName());
			d.append(" <"); //$NON-NLS-1$
			d.append(author.getEmailAddress());
			d.append("> "); //$NON-NLS-1$
			d.append(fmt.format(author.getWhen()));
			d.append(LF);
		}

		if (committer != null) {
			d.append(UIText.CommitMessageViewer_committer);
			d.append(": "); //$NON-NLS-1$
			d.append(committer.getName());
			d.append(" <"); //$NON-NLS-1$
			d.append(committer.getEmailAddress());
			d.append("> "); //$NON-NLS-1$
			d.append(fmt.format(committer.getWhen()));
			d.append(LF);
		}

		for (int i = 0; i < commit.getParentCount(); i++) {
			final SWTCommit p = (SWTCommit)commit.getParent(i);
			p.parseBody();
			d.append(UIText.CommitMessageViewer_parent);
			d.append(": "); //$NON-NLS-1$
			addLink(d, styles, p);
			d.append(" ("); //$NON-NLS-1$
			d.append(p.getShortMessage());
			d.append(")"); //$NON-NLS-1$
			d.append(LF);
		}

		for (int i = 0; i < commit.getChildCount(); i++) {
			final SWTCommit p = (SWTCommit)commit.getChild(i);
			p.parseBody();
			d.append(UIText.CommitMessageViewer_child);
			d.append(": "); //$NON-NLS-1$
			addLink(d, styles, p);
			d.append(" ("); //$NON-NLS-1$
			d.append(p.getShortMessage());
			d.append(")"); //$NON-NLS-1$
			d.append(LF);
		}

		if(Activator.getDefault().getPreferenceStore().getBoolean(
				UIPreferences.HISTORY_SHOW_BRANCH_SEQUENCE)) {
			try (RevWalk rw = new RevWalk(db)) {
				List<Ref> branches = getBranches(commit, allRefs, db);
				if (!branches.isEmpty()) {
					d.append(UIText.CommitMessageViewer_branches);
					d.append(": "); //$NON-NLS-1$
					int count = 0;
					for (Iterator<Ref> i = branches.iterator(); i.hasNext();) {
						Ref head = i.next();
						RevCommit p;
						p = rw.parseCommit(head.getObjectId());
						addLink(d, formatHeadRef(head), styles, p);
						if (i.hasNext()) {
							if (count++ <= MAXBRANCHES) {
								d.append(", "); //$NON-NLS-1$
							} else {
								d.append(NLS.bind(UIText.CommitMessageViewer_MoreBranches, Integer.valueOf(branches.size() - MAXBRANCHES)));
								break;
							}
						}
					}
					d.append(LF);
				}
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}
		}

		String tagsString = getTagsString();
		if (tagsString.length() > 0) {
			d.append(UIText.CommitMessageViewer_tags);
			d.append(": "); //$NON-NLS-1$
			d.append(tagsString);
			d.append(LF);
		}

		if (Activator.getDefault().getPreferenceStore().getBoolean(
				UIPreferences.HISTORY_SHOW_TAG_SEQUENCE)) {
			try (RevWalk rw = new RevWalk(db)) {
				monitor.setTaskName(UIText.CommitMessageViewer_GettingPreviousTagTaskName);
				Ref followingTag = getNextTag(false, monitor);
				if (followingTag != null) {
					d.append(UIText.CommitMessageViewer_follows);
					d.append(": "); //$NON-NLS-1$
					RevCommit p = rw.parseCommit(followingTag
							.getObjectId());
					addLink(d, formatTagRef(followingTag), styles, p);
					d.append(LF);
				}
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}

			try (RevWalk rw = new RevWalk(db)) {
				monitor.setTaskName(UIText.CommitMessageViewer_GettingNextTagTaskName);
				Ref precedingTag = getNextTag(true, monitor);
				if (precedingTag != null) {
					d.append(UIText.CommitMessageViewer_precedes);
					d.append(": "); //$NON-NLS-1$
					RevCommit p = rw.parseCommit(precedingTag
							.getObjectId());
					addLink(d, formatTagRef(precedingTag), styles, p);
					d.append(LF);
				}
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}
		}

		makeGrayText(d, styles);
		d.append(LF);
		String msg = commit.getFullMessage();
		Pattern p = Pattern.compile("\n([A-Z](?:[A-Za-z]+-)+by: [^\n]+)"); //$NON-NLS-1$
		if (fill) {
			Matcher spm = p.matcher(msg);
			if (spm.find()) {
				String subMsg = msg.substring(0, spm.end());
				msg = subMsg.replaceAll("([\\w.,; \t])\n(\\w)", "$1 $2") //$NON-NLS-1$ //$NON-NLS-2$
						+ msg.substring(spm.end());
			}
		}
		int h0 = d.length();
		d.append(msg);
		if (!msg.endsWith(LF))
			d.append(LF);

		Matcher matcher = p.matcher(msg);
		while (matcher.find()) {
			styles.add(new StyleRange(h0 + matcher.start(), matcher.end()
					- matcher.start(), null, null, SWT.ITALIC));
		}

		if (trace)
			GitTraceLocation.getTrace().traceExit(
					GitTraceLocation.HISTORYVIEW.getLocation());
		return d.toString();
	}

	private void addLink(final StringBuilder d, String linkLabel,
			final List<StyleRange> styles, final RevCommit to) {
		final ObjectLink sr = new ObjectLink();
		sr.targetCommit = to;
		sr.foreground = linkColor;
		sr.underline = true;
		sr.start = d.length();
		d.append(linkLabel);
		sr.length = d.length() - sr.start;
		styles.add(sr);
	}

	private void addLink(final StringBuilder d, final List<StyleRange> styles,
			final RevCommit to) {
		addLink(d, to.getId().name(), styles, to);
	}

	/**
	 * @param commit
	 * @param allRefs
	 * @param db
	 * @return List of heads from those current commit is reachable
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	private static List<Ref> getBranches(RevCommit commit,
			Collection<Ref> allRefs, Repository db)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (RevWalk revWalk = new RevWalk(db)) {
			revWalk.setRetainBody(false);
			return RevWalkUtils.findBranchesReachableFrom(commit, revWalk, allRefs);
		}
	}

	private String formatHeadRef(Ref ref) {
		final String name = ref.getName();
		if (name.startsWith(Constants.R_HEADS))
			return name.substring(Constants.R_HEADS.length());
		else if (name.startsWith(Constants.R_REMOTES))
			return name.substring(Constants.R_REMOTES.length());
		return name;
	}

	private String formatTagRef(Ref ref) {
		final String name = ref.getName();
		if (name.startsWith(Constants.R_TAGS))
			return name.substring(Constants.R_TAGS.length());
		return name;
	}

	private void makeGrayText(StringBuilder d, List<StyleRange> styles) {
		int p0 = 0;
		for (int i = 0; i < styles.size(); ++i) {
			StyleRange r = styles.get(i);
			if (p0 < r.start) {
				StyleRange nr = new StyleRange(p0, r.start - p0, darkGrey,
						null);
				styles.add(i, nr);
				p0 = r.start;
			} else {
				if (r.foreground == null)
					r.foreground = darkGrey;
				p0 = r.start + r.length;
			}
		}
		if (d.length() - 1 > p0) {
			StyleRange nr = new StyleRange(p0, d.length() - p0, darkGrey,
					null);
			styles.add(nr);
		}
	}

	private String getTagsString() {
		StringBuilder sb = new StringBuilder();
		Map<String, Ref> tagsMap = db.getTags();
		for (Entry<String, Ref> tagEntry : tagsMap.entrySet()) {
			ObjectId target = tagEntry.getValue().getPeeledObjectId();
			if (target == null)
				target = tagEntry.getValue().getObjectId();
			if (target != null && target.equals(commit)) {
				if (sb.length() > 0)
					sb.append(", "); //$NON-NLS-1$
				sb.append(tagEntry.getKey());
			}
		}
		return sb.toString();
	}

	/**
	 * Finds next door tagged revision. Searches forwards (in descendants) or
	 * backwards (in ancestors)
	 *
	 * @param searchDescendant
	 *            if <code>false</code>, will search for tagged revision in
	 *            ancestors
	 * @param monitor
	 * @return {@link Ref} or <code>null</code> if no tag found
	 * @throws IOException
	 * @throws OperationCanceledException
	 */
	private Ref getNextTag(boolean searchDescendant, IProgressMonitor monitor)
			throws IOException, OperationCanceledException {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		try (RevWalk revWalk = new RevWalk(db)) {
			revWalk.setRetainBody(false);
			Map<String, Ref> tagsMap = db.getTags();
			Ref tagRef = null;

			for (Ref ref : tagsMap.values()) {
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				// both RevCommits must be allocated using same RevWalk
				// instance,
				// otherwise isMergedInto returns wrong result!
				RevCommit current = revWalk.parseCommit(commit);
				// tags can point to any object, we only want tags pointing at
				// commits
				RevObject any = revWalk
						.peel(revWalk.parseAny(ref.getObjectId()));
				if (!(any instanceof RevCommit))
					continue;
				RevCommit newTag = (RevCommit) any;
				if (newTag.getId().equals(commit))
					continue;

				// check if newTag matches our criteria
				if (isMergedInto(revWalk, newTag, current, searchDescendant)) {
					if (monitor.isCanceled())
						throw new OperationCanceledException();
					if (tagRef != null) {
						RevCommit oldTag = revWalk
								.parseCommit(tagRef.getObjectId());

						// both oldTag and newTag satisfy search criteria, so
						// taking
						// the closest one
						if (isMergedInto(revWalk, oldTag, newTag,
								searchDescendant))
							tagRef = ref;
					} else
						tagRef = ref;
				}
			}
			return tagRef;
		}
	}

	/**
	 * @param rw
	 * @param base
	 * @param tip
	 * @param swap
	 *            if <code>true</code>, base and tip arguments are swapped
	 * @return <code>true</code> if there is a path directly from tip to base
	 *         (and thus base is fully merged into tip); <code>false</code>
	 *         otherwise.
	 * @throws IOException
	 */
	private boolean isMergedInto(final RevWalk rw, final RevCommit base,
			final RevCommit tip, boolean swap) throws IOException {
		return !swap ? rw.isMergedInto(base, tip) : rw.isMergedInto(tip, base);
	}

}
