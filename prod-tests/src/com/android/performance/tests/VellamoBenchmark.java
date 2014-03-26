/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.performance.tests;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * A harness that launches VellamoBenchmark and reports result. Requires
 * VellamoBenchmark apk.
 */
public class VellamoBenchmark implements IDeviceTest, IRemoteTest {

    private static final String LOGTAG = "VAUTOMATIC";
    private static final String RUN_KEY = "vellamobenchmark";
    private static final long TIMEOUT_MS = 30 * 60 * 1000;
    private static final long POLLING_INTERVAL_MS = 10 * 1000;

    private ITestDevice mDevice;

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), RUN_KEY);
        ITestDevice device = getDevice();
        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();
        String errMsg = null;

        boolean isTimedOut = false;
        boolean isRunningBenchmark = false;
        boolean isResultGenerated = false;
        boolean hasScore = false;
        double sumScore = 0;
        device.clearErrorDialogs();
        isTimedOut = false;

        long benchmarkStartTime = System.currentTimeMillis();
        // start the vellamo benchmark app and run all the tests
        // the documentation and binary for the Vellamo 2.0.x for Automation APK
        // can be found here:
        // https://b.corp.google.com/issue?id=5035578
        CLog.i("Starting Benchmark");
        device.executeShellCommand("am start -a com.quicinc.vellamo.AUTOMATIC"
                + " -n com.quicinc.vellamo/.VellamoActivity");
        isRunningBenchmark = true;
        String line;
        while (isRunningBenchmark && !isResultGenerated && !isTimedOut) {
            RunUtil.getDefault().sleep(POLLING_INTERVAL_MS);
            isTimedOut = (System.currentTimeMillis() - benchmarkStartTime >= TIMEOUT_MS);
            isRunningBenchmark = device.executeShellCommand("ps").contains(
                    "com.quicinc.vellamo");

            // get the logcat and parse
            BufferedReader logcat =
                    new BufferedReader(
                            new InputStreamReader(device.getLogcat().createInputStream()));
            try {
                while ((line = logcat.readLine()) != null) {
                    // filter only output from the Vellamo process
                    if (!line.contains(LOGTAG)) {
                        continue;
                    }
                    line = line.substring(line.indexOf(LOGTAG) + LOGTAG.length());
                    // we need to see if the score is generated since there are some
                    // cases the result with </automatic> tag is generated but no score is included
                    if (line.contains("</automatic>")) {
                        if(hasScore){
                            isResultGenerated = true;
                            break;
                        }
                    }
                    // get the score out
                    if (line.contains(" b: ")) {
                        hasScore = true;
                        String[] results = line.split(" b: ")[1].split(",");
                        sumScore += Double.parseDouble(results[3]);
                        metrics.put(results[0], results[3]);
                        CLog.i("%s :: %s", results[0], results[3]);
                    }
                }
            } catch (IOException e) {
                CLog.e(e);
            }
        }

        if (isTimedOut) {
            errMsg = "vellamo timed out.";
        } else {
            CLog.i("== VellamoBenchmark result ends ==");
        }
        if (!hasScore) {
            errMsg = "Test ended but no scores can be found.";
        }
        if (errMsg != null) {
            CLog.e(errMsg);
            listener.testFailed(TestFailure.FAILURE, testId, errMsg);
        }
        long durationMs = System.currentTimeMillis() - testStartTime;
        metrics.put("total", Double.toString(sumScore));
        CLog.i("total :: %f", sumScore);
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }
}
