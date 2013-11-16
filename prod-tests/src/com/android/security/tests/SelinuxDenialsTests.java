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

package com.android.security.tests;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.loganalysis.item.BugreportItem;
import com.android.loganalysis.item.KernelLogItem;
import com.android.loganalysis.item.MiscKernelLogItem;
import com.android.loganalysis.parser.BugreportParser;
import com.android.loganalysis.parser.KernelLogParser;
import com.android.loganalysis.util.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tests that count how many SELinux Denials occurred.
 */
public class SelinuxDenialsTests implements IRemoteTest, IDeviceTest {

    private ITestDevice mDevice;
    private static final int TEST_FAILED = -1;
    private static final String ADB_SHELL_KERNEL_LOGS_CMD = "su -c dmesg";
    private static final String DMESG_OUTPUT_FILE_NAME = "dmesg_output";

    @Option(name = "kmsg-from-bugreport", description =
            "Obtain kernel logs from bugreport instead of adb shell.")
    private boolean mKmsgFromBugreport = false;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Runs the SelinuxDenialsCount test.
     *
     * @param listener The test invocation listener.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> runMetrics = new HashMap<String, String>(1);

        listener.testRunStarted("SELinux", 0);

        int result = getNumDenialsOnStartup(listener);
        // only append metrics to the run if the test passed
        if (result != TEST_FAILED) {
            runMetrics.put("SELinuxDenialsOnStartup", String.format("%d", result));
        }

