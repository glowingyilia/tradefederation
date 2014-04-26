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

package com.android.sdk;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.sdk.tests.EmulatorGpsPreparer;
import com.android.sdk.tests.EmulatorSmsPreparer;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.SdkAvdPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import java.util.HashMap;

/**
 * A class for posting emulator boot result as a test
 */
public class EmulatorBootTest implements IDeviceTest, IRemoteTest, IBuildReceiver, IConfigurationReceiver {
    private IConfiguration mConfiguration;
    private String mTestLabel = "emulator_boot_test";
    private SdkAvdPreparer mAvdPreparer;
    private EmulatorSmsPreparer mSmsPreparer;
    private EmulatorGpsPreparer mGpsPreparer;
    private ITestDevice mDevice;

    void createPreparers() {

        mAvdPreparer = (SdkAvdPreparer) mConfiguration.getConfigurationObject("sdk-avd-preparer");
        mSmsPreparer = (EmulatorSmsPreparer) mConfiguration.getConfigurationObject("sms-preparer");
        mGpsPreparer = (EmulatorGpsPreparer) mConfiguration.getConfigurationObject("gps-preparer");
    }

   IBuildInfo mBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
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
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier bootTest = new TestIdentifier(EmulatorBootTest.class.getSimpleName(), mTestLabel);
        listener.testRunStarted(EmulatorBootTest.class.getSimpleName(), 1);
        listener.testStarted(bootTest);
        try {
            createPreparers();
            mAvdPreparer.setUp(mDevice, mBuildInfo);
            mSmsPreparer.setUp(mDevice, mBuildInfo);
            mGpsPreparer.setUp(mDevice, mBuildInfo);
        }
        catch(BuildError b) {
            listener.testFailed(TestFailure.ERROR, bootTest, StreamUtil.getStackTrace(b));
            // throw exception to prevent other tests from executing needlessly
            throw new DeviceUnresponsiveException("The emulator failed to boot", b);
        }
        catch(RuntimeException e) {
            listener.testFailed(TestFailure.ERROR, bootTest, StreamUtil.getStackTrace(e));
            throw e;
        } catch (TargetSetupError e) {
            listener.testFailed(TestFailure.ERROR, bootTest, StreamUtil.getStackTrace(e));
            throw new RuntimeException(e);
        }
        finally {
            listener.testEnded(bootTest, new HashMap<String, String>());
            listener.testRunEnded(0, new HashMap<String,String>());
        }
    }
}
