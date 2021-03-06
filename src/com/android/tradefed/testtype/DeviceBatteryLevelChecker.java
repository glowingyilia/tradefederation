/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.testtype;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ITargetPreparer} that checks for a minimum battery charge, and waits for the battery
 * to reach a second charging threshold if the minimum charge isn't present.
 */
@OptionClass(alias = "battery-checker")
public class DeviceBatteryLevelChecker implements IDeviceTest, IRemoteTest {

    ITestDevice mTestDevice = null;

    /**
     * We use max-battery here to coincide with a {@link DeviceSelectionOptions} option of the same
     * name.  Thus, DeviceBatteryLevelChecker
     */
    @Option(name = "max-battery", description = "Charge level below which we force the device to " +
            "sit and charge.  Range: 0-100.")
    private Integer mMaxBattery = 20;

    @Option(name = "resume-level", description = "Charge level at which we release the device to " +
            "begin testing again. Range: 0-100.")
    private int mResumeLevel = 80;

    /**
     * This is decoupled from the log poll time below specifically to allow this invocation to be
     * killed without having to wait for the full log period to lapse.
     */
    @Option(name = "poll-time", description = "Time in minutes to wait between battery level " +
            "polls. Decimal times accepted.")
    private double mChargingPollTime = 1.0;

    @Option(name = "batt-log-period", description = "Min time in minutes to wait between " +
            "printing current battery level to log.  Decimal times accepted.")
    private double mLoggingPollTime = 10.0;

    @Option(name = "reboot-charging-devices", description = "Whether to reboot a device when we " +
            "detect that it should be held for charging.  This would hopefully kill any battery-" +
            "draining processes and allow the device to charge at its fastest rate.")
    private boolean mRebootChargeDevices = false;

    Integer checkBatteryLevel(ITestDevice device) throws DeviceNotAvailableException {
        try {
            IDevice idevice = device.getIDevice();
            // Force a synchronous check, which will also tell us if the device is still alive
            return idevice.getBattery(0, TimeUnit.MILLISECONDS).get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    private void turnScreenOffOrStopRuntime(ITestDevice device) throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand("pm path android");
        if (output == null || !output.contains("package:")) {
            CLog.d("framework does not seem to be running, trying to stop it.");
            // stop framework in case it's running some sort of runtime restart loop, and we can
            // still charge the device
            getDevice().executeShellCommand("stop");
        } else {
            output = getDevice().executeShellCommand("dumpsys power");
            if (output.contains("mScreenOn=true")) {
                // KEYCODE_POWER = 26
                getDevice().executeShellCommand("input keyevent 26");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        Integer batteryLevel = checkBatteryLevel(mTestDevice);

        if (batteryLevel == null) {
            CLog.w("Failed to determine battery level for device %s.",
                    mTestDevice.getSerialNumber());
            return;
        } else if (batteryLevel < mMaxBattery) {
            // Time-out.  Send the device to the corner
            CLog.w("Battery level %d is below the min level %d; holding for device %s to charge " +
                    "to level %d", batteryLevel, mMaxBattery, mTestDevice.getSerialNumber(),
                    mResumeLevel);
        } else {
            // Good to go
            CLog.d("Battery level %d is above the minimum of %d; %s is good to go.", batteryLevel,
                    mMaxBattery, mTestDevice.getSerialNumber());
            return;
        }

        if (mRebootChargeDevices) {
            // reboot the device, in an attempt to kill any battery-draining processes
            CLog.d("Rebooting device %s prior to holding", mTestDevice.getSerialNumber());
            mTestDevice.reboot();
        }

        turnScreenOffOrStopRuntime(mTestDevice);

        // If we're down here, it's time to hold the device until it reaches mResumeLevel
        Long lastReportTime = System.currentTimeMillis();
        Integer newLevel = checkBatteryLevel(mTestDevice);
        while (batteryLevel != null && batteryLevel < mResumeLevel) {
            if (System.currentTimeMillis() - lastReportTime > mLoggingPollTime * 60 * 1000) {
                // Log the battery level status every mLoggingPollTime minutes
                CLog.w("Battery level for device %s is currently %d", mTestDevice.getSerialNumber(),
                        newLevel);
                lastReportTime = System.currentTimeMillis();
            }

            getRunUtil().sleep((long) (mChargingPollTime * 60 * 1000));
            newLevel = checkBatteryLevel(mTestDevice);
            if (newLevel == null) {
                // weird
                CLog.w("Breaking out of wait loop because battery level read failed for device %s",
                        mTestDevice.getSerialNumber());
                break;
            } else if (newLevel < batteryLevel) {
                // also weird
                CLog.w("Warning: battery discharged from %d to %d on device %s during the last " +
                        "%.02f minutes.", batteryLevel, newLevel, mTestDevice.getSerialNumber(),
                        mChargingPollTime);
            } else {
                CLog.v("Battery level for device %s is currently %d", mTestDevice.getSerialNumber(),
                        newLevel);
            }
            batteryLevel = newLevel;
        }
        CLog.w("Device %s is now charged to battery level %d; releasing.",
                mTestDevice.getSerialNumber(), batteryLevel);
    }

    /**
     * Get a RunUtil instance
     * <p />
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}

