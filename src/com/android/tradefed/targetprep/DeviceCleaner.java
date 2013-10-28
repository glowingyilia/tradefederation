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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;

/**
 * Performs reboot or format as cleanup action after test, and optionally turns screen off
 */
@OptionClass(alias = "device-cleaner")
public class DeviceCleaner implements ITargetCleaner {

    public static enum CleanupAction {
        NONE,
        REBOOT,
        FORMAT,
    }

    @Option(name = "cleanup-action",
            description = "Type of action to perform as a post test cleanup; options are: "
            + "NONE, REBOOT or FORMAT; defaults to NONE")
    private CleanupAction mCleanupAction = CleanupAction.NONE;

    @Option(name = "screen-off", description = "After cleanup action, "
            + "if screen should be turned off; defaults to false")
    private boolean mScreenOff = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        // no op since this is a target cleaner
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (TestDeviceState.ONLINE.equals(device.getDeviceState())) {
            switch (mCleanupAction) {
                case NONE:
                    // do nothing here
                    break;
                case REBOOT:
                    device.reboot();
                    break;
                case FORMAT:
                    device.rebootIntoBootloader();
                    device.executeLongFastbootCommand("format", "cache");
                    device.executeLongFastbootCommand("format", "userdata");
                    device.executeFastbootCommand("reboot");
                    device.waitForDeviceAvailable();
                    break;
            }
            // done with cleanup, check if screen should be turned off
            if (mScreenOff) {
                String output = device.executeShellCommand("dumpsys power");
                if (output.contains("mScreenOn=true")) {
                    // KEYCODE_POWER = 26
                    device.executeShellCommand("input keyevent 26");
                }
            }
        }
    }
}
