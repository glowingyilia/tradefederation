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

import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * An {@link ITargetCleaner} that uses fastboot to format userdata and cache after test
 *
 * Device must support 'fastboot format'
 */
@OptionClass(alias = "fastboot-format-cleaner")
public class FormatDeviceTargetCleaner implements ITargetCleaner {

    @Option(name = "format-after-test",
            description = "Format device after test invocation finishes. " +
                          "Device must support 'fastboot format'")
    private boolean mShouldFormat = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        // no op since this is a target cleaner
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mShouldFormat && DeviceState.ONLINE.equals(device.getDeviceState())) {
            device.rebootIntoBootloader();
            device.executeLongFastbootCommand("format", "cache");
            device.executeLongFastbootCommand("format", "userdata");
            device.executeFastbootCommand("reboot");
            device.waitForDeviceAvailable();
        }
    }

}
