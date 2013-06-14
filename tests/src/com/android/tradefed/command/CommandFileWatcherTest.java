/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.command;

import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link CommandFileWatcher}.  Mocks all filesystem accesses.
 */
public class CommandFileWatcherTest extends TestCase {
    private TestCommandFileWatcher mWatcher = null;
    private ICommandScheduler mMockScheduler = null;
    private TestCommandFileParser mParser = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mParser = new TestCommandFileParser();
        mMockScheduler = EasyMock.createStrictMock(ICommandScheduler.class);
        mWatcher = new TestCommandFileWatcher(mMockScheduler);
    }

    /**
     * Make sure we get a parse attempt if the mod time changes immediately
     * after we start running
     */
    public void testImmediateChange() throws Exception {
        final File cmdFile = new ModFile("/a/path/too/far", 1, 2, 2);
        mWatcher.addCmdFile(cmdFile, null);
        mMockScheduler.removeAllCommands();  // EasyMock expectation
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 3;
        mWatcher.run();
        assertEquals(2, mWatcher.totalRunCount);
        assertEquals(1, mParser.parseRequests.size());
        assertEquals(cmdFile, mParser.parseRequests.get(0));

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure we get a parse attempt if the mod time changes after a little
     * while.
     */
    public void testDelayedChange() throws Exception {
        final File cmdFile = new ModFile("/a/path/too/far", 1, 1, 1, 1, 1, 1, 1, 2, 2);
        mWatcher.addCmdFile(cmdFile, null);
        mMockScheduler.removeAllCommands();  // EasyMock expectation
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 10;
        mWatcher.run();
        assertEquals(8, mWatcher.totalRunCount);
        assertEquals(1, mParser.parseRequests.size());
        assertEquals(cmdFile, mParser.parseRequests.get(0));

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure we _don't_ get a parse attempt if the mod time never changes.
     */
    public void testNoChange() throws Exception {
        final File cmdFile = new ModFile("/a/path/too/far", 1, 1, 1, 1, 1, 1, 1, 1);
        mWatcher.addCmdFile(cmdFile, null);
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 8;
        mWatcher.run();
        assertEquals(8, mWatcher.totalRunCount);
        assertEquals(0, mParser.parseRequests.size());

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that we behave properly when watching multiple primary command
     * files.  This means that we should reload both, regardless of which
     * command file changed.
     */
    public void testMultipleCmdFiles() throws Exception {
        final File cmdFile1 = new ModFile("/went/too/far", 1, 1, 1, 1);
        final File cmdFile2 = new ModFile("/knew/too/much", 1, 1, 2, 2);
        mWatcher.addCmdFile(cmdFile1, null);
        mWatcher.addCmdFile(cmdFile2, null);
        mMockScheduler.removeAllCommands();  // EasyMock expectation
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 5;
        mWatcher.run();
        assertEquals(3, mWatcher.totalRunCount);

        assertEquals(2, mParser.parseRequests.size());
        assertEquals(cmdFile1, mParser.parseRequests.get(0));
        assertEquals(cmdFile2, mParser.parseRequests.get(1));

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that we behave properly when watching a primary command file as
     * well as its dependencies.  In this case, we should only reload the
     * primary command files, even though only the dependencies changed.
     */
    public void testDependencies() throws Exception {
        final File cmdFile1 = new ModFile("/went/too/far", 1, 1, 1, 1);
        final File cmdFile2 = new ModFile("/knew/too/much", 1, 1, 1, 1);
        final File dependent = new ModFile("/those/are/my/lines", 1, 1, 2, 2);
        mWatcher.addCmdFile(cmdFile1, null, dependent);
        mWatcher.addCmdFile(cmdFile2, null);
        mMockScheduler.removeAllCommands();  // EasyMock expectation
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 5;
        mWatcher.run();
        assertEquals(3, mWatcher.totalRunCount);

        assertEquals(2, mParser.parseRequests.size());
        assertEquals(cmdFile1, mParser.parseRequests.get(0));
        assertEquals(cmdFile2, mParser.parseRequests.get(1));

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that we behave properly when watching a primary command file as
     * well as its dependencies.  In this case, we should only reload the
     * primary command files, even though only the dependencies changed.
     */
    public void testMultipleDependencies() throws Exception {
        final File cmdFile1 = new ModFile("/went/too/far", 1, 1, 1, 1);
        final File cmdFile2 = new ModFile("/knew/too/much", 1, 1, 1, 1);
        final File dep1 = new ModFile("/those/are/my/lines", 1, 1, 2, 2);
        final File dep2 = new ModFile("/ceci/n'est/pas/une/line", 1, 1, 1, 1);
        mWatcher.addCmdFile(cmdFile1, null, dep1, dep2);
        mWatcher.addCmdFile(cmdFile2, null, dep2);
        mMockScheduler.removeAllCommands();  // EasyMock expectation
        EasyMock.replay(mMockScheduler);

        mWatcher.run();
        mWatcher.maxRunCount = 10;
        assertEquals(3, mWatcher.totalRunCount);

        assertEquals(2, mParser.parseRequests.size());
        assertEquals(cmdFile1, mParser.parseRequests.get(0));
        assertEquals(cmdFile2, mParser.parseRequests.get(1));

        EasyMock.verify(mMockScheduler);
    }

    /**
     * Since CommandFileWatcher is a singleton, make sure that we behave
     * properly for multiple cycles of reloads.
     */
    public void testMultipleCycles() throws Exception {
        final File cmdFile1 = new ModFile("/went/too/far", 1, 1, 1, 1, 1, 1, 1);
        final File cmdFile2 = new ModFile("/knew/too/much", 1, 1, 2, 2, 2, 3, 3);
        mWatcher.addCmdFile(cmdFile1, null);
        mWatcher.addCmdFile(cmdFile2, null);
        mMockScheduler.removeAllCommands();
        EasyMock.expectLastCall().times(2);
        EasyMock.replay(mMockScheduler);

        mWatcher.maxRunCount = 10;

        mWatcher.run();
        assertEquals(3, mWatcher.totalRunCount);
        assertEquals(2, mParser.parseRequests.size());
        assertEquals(cmdFile1, mParser.parseRequests.get(0));
        assertEquals(cmdFile2, mParser.parseRequests.get(1));

        mWatcher.restart();
        mWatcher.run();
        assertEquals(6, mWatcher.totalRunCount);
        assertEquals(4, mParser.parseRequests.size());
        assertEquals(cmdFile1, mParser.parseRequests.get(2));
        assertEquals(cmdFile2, mParser.parseRequests.get(3));

        EasyMock.verify(mMockScheduler);
    }


    private class TestCommandFileParser extends CommandFileParser {
        public List<File> parseRequests = new ArrayList<File>();

        /**
         * Allow us to verify requests to parse command files without actually running the
         * parser.
         * <p />
         * Note that we have no ability to restore test expectations of dependencies.
         */
        @Override
        public void parseFile(File file, ICommandScheduler scheduler, List<String> extraArgs) {
            parseRequests.add(file);
            mWatcher.addCmdFile(file, extraArgs);
        }
    };

    private class TestCommandFileWatcher extends CommandFileWatcher {
        public int maxRunCount = 10;
        public int totalRunCount = 0;

        public TestCommandFileWatcher(ICommandScheduler scheduler) {
            super(scheduler);
        }

        /**
         * Cancel if checkForUpdates returns true
         */
        @Override
        boolean checkForUpdates() {
            if (super.checkForUpdates()) {
                cancel();
                return true;
            } else {
                return false;
            }
        }

        /**
         * Enable tests to cancel on demand to prevent Watcher from running
         * away in case of bugs
         */
        @Override
        public boolean isCancelled() {
            maxRunCount--;
            totalRunCount++;
            return super.isCancelled() || maxRunCount <= 0;
        }

        /**
         * Reset the value of mCancelled so we can continue to step through the
         * {@link CommandFileWatcher} functionality
         */
        public void restart() {
            mCancelled = false;
        }

        /**
         * Disable sleeping
         */
        @Override
        IRunUtil getRunUtil() {
            return EasyMock.createNiceMock(IRunUtil.class);
        }

        /**
         * Use our specially-prepared CommandFileParser
         */
        @Override
        CommandFileParser createCommandFileParser() {
            return mParser;
        }
    };

    /**
     * A File extension that allows a list of modtimes to be set.
     */
    @SuppressWarnings("serial")
    private static class ModFile extends File {
        private long[] mModTimes = null;
        private int mCurrentIdx = 0;

        public ModFile(String path, long... modTimes) {
            super(path);
            mModTimes = modTimes;
        }

        @Override
        public long lastModified() {
            if (mCurrentIdx >= mModTimes.length) {
                throw new IllegalStateException("Unexpected call to #lastModified");
            }
            return mModTimes[mCurrentIdx++];
        }
    }
}
