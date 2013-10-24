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
 * A harness that launches VellamoBenchmark and reports result. Requires VellamoBenchmark apk.
 */
public class VellamoBenchmark implements IDeviceTest, IRemoteTest {

  /**
   * TEST chapter names
   */
  private static final String METAL = "Metal";
  private static final String HTML5 = "HTML5";
  private static final String ALL = "ALL";


  private static final String LOGTAG = "VAUTOMATIC";
  private static final String RUN_KEY = "vellamobenchmark";
  private static final long TIMEOUT_MS = 30 * 60 * 1000;
  private static final long POLLING_INTERVAL_MS = 5 * 1000;
  private static final int MAX_ATTEMPTS = 3;

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

    int attempts = 0;
    boolean isTimedOut = false;
    boolean isRunningBenchmark = false;
    boolean isResultGenerated = false;

    while (!isResultGenerated && attempts < MAX_ATTEMPTS) {
      device.clearErrorDialogs();
      isTimedOut = false;
      long benchmarkStartTime = System.currentTimeMillis();
      // start the vellamo benchmark app and select an chapter (html5 or metal)
      CLog.i("Starting Benchmark");
      device.executeShellCommand("am start -a com.quicinc.vellamo.AUTOMATIC"
          + " -n com.quicinc.vellamo/.VellamoActivity -e c " + ALL);
      isRunningBenchmark = true;
      String line;
      while (isRunningBenchmark && !isResultGenerated && !isTimedOut) {
        RunUtil.getDefault().sleep(POLLING_INTERVAL_MS);
        isTimedOut = (System.currentTimeMillis() - benchmarkStartTime >= TIMEOUT_MS);
        isRunningBenchmark = device.executeShellCommand("ps").contains("com.quicinc.vellamo");

        // get the logcat and parse
        BufferedReader logcat =
            new BufferedReader(new InputStreamReader(device.getLogcat().createInputStream()));
        try {
          while ((line = logcat.readLine()) != null) {
            // filter only output from the Vellamo process
            if (!line.contains(LOGTAG)) {
              continue;
            }
            line = line.substring(line.indexOf(LOGTAG) + LOGTAG.length());
            // if we see </automatic>, we know the test is over
            if (line.contains("</automatic>")) {
              isResultGenerated = true;
              break;
            }
            // get the score out
            if (line.contains(" c: ")) {
              String[] results = line.split(" c: ")[1].split(",");
              metrics.put(results[0], results[1]);
              CLog.i("%s :: %s", results[0], results[1]);
            }
          }
        } catch (IOException e) {
          CLog.e(e);
        }
      }
      attempts++;
    }

    if (attempts >= MAX_ATTEMPTS) {
      errMsg = String.format("vellamo failed after %d attempts.", MAX_ATTEMPTS);
    } else if (isTimedOut) {
      errMsg = "vellamo timed out.";
    } else {
      CLog.i("== VellamoBenchmark result ends ==");
    }

    if (errMsg != null) {
      CLog.e(errMsg);
      listener.testFailed(TestFailure.FAILURE, testId, errMsg);
    }
    long durationMs = System.currentTimeMillis() - testStartTime;
    listener.testEnded(testId, metrics);
    listener.testRunEnded(durationMs, metrics);
  }

}
