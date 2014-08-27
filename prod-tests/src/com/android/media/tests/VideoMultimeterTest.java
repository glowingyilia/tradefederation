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

package com.android.media.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A harness that test video playback and reports result.
 */
public class VideoMultimeterTest implements IDeviceTest, IRemoteTest {

    private static final String RUN_KEY = "video_multimeter";

    @Option(name = "multimeter-util-path", description = "path for multimeter control util",
            importance = Importance.ALWAYS)
    private String mUtilPath = "/tmp/util.sh";

    private static final String START_VIDEO_PLAYER = "am start"
            + " -a android.intent.action.VIEW -t video/mp4 -d \"file://%s\""
            + " -n \"com.google.android.apps.plus/.phone.VideoViewActivity\"";
    private static final String KILL_VIDEO_PLAYER = "am force-stop com.google.android.apps.plus";
    private static final String ROTATE_LANDSCAPE = "content insert --uri content://settings/system"
            + " --bind name:s:user_rotation --bind value:i:1";

    private static final String VIDEO_DIR = "/sdcard/DCIM/Camera/";

    private static final String CALI_VIDEO_DEVICE_PATH = VIDEO_DIR + "video_cali.mp4";

    private static final String TEST_VIDEO_1_DEVICE_PATH = VIDEO_DIR + "video.mp4";
    private static final String TEST_VIDEO_1_PREFIX = "bbb_";
    private static final long TEST_VIDEO_1_DURATION = 11 * 60; // in second

    private static final String TEST_VIDEO_2_DEVICE_PATH = VIDEO_DIR + "video2.mp4";
    private static final String TEST_VIDEO_2_PREFIX = "60fps_";
    private static final long TEST_VIDEO_2_DURATION = 5 * 60; // in second

    private static final String CMD_GET_FRAMERATE_STATE = "GETF";
    private static final String CMD_START_CALIBRATION = "STAC";
    private static final String CMD_STOP_CALIBRATION = "STOC";
    private static final String CMD_START_MEASUREMENT = "STAM";
    private static final String CMD_STOP_MEASUREMENT = "STOM";
    private static final String CMD_GET_NUM_FRAMES = "GETN";
    private static final String CMD_GET_ALL_DATA = "GETD";

    private static final long DEVICE_SYNC_TIME_MS = 30 * 1000;
    private static final long CALIBRATION_TIMEOUT_MS = 30 * 1000;
    private static final long COMMAND_TIMEOUT_MS = 5 * 1000;
    private static final long GETDATA_TIMEOUT_MS = 10 * 60 * 1000;

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

    private void rotateScreen() throws DeviceNotAvailableException {
        // rotate to landscape mode, except for manta
        if (!getDevice().getProductType().contains("manta")) {
            getDevice().executeShellCommand(ROTATE_LANDSCAPE);
        }
    }

    private boolean setupTestEnv() throws DeviceNotAvailableException {
        getRunUtil().sleep(DEVICE_SYNC_TIME_MS);
        CommandResult cr = getRunUtil().runTimedCmd(
                COMMAND_TIMEOUT_MS, mUtilPath, CMD_STOP_MEASUREMENT);

        getDevice().setDate(new Date());
        CLog.i("syncing device time to host time");
        getRunUtil().sleep(3 * 1000);

        // start and stop to clear old data
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_START_MEASUREMENT);
        getRunUtil().sleep(3 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_STOP_MEASUREMENT);
        getRunUtil().sleep(3 * 1000);
        CLog.i("Stopping measurement: " + cr.getStdout());
        getDevice().unlockDevice();
        getRunUtil().sleep(3 * 1000);

        // play calibration video
        getDevice().executeShellCommand(String.format(START_VIDEO_PLAYER, CALI_VIDEO_DEVICE_PATH));
        getRunUtil().sleep(3 * 1000);
        rotateScreen();
        getRunUtil().sleep(1 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_START_CALIBRATION);
        CLog.i("Starting calibration: " + cr.getStdout());

