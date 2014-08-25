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

package com.android.encryption.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import junit.framework.Assert;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs the encryption func tests.
 * <p>
 * Encrypts the device inplace, and check the password to make sure the device can boot into the
 * system. The time of every stage is measured.
 * </p>
 */
public class EncryptionFunctionalityTest implements IDeviceTest, IRemoteTest {

    private static final int BOOT_TIMEOUT = 120 * 1000;

    ITestDevice mTestDevice = null;

    final String[] STAGE_NAME = {
            "encryption", "online", "bootcomplete", "decryption", "bootcomplete" };
    Map<String, String> metrics = new HashMap<String, String>();
    int stage = 0;
    long stageEndTime, stageStartTime;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        listener.testRunStarted("EncryptionFunc", 0);
        if (!(mTestDevice.unencryptDevice() && !mTestDevice.isDeviceEncrypted())) {
            String message = String.format("Failed to unencrypt device %s",
                    mTestDevice.getSerialNumber());
            CLog.e(message);
            throw new DeviceNotAvailableException(message);
        }
        mTestDevice.waitForDeviceAvailable();
        stageStartTime = System.currentTimeMillis();
        mTestDevice.executeShellCommand("vdc cryptfs enablecrypto inplace password \"abcd\"");
        try {
            mTestDevice.waitForDeviceOnline();
            stageEnd(); // stage 1

            mTestDevice.waitForBootComplete(BOOT_TIMEOUT);
            stageEnd(); // stage 2

            mTestDevice.enableAdbRoot();
            mTestDevice.executeShellCommand("vdc cryptfs checkpw \"abcd\"");
            mTestDevice.executeShellCommand("vdc cryptfs restart");
            stageEnd(); // stage 3

            mTestDevice.waitForDeviceAvailable();
            stageEnd(); // stage 4
        } catch (DeviceNotAvailableException e) {
            listener.testRunFailed(String.format("Device not avaible after %s before %s.",
                    STAGE_NAME[stage], STAGE_NAME[stage + 1]));
        }
        metrics.put("SuccessStage", Integer.toString(stage));
        listener.testRunEnded(0, metrics);
    }

    // measure the time between stages.
    void stageEnd() {
        stageEndTime = System.currentTimeMillis();
        metrics.put(String.format("between%sAnd%s", capitalize(STAGE_NAME[stage]),
                capitalize(STAGE_NAME[stage + 1])), Long.toString(stageEndTime - stageStartTime));
        stageStartTime = stageEndTime;
        stage++;
    }

    private String capitalize(String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
