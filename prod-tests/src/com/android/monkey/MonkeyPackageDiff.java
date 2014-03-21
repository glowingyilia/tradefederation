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

package com.android.monkey;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.google.common.base.Joiner;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the list of packages the monkey can run against a golden file and fails on diffs.
 */
public class MonkeyPackageDiff implements IDeviceTest, IRemoteTest {
    private static final String TEST_KEY = "MonkeyPackageDiff";
    private static final String MONKEY_CMD = "monkey -v -v -v %s0";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^//\\s+\\+ [^\\(]+\\(from package ([^\\)]+)\\)$");

    @Option(name = "category", description = "Monkey app category. May be repeated.")
    private Collection<String> mCategories = new LinkedList<String>();

    @Option(name = "golden-file", description = "The golden file containing the list of packages",
            importance = Importance.ALWAYS, mandatory = true)
    private File mGoldenFile = null;

    @Option(name = "ignore-package", description = "Package name to ignore. May be repeated.")
    private Collection<String> mIgnoreList = new HashSet<String>();

    ITestDevice mDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());

        Set<String> expectedPackages = null;
        try {
            expectedPackages = readGoldenFile();
        } catch (IOException e) {
            Assert.fail("Could not read golden file");
        }

        StringBuilder categories = new StringBuilder();
        for (String category : mCategories) {
            categories.append("-c ").append(category).append(" ");
        }
        String cmd = String.format(MONKEY_CMD, categories.toString());
        String output = getDevice().executeShellCommand(cmd);

        SortedSet<String> actualPackages = new TreeSet<String>();
        for (String line : output.split("\n")) {
            line = line.trim();
            Matcher m = PACKAGE_PATTERN.matcher(line);
            if (m.matches()) {
                actualPackages.add(m.group(1));
            }
        }

        StringBuilder packageList = new StringBuilder();
        for (String pack : actualPackages) {
            packageList.append(pack).append("\n");
        }
        listener.testLog("packages", LogDataType.TEXT,
                new ByteArrayInputStreamSource(packageList.toString().getBytes()));

        SortedSet<String> addedPackages = new TreeSet<String>();
        for (String pack : actualPackages) {
            if (!expectedPackages.contains(pack) && !mIgnoreList.contains(pack)) {
                addedPackages.add(pack);
            }
        }
        SortedSet<String> removedPackages = new TreeSet<String>();
        for (String pack : expectedPackages) {
            if (!actualPackages.contains(pack) && !mIgnoreList.contains(pack)) {
                removedPackages.add(pack);
            }
        }

        reportMetrics(listener, addedPackages, removedPackages);
    }

    /**
     * Report the metrics and fail the tests if any packages are added or removed.
     */
    private void reportMetrics(ITestInvocationListener listener, Set<String> addedPackages,
            Set<String> removedPackages) {
        listener.testRunStarted(TEST_KEY, 0);
        Map<String, String> metrics = new HashMap<String, String>();
        Map<String, String> emptyMap = Collections.emptyMap();

        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), "added");
        listener.testStarted(testId);
        metrics.put("added", Integer.toString(addedPackages.size()));
        if (!addedPackages.isEmpty()) {
            String message = String.format("Added packages: %s",
                    Joiner.on(", ").join(addedPackages));
            listener.testFailed(TestFailure.FAILURE, testId, message);
        }
        listener.testEnded(testId, emptyMap);

        testId = new TestIdentifier(getClass().getCanonicalName(), "removed");
        listener.testStarted(testId);
        metrics.put("removed", Integer.toString(removedPackages.size()));
        if (!removedPackages.isEmpty()) {
            String message = String.format("Removed packages: %s",
                    Joiner.on(", ").join(removedPackages));
            listener.testFailed(TestFailure.FAILURE, testId, message);
        }
        listener.testEnded(testId, emptyMap);

        CLog.d("About to report monkey package diff metrics: %s", metrics);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Read the golden file and return a set of strings.
     */
    private Set<String> readGoldenFile() throws IOException {
        Set<String> packages = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new FileReader(mGoldenFile));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!"".equals(line)) {
                    packages.add(line);
                }
            }
        } finally {
            reader.close();
        }
        return packages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

}
