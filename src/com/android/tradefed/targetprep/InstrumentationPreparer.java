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

package com.android.tradefed.targetprep;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestResult.TestStatus;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.RunUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ITargetPreparer} that runs instrumentation
 */
@OptionClass(alias = "instrumentation-preparer")
public class InstrumentationPreparer implements ITargetPreparer {

    @Option(name = "disable", description = "disables the instrumentation runner")
    private boolean mDisable = false;

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run.",
            importance = Importance.IF_UNSET)
    private String mPackageName = null;

    @Option(name = "runner",
            description="The instrumentation test runner class name to use.")
    private String mRunnerName = "android.test.InstrumentationTestRunner";

    @Option(name = "class", shortName = 'c',
            description="The test class name to run.")
    private String mClassName = null;

    @Option(name = "method", shortName = 'm',
            description="The test method name to run.")
    private String mMethodName = null;

    @Option(name = "timeout",
            description="Aborts the test run if any test takes longer than the specified number of "
            + "milliseconds. For no timeout, set to 0.")
    private int mTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "instrumentation-arg",
            description = "Instrumentation arguments to provide.")
    private Map<String, String> mInstrArgMap = new HashMap<String, String>();

    @Option(name = "attempts",
            description =
            "The max number of attempts to make to run the instrumentation successfully.")
    private int mAttempts = 1;

    @Option(name = "delay-before-retry",
            description = "Time to delay before retrying another instrumentation attempt, in msecs")
    private long mRetryDelayMs = 0;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        if (mDisable) {
            return;
        }

        BuildError e = new BuildError("unknown error");
        for (int i = 0; i < mAttempts; i++) {
            try {
                runInstrumentation(device);
                return;
            } catch (BuildError e1) {
                e = e1;
                CLog.d("sleeping %d msecs on device %s before retrying instrumentation test run",
                        mRetryDelayMs, device.getSerialNumber());
                RunUtil.getDefault().sleep(mRetryDelayMs);
            }
        }
        // all attempts failed!
        throw e;
    }

    private void runInstrumentation(ITestDevice device) throws DeviceNotAvailableException,
            BuildError {
        final InstrumentationTest test = createInstrumentationTest();
        test.setDevice(device);
        test.setPackageName(mPackageName);
        test.setRunnerName(mRunnerName);
        test.setClassName(mClassName);
        test.setMethodName(mMethodName);
        test.setTestTimeout(mTimeout);
        for (Map.Entry<String, String> entry : mInstrArgMap.entrySet()) {
            test.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        final CollectingTestListener listener = new CollectingTestListener();
        test.run(listener);
        if (listener.hasFailedTests()) {
            String msg = String.format("Failed to run instrumentation %s on %s. failed tests = %s",
                    mPackageName, device.getSerialNumber(), getFailedTestNames(listener));
            CLog.w(msg);
            throw new BuildError(msg);
        }
    }

    private String getFailedTestNames(CollectingTestListener listener) {
        final StringBuilder builder = new StringBuilder();
        for (TestRunResult result : listener.getRunResults()) {
            if (!result.hasFailedTests()) {
                continue;
            }
            for (Map.Entry<TestIdentifier, TestResult> entry : result.getTestResults().entrySet()) {
                if (entry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    continue;
                }

                if (0 < builder.length()) {
                    builder.append(",");
                }
                builder.append(entry.getKey());
            }
        }
        return builder.toString();
    }

    InstrumentationTest createInstrumentationTest() {
        return new InstrumentationTest();
    }

    void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    void setRunnerName(String runnerName) {
        mRunnerName = runnerName;
    }

    void setClassName(String className) {
        mClassName = className;
    }

    void setMethodName(String methodName) {
        mMethodName = methodName;
    }

    void setTimeout(int timeout) {
        mTimeout = timeout;
    }

    void setAttempts(int attempts) {
        mAttempts = attempts;
    }

    void setRetryDelay(int delayMs) {
        mRetryDelayMs = delayMs;
    }
}
