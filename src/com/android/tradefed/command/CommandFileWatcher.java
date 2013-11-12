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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A simple class to watch a set of command files for changes, and to trigger a
 * reload of _all_ manually-loaded command files when such a change happens.
 */
class CommandFileWatcher extends Thread {
    private static final long POLL_TIME_MS = 20 * 1000;  // 20 seconds
    private List<CommandFile> mCmdFiles = new LinkedList<CommandFile>();
    private ICommandScheduler mScheduler = null;
    boolean mCancelled = false;

    /**
     * A simple struct to store a command file as well as its extra args
     */
    private static class CommandFile {
        public final File file;
        public final long modTime;
        public final List<String> extraArgs;
        public final List<CommandFile> dependencies;

        /**
         * Construct a CommandFile with no arguments and no dependencies
         *
         * @param cmdFile a {@link File} representing the command file path
         */
        public CommandFile(File cmdFile) {
            if (cmdFile == null) throw new NullPointerException();

            this.file = cmdFile;
            this.modTime = cmdFile.lastModified();

            this.extraArgs = Collections.emptyList();
            this.dependencies = Collections.emptyList();
        }

        /**
         * Construct a CommandFile
         *
         * @param cmdFile a {@link File} representing the command file path
         * @param extraArgs A {@link List} of extra arguments that should be
         *        used when the command is rerun.
         * @param dependencies The command files that this command file
         *        requires as transitive dependencies.  A change in any of the
         *        dependencies will trigger a reload, but none of the
         *        dependencies themselves will be reloaded directly, only the
         *        main command file, {@code cmdFile}.
         */
        public CommandFile(File cmdFile, List<String> extraArgs, File[] dependencies) {
            if (cmdFile == null) throw new NullPointerException();

            this.file = cmdFile;
            this.modTime = cmdFile.lastModified();

            if (extraArgs == null) {
                this.extraArgs = Collections.emptyList();
            } else {
                this.extraArgs = extraArgs;
            }
            if (dependencies == null) {
                this.dependencies = Collections.emptyList();
            } else {
                this.dependencies = new ArrayList<CommandFile>(dependencies.length);

                for (File dep : dependencies) {
                    this.dependencies.add(new CommandFile(dep));
                }
            }
        }
    }

    public CommandFileWatcher(ICommandScheduler cmdScheduler) {
        super("CommandFileWatcher");  // set the thread name
        mScheduler = cmdScheduler;
        setDaemon(true);  // Don't keep the JVM alive for this thread
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void run() {
        while (!isCancelled()) {
            if (checkForUpdates()) {
                reloadCmdFiles();
            }
            getRunUtil().sleep(POLL_TIME_MS);
        }
    }

    /**
     * Add a command file to watch, as well as its dependencies.  When either
     * the command file itself or any of its dependencies changes, that triggers
     * us to forget everthing and reload the command file, which will
     * automatically cause us to repopulate.
     */
    public void addCmdFile(File cmdFile, List<String> extraArgs, File... dependencies) {

        final CommandFile cmd = new CommandFile(cmdFile, extraArgs, dependencies);

        mCmdFiles.add(cmd);
    }

    /**
     * Terminate the watcher thread
     */
    public void cancel() {
        mCancelled = true;
    }

    /**
     * Check if the thread has been signalled to stop.
     */
    public boolean isCancelled() {
        return mCancelled;
    }

    /**
     * Actually do the work of reloading the command files.  This includes
     * telling the {@link ICommandScheduler} to remove all commands, forgetting
     * about them ourselves, and then finally reloading everything.
     */
    private void reloadCmdFiles() {
        CLog.w("Auto-reloading all command files");
        mScheduler.removeAllCommands();

        final List<CommandFile> cmdFilesCopy = new ArrayList<CommandFile>(mCmdFiles);
        mCmdFiles.clear();

        for (CommandFile cmd : cmdFilesCopy) {
            final File file = cmd.file;
            final List<String> extraArgs = cmd.extraArgs;
            try {
                if (extraArgs.isEmpty()) {
                    createCommandFileParser().parseFile(file, mScheduler);
                } else {
                    createCommandFileParser().parseFile(file, mScheduler, extraArgs);
                }
            } catch (IOException e) {
                CLog.wtf("Failed to automatically reload cmdfile", e);
            } catch (ConfigurationException e) {
                CLog.wtf("Failed to automatically reload cmdfile", e);
            }
        }
    }

    /**
     * Poll the filesystem to see if any of the files of interest have
     * changed
     * <p />
     * Exposed for unit testing
     */
    boolean checkForUpdates() {
        final Set<File> checkedFiles = new HashSet<File>();

        for (CommandFile cmd : mCmdFiles) {
            if (checkCommandFileForUpdate(cmd, checkedFiles)) {
                // The command file or one of its dependencies has been updated
                return true;
            }
        }

        // Nothing changed.
        return false;
    }

    boolean checkCommandFileForUpdate(CommandFile cmd, Set<File> checkedFiles) {
        if (checkedFiles.contains(cmd.file)) {
            return false;
        } else {
            checkedFiles.add(cmd.file);
        }

        final long curModTime = cmd.file.lastModified();
        if (curModTime == 0L) {
            // File doesn't exist, or had an IO error.  Don't do anything.  If a change occurs
            // that we should pay attention to, then we'll see the file actually updated, which
            // implies that the modtime will be non-zero and will also be different from what
            // we stored before.
        } else if (curModTime != cmd.modTime) {
            // Note that we land on this case if the original modtime was 0 and the modtime is
            // now non-zero, so there's a race-condition if an IO error causes us to fail to
            // read the modtime initially.  This should be okay.
            CLog.w("Found update in monitored cmdfile %s (%d -> %d)", cmd.file, cmd.modTime,
                    curModTime);
            return true;
        }

        // Now check dependencies
        for (CommandFile dep : cmd.dependencies) {
            if (checkCommandFileForUpdate(dep, checkedFiles)) {
                // dependency changed
                return true;
            }
        }

        // We didn't change, and nor did any of our dependencies
        return false;
    }

    /**
     * Factory method for creating a {@link CommandFileParser}.
     * <p/>
     * Exposed for unit testing.
     */
    CommandFileParser createCommandFileParser() {
        return new CommandFileParser();
    }

    /**
     * Utility method to fetch the default {@link IRunUtil} singleton
     * <p />
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
