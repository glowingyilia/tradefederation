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
package com.android.wireless.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.Assert;

import java.util.concurrent.TimeUnit;

/**
 * Test runner for VPN tests
 */
public class VpnTest implements IRemoteTest, IDeviceTest {
    private static final String TEST_PACKAGE_NAME = "com.android.settings.tests";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";
    private static final String TEST_CLASS_NAME = "com.android.settings.vpn2.VpnTests";
    private static final int VPN_TIMER = 60 * 60 * 1000; // 1 hour
    private ITestDevice mTestDevice = null;

    @Option(name="profile", description="name of the VPN profiles in xml format")
    private String mVpnProfile = "vpnprofile.xml";

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Run VPN functional tests and post results
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
            TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("class", TEST_CLASS_NAME);
        runner.addInstrumentationArg("profile", mVpnProfile);

        // Add bugreport listener for failed tests
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);

        runner.setMaxTimeToOutputResponse(VPN_TIMER, TimeUnit.MILLISECONDS);
        bugListener.setDescriptiveName(this.getClass().getSimpleName());
        mTestDevice.runInstrumentationTests(runner, bugListener);
    }
}
