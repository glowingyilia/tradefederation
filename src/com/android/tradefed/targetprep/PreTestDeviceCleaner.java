//Copyright (C) 2014 The Android Open Source Project

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * A {@link ITargetPreparer} that configures a device based on provided {@link Option}s before the test execution.
 * <p/>
 * Requires a device where 'adb root' is possible, typically a userdebug build type.
 * <p/>
 */
@OptionClass(alias = "pretest-device-cleaner")
public class PreTestDeviceCleaner extends DeviceCleaner {

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        clean(device);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // no op since this preparer does pre-test test cleanup only
    }
}