        // check whether multimeter is calibrated
        boolean isCalibrated = false;
        long calibrationStartTime = System.currentTimeMillis();
        while (!isCalibrated
                && System.currentTimeMillis() - calibrationStartTime <= CALIBRATION_TIMEOUT_MS) {
            getRunUtil().sleep(1 * 1000);
            cr = getRunUtil().runTimedCmd(2 * 1000, mUtilPath, CMD_GET_FRAMERATE_STATE);
            if (cr.getStdout().contains("calib0")) {
                isCalibrated = true;
            }
        }
        getDevice().executeShellCommand(KILL_VIDEO_PLAYER);
        if (!isCalibrated) {
            cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_STOP_CALIBRATION);
            CLog.e("Calibration timed out.");
            return false;
        } else {
            CLog.i("Calibrated.");
            return true;
        }
    }

    private void doMeasurement(String testVideoPath, long durationSecond)
            throws DeviceNotAvailableException {
        CommandResult cr;
        getDevice().clearErrorDialogs();
        getDevice().unlockDevice();

        // play test video
        getDevice().executeShellCommand(String.format(START_VIDEO_PLAYER, testVideoPath));
        getRunUtil().sleep(3 * 1000);

        rotateScreen();
        getRunUtil().sleep(1 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_START_MEASUREMENT);
        CLog.i("Starting measurement: " + cr.getStdout());

        // end measurement
        getRunUtil().sleep(durationSecond * 1000);

        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_STOP_MEASUREMENT);
        CLog.i("Stopping measurement: " + cr.getStdout());
        if (cr == null || !cr.getStdout().contains("OK")) {
            cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_STOP_MEASUREMENT);
            CLog.i("Retry - Stopping measurement: " + cr.getStdout());
        }

        getDevice().executeShellCommand(KILL_VIDEO_PLAYER);
        getDevice().clearErrorDialogs();
    }

    private Map<String, String> getResult(Map<String, String> metrics,
            String keyprefix, boolean lipsync) {
        CommandResult cr;

        // get number of results
        getRunUtil().sleep(5 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mUtilPath, CMD_GET_NUM_FRAMES);
        String frameNum = cr.getStdout();
        CLog.i("Number of results: " + frameNum);

        // get all results
        cr = getRunUtil().runTimedCmd(GETDATA_TIMEOUT_MS, mUtilPath, CMD_GET_ALL_DATA);
        String allData = cr.getStdout();
        CLog.i("Data: " + allData);

        // parse results
        return parseResult(metrics, frameNum, allData, keyprefix, lipsync);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass()
                .getCanonicalName(), RUN_KEY);

        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();

        if (setupTestEnv()) {
            doMeasurement(TEST_VIDEO_1_DEVICE_PATH, TEST_VIDEO_1_DURATION);
            metrics = getResult(metrics, TEST_VIDEO_1_PREFIX, true);

            doMeasurement(TEST_VIDEO_2_DEVICE_PATH, TEST_VIDEO_2_DURATION);
            metrics = getResult(metrics, TEST_VIDEO_2_PREFIX, true);
        }

        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }

    /**
     * Parse Multimeter result.
     *
     * @param result
     * @return a {@link HashMap} that contains metrics keys and results
     */
    private Map<String, String> parseResult(Map<String, String> metrics,
            String numFrames, String result, String keyprefix, boolean lipsync) {
        CLog.i("== Video Multimeter Result '%s' ==", keyprefix);

        Pattern p = Pattern.compile("OK\\s+(\\d+)$");
        Matcher m = p.matcher(numFrames.trim());
        if (m.matches()) {
            String numFrame = m.group(1);
            metrics.put(keyprefix + "frame_captured", numFrame);
            CLog.i("Captured frames: " + numFrame);
            if (Integer.parseInt(numFrame) == 0) {
                // no frame captured
                CLog.w("No frame captured for " + keyprefix);
                return metrics;
            }
        } else {
            CLog.i("Cannot parse result for " + keyprefix);
            return metrics;
        }

        // Get total captured frames from the last line of result
        // format: "OK (time); (frame duration); (marker color); (total dropped frames)"
        String[] lines = result.split(System.getProperty("line.separator"));
        for (int i = lines.length - 1; i >= 0; i--) {
            p = Pattern.compile("OK\\s+\\d+;\\s*\\d+;\\s*[a-z]+;\\s*(\\d+)");
            m = p.matcher(lines[i].trim());
            if (m.matches()) {
                String dropFrame = m.group(1);
                metrics.put(keyprefix + "frame_drop", dropFrame);
                CLog.i("Dropped frames: " + dropFrame);
                break;
            }
        }
        if (!metrics.containsKey(keyprefix + "frame_drop")) {
            // no matching result found
            CLog.w("No result found for " + keyprefix);
            return metrics;
        }

        // parse lipsync results (the audio and video synchronization offset)
        // format: "OK (time); (frame duration); (marker color); (total dropped frames); (lipsync)"
        if (lipsync) {
            ArrayList<Integer> lipsyncVals = new ArrayList<Integer>();
            StringBuilder lipsyncValsStr = new StringBuilder("[");
            long lipsyncSum = 0;
            for (int i = 0; i < lines.length; i++) {
                p = Pattern.compile("OK\\s+\\d+;\\s*\\d+;\\s*[a-z]+;\\s*\\d+;\\s*(-?\\d+)");
                m = p.matcher(lines[i].trim());
                if (m.matches()) {
                    int lipSyncVal = Integer.parseInt(m.group(1));
                    lipsyncVals.add(lipSyncVal);
                    lipsyncValsStr.append(lipSyncVal);
                    lipsyncValsStr.append(" ,");
                    lipsyncSum += lipSyncVal;
                }
            }
            if (lipsyncVals.size() > 0) {
                lipsyncValsStr.append("]");
                Collections.sort(lipsyncVals);
                int lipsyncCount = lipsyncVals.size();
                int minLipsync = lipsyncVals.get(0);
                int maxLipsync = lipsyncVals.get(lipsyncCount - 1);
                CLog.i("Lipsync values: " + lipsyncVals.toString());
                metrics.put(keyprefix + "lipsync_count", String.valueOf(lipsyncCount));
                CLog.i("Lipsync Count: " + lipsyncCount);
                metrics.put(keyprefix + "lipsync_min", String.valueOf(lipsyncVals.get(0)));
                CLog.i("Lipsync Min: " + minLipsync);
                metrics.put(keyprefix + "lipsync_max", String.valueOf(maxLipsync));
                CLog.i("Lipsync Max: " + maxLipsync);
                double meanLipSync = (double) lipsyncSum / lipsyncCount;
                metrics.put(keyprefix + "lipsync_mean", String.valueOf(meanLipSync));
                CLog.i("Lipsync Mean: " + meanLipSync);
            } else {
                CLog.w("Lipsync value not found in result.");
            }
        }
        CLog.i("== End ==", keyprefix);
        return metrics;
    }

    private IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
