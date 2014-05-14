/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tradefed.util;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Callable} that wraps the details of executing shell command on an {@link ITestDevice}.
 * <p>
 * Must implement {@link #processOutput(String)} to process the command output and determine return
 * of the <code>Callable</code>
 * @param <V> passthru of the {@link Callable} return type, see {@link Callable}
 */
public abstract class ShellCommandCallable<V> implements Callable<V> {
    private String mCommand;
    private long mTimeout;
    private ITestDevice mDevice;

    public ShellCommandCallable() {
        // do nothing for default
    }

    public ShellCommandCallable(ITestDevice device, String command, long timeout) {
        this();
        mCommand = command;
        mTimeout = timeout;
        mDevice = device;
    }

    public ShellCommandCallable<V> setCommand(String command) {
        mCommand = command;
        return this;
    }

    public ShellCommandCallable<V> setTimeout(long timeout) {
        mTimeout = timeout;
        return this;
    }

    public ShellCommandCallable<V> setDevice(ITestDevice device) {
        mDevice = device;
        return this;
    }

    @Override
    public V call() throws Exception {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(mCommand, receiver, mTimeout, TimeUnit.MILLISECONDS, 1);
        String output = receiver.getOutput();
        CLog.v("raw output for \"%s\"\n%s", mCommand, output);
        return processOutput(output);
    }

    public abstract V processOutput(String output);
}