        listener.testRunEnded(0, runMetrics);
    }

    /**
     * Count the number of SELinux denials that occurred since device startup - expecting 0.
     * This test case should be run first, directly after device flash & startup.
     *
     * @param listener The test invocation listener.
     * @return the count of SELinux denials, or TEST_FAILED upon failure to get the count
     * @throws DeviceNotAvailableException
     */
    private int getNumDenialsOnStartup(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        List<MiscKernelLogItem> selinuxLog = null;
        KernelLogItem kernelLogs;
        int denialsCount;

        /* retrieve the kernel logs */
        if (mKmsgFromBugreport) {
            kernelLogs = getKernelLogsFromBugreport(captureBugreport(listener));
        } else {
            kernelLogs = getKernelLogsFromAdbShell(listener);
        }
        if (kernelLogs == null) {
            listener.testRunFailed("failed to retrieve the kernel logs");
            return TEST_FAILED;
        }

        /* retrieve the selinux logs from the kernel logs */
        selinuxLog = kernelLogs.getMiscEvents(KernelLogParser.SELINUX_DENIAL);
        attachLogs(listener, "MiscKernelLogItem", LogDataType.KERNEL_LOG,
                constructKernelLogString(selinuxLog));
        if (selinuxLog == null) {
            listener.testRunFailed("failed to retrieve the selinux logs");
            return TEST_FAILED;
        }

        denialsCount = selinuxLog.size();
        CLog.i("SELinux logs: %d", denialsCount);
        return denialsCount;
    }

    /**
     * Gets the Kernel logs from adb shell.
     *
     * @param listener The test invocation listener - for capturing dmesg output upon failure.
     * @return the kernel logs, or null upon failure to retrieve the logs
     * @throws DeviceNotAvailableException
     */
    private KernelLogItem getKernelLogsFromAdbShell(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        final int MAX_TIME_MS = 2 * 60 * 1000;
        final int RETRIES = 3;

        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        CLog.i("executing adb shell %s", ADB_SHELL_KERNEL_LOGS_CMD);
        mDevice.executeShellCommand(ADB_SHELL_KERNEL_LOGS_CMD, receiver, MAX_TIME_MS,
                TimeUnit.MILLISECONDS, RETRIES);
        String shellOutputString = receiver.getOutput();

        // split the multi-line output into List<String> so it can be passed into KernelLogParser
        List<String> shellOutputStrings = new ArrayList<String>(Arrays.asList(
                shellOutputString.split("\\r?\\n")));

        KernelLogItem kernelLog = new KernelLogParser().parse(shellOutputStrings);
        if (!hasKernelLogs(kernelLog)) {
            attachLogs(listener, DMESG_OUTPUT_FILE_NAME, LogDataType.TEXT, shellOutputString);
            String errorMsg = "The dmesg output doesn't contain any properly formatted log lines";
            CLog.e("Error: %s. See attached log file: %s", errorMsg, DMESG_OUTPUT_FILE_NAME);
            return null;
        }

        return kernelLog;
    }

    /**
     * Gets a parsed BugreportItem from the given InputStreamSource.
     *
     * @param bugreportSource the InputStreamSource containing the bugreport
     * @return the parsed BugreportItem, or null upon failure.
     */
    private KernelLogItem getKernelLogsFromBugreport(InputStreamSource bugreportSource) {
        KernelLogItem kernelLog = null;

        try {
            BugreportItem bugreport = new BugreportParser().parse(new BufferedReader(
                        new InputStreamReader(bugreportSource.createInputStream())));

            kernelLog = bugreport.getKernelLog();
            if (!hasKernelLogs(kernelLog)) {
                CLog.e("The kernel log appears invalid, see the captured bugreport.");
                return null;
            }
        } catch (IOException e) {
            CLog.e(String.format("Failed to fetch and parse bugreport for device %s: %s",
                    mDevice.getSerialNumber(), e));
        } finally {
            bugreportSource.cancel();
        }

        return kernelLog;
    }

    /**
     * Verifies whether or not the specified KernelLogItem actually contains any valid Kernel Logs.
     *
     * @param kernelLog the kernel log item
     * @return true if the KernelLogItem contains any valid Kernel logs, false otherwise
     */
    private boolean hasKernelLogs(KernelLogItem kernelLog) {
        /*
         * When getStartTime() returns null, this means that KernelLogParser couldn't find any
         * properly formatted kernel log entries with timestamps. This should mean that the adb
         * shell cmd spit out an error, so we throw an exception here.
         */
        if (kernelLog == null || kernelLog.getStartTime() == null) {
            return false;
        }
        return true;
    }

    /**
     * Construct a list of strings out of the given list of kernel logs.
     *
     * @param selinuxLogs the list of kernel log items
     * @return list of strings, each constructed using kernel log attributes
     */
    private String constructKernelLogString (List<MiscKernelLogItem> kernelLogs) {
        /* construct and attach a log showing only SELinuxDenials */
        StringBuffer logStr = new StringBuffer();
        for (MiscKernelLogItem logItem : kernelLogs) {
            logStr.append((String.format("%f   %s\n",
                    logItem.getEventTime(),
                    logItem.getStack())));
        }
        return logStr.toString();
    }

    /**
     * Captures a bug report from the device and attaches the logs to the listener.
     *
     * @param listener The test invocation listener to capture the logs to.
     * @return the InputStreamSource of the captured bug report
     */
    private InputStreamSource captureBugreport(ITestInvocationListener listener) {
        CLog.i("capturing bugreport...");
        InputStreamSource bugreportSource = mDevice.getBugreport();
        listener.testLog("bugreport", LogDataType.BUGREPORT, bugreportSource);
        return bugreportSource;
    }

    /**
     * Passes the specified logs string to the listener, so they can be available to result
     * reporters. This is typically used to upload these logs to a test results dashboard.
     *
     * @param listener The test invocation listener to attach the logs to.
     * @param dataName the name of the logs being attached
     * @param dataType the data type of the log, ie. LogDataType.KERNEL_LOG
     * @param logsStr the multi-line string containing logs
     */
    private void attachLogs(ITestInvocationListener listener, String dataName,
            LogDataType dataType, String logsStr) {
        if (logsStr.isEmpty())
            // only attaching logsStr if there's any content in it
            return;

        // in order to attach logs to the listener, they need to be of type InputStreamSource
        InputStreamSource logsInputStream = new ByteArrayInputStreamSource(logsStr.getBytes());
        // attach logs to listener, so the logs will be available to result reporters
        listener.testLog(dataName, dataType, logsInputStream);
        // cleanup the InputStreamSource
        logsInputStream.cancel();
    }
}
