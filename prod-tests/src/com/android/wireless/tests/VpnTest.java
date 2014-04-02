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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.util.concurrent.TimeUnit;

/**
 * Test runner for VPN tests
 */
public class VpnTest implements IRemoteTest, IDeviceTest {
    private static final String TEST_PACKAGE_NAME = "com.android.settings.tests";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";
    private static final String TEST_CLASS_NAME = "com.android.settings.vpn2.VpnTests";
    private static final String PPTP_TEST_CASE = "testPPTPConnection";
    // All test cases except pptp
    private static final String[] NONE_PPTP_TEST_CASES = {"testL2tpIpsecPskConnection",
            "testL2tpIpsecRsaConnection", "testIpsecXauthRsaConnection",
            "testIpsecXauthPskConnection", "testIpsecHybridRsaConnection"};
    private static final long VPN_TIMER = 60 * 60 * 1000; // 1 hour
    private static final long WAIT_TIMER = 5 * 60 * 1000; // 5 minutes
    private ITestDevice mTestDevice = null;

    @Option(name = "profile", description = "name of the VPN profiles in xml format")
    private String mVpnProfile = "vpnprofile.xml";

    @Option(name = "pptp-wifi-ssid", description = "wifi network for PPTP test, which requires"
            + " a network that allows GRE packets")
    private String mPptpWifiSsid = null;

    @Option(name = "pptp-wifi-password", description = "wifi network password for PPTP test, which"
            + " requires a network that allows GRE packets")
    private String mPptpWifiPsw = null;

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
        // Wait for device is stable
        getRunUtil().sleep(WAIT_TIMER);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
            TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("profile", mVpnProfile);
        if (mPptpWifiSsid != null) {
            // exclude pptp test
            String[] methods = new String[NONE_PPTP_TEST_CASES.length];
            for (int i = 0; i < NONE_PPTP_TEST_CASES.length; i++) {
                  StringBuilder method = new StringBuilder(TEST_CLASS_NAME);
                  method.append("#");
                  method.append(NONE_PPTP_TEST_CASES[i]);
                  methods[i] = method.toString();
            }
            runner.setClassNames(methods);
        } else {
            // run the whole suite
            runner.addInstrumentationArg("class", TEST_CLASS_NAME);
        }
        // Add bugreport listener for failed tests
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);
        runner.setMaxTimeToOutputResponse(VPN_TIMER, TimeUnit.MILLISECONDS);
        bugListener.setDescriptiveName(this.getClass().getSimpleName());
        CollectingTestListener collectListener = new CollectingTestListener();
        mTestDevice.runInstrumentationTests(runner, bugListener, collectListener);
        if (mPptpWifiSsid != null) {
            // Run PPTP test
            mTestDevice.connectToWifiNetwork(mPptpWifiSsid, mPptpWifiPsw);
            // Wait for 60 seconds till it is fully connected.
            getRunUtil().sleep(60 * 1000);
            // Run PPTP test case
            runner.removeInstrumentationArg("class");
            runner.setMethodName(TEST_CLASS_NAME, PPTP_TEST_CASE);
            mTestDevice.runInstrumentationTests(runner, bugListener, collectListener);
        }
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

}
