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
package com.android.tradefed.device;

import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.DeviceUtilStatsMonitor.ITimeProvider;
import com.android.tradefed.device.DeviceUtilStatsMonitor.UtilizationDesc;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Simple unit tests for {@link DeviceUtilStatsMonitor}
 */
public class DeviceUtilStatsMonitorTest extends TestCase {

    private IDeviceManager mMockDeviceManager;
    private ITimeProvider mMockTime;

    @Override
    public void setUp() {
        mMockDeviceManager = EasyMock.createNiceMock(IDeviceManager.class);
        mMockTime = EasyMock.createNiceMock(ITimeProvider.class);
    }

    public void testEmpty() {
        EasyMock.replay(mMockTime, mMockDeviceManager);
        assertEquals(0, createUtilMonitor().getUtilizationStats().mTotalUtil);
    }

    /**
     * Test case where device has been available but never allocated
     */
    public void testOnlyAvailable() {
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 10 for current time when getUtil call happens
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);

        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(0, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
        assertEquals(0L, (long)desc.mDeviceUtil.get(serial));
    }

    private DeviceUtilStatsMonitor createUtilMonitor() {
        return new DeviceUtilStatsMonitor(mMockTime) {
            @Override
            IDeviceManager getDeviceManager() {
                return mMockDeviceManager;
            }
        };
    }

    /**
     * Test case where device has been allocated but never available
     */
    public void testOnlyAllocated() {
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 10 for current time when getUtil call happens
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Allocated);

        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(100L, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
        assertEquals(100L, (long)desc.mDeviceUtil.get(serial));
    }

    /**
     * Test case where device has been allocated for half the time
     */
    public void testHalfAllocated() {
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 5 for current time when available end, and alloc start happens
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(5L).times(2);
        // use time of 10 when getUtil time happens
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Available,
                DeviceAllocationState.Allocated);

        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(50L, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
        assertEquals(50L, (long)desc.mDeviceUtil.get(serial));
    }

    /**
     * Ensure records from older than window are discarded.
     * <p/>
     * Simulate by recording available record entirely before window, and allocation start before
     * window. therefore expect utilization 100%
     */
    public void testCleanRecords() {
        long fakeCurrentTime = DeviceUtilStatsMonitor.WINDOW_MS + 100;
        // this will be available start and starttime- use time of 0
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // this is available end and alloc start
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(50L).times(2);
        // for all other calls use current time which is > window
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andStubReturn(fakeCurrentTime);

        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Available,
                DeviceAllocationState.Allocated);

        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(100L, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
        assertEquals(100L, (long)desc.mDeviceUtil.get(serial));
    }

    /**
     * Ensures null device data is dropped when --collect-null-device==IGNORE
     * @throws ConfigurationException
     */
    public void testNullDevice_ignored() throws ConfigurationException {
        // this will be available start and starttime- use time of 0
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // this is available end and alloc start
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(50L).times(2);
        // for all other calls use 100
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andStubReturn(100L);

        EasyMock.expect(mMockDeviceManager.isNullDevice(EasyMock.<String>anyObject()))
                .andStubReturn(Boolean.TRUE);
        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        new ArgsOptionParser(s).parse("--collect-null-device", "IGNORE");
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Available,
                DeviceAllocationState.Allocated);
        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(0, desc.mTotalUtil);
        assertEquals(0, desc.mDeviceUtil.size());
    }

    /**
     * Ensures null device data treatment when --collect-null-device==INCLUDE_IF_USED
     * @throws ConfigurationException
     */
    public void testNullDevice_whenUsed() throws ConfigurationException {
        // this will be available start and starttime- use time of 0
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // this is available end and alloc start
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andReturn(50L).times(3);
        // for all other calls use 100
        EasyMock.expect(mMockTime.getCurrentTimeMillis()).andStubReturn(100L);

        EasyMock.expect(mMockDeviceManager.isNullDevice(EasyMock.<String>anyObject()))
                .andStubReturn(Boolean.TRUE);
        EasyMock.replay(mMockTime, mMockDeviceManager);

        DeviceUtilStatsMonitor s = createUtilMonitor();
        new ArgsOptionParser(s).parse("--collect-null-device", "INCLUDE_IF_USED");
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);
        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(0, desc.mTotalUtil);
        assertEquals(0, desc.mDeviceUtil.size());

        s.notifyDeviceStateChange(serial, DeviceAllocationState.Available,
                DeviceAllocationState.Allocated);
        desc = s.getUtilizationStats();
        assertEquals(50, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
    }
}
