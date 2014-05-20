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

import com.android.tradefed.device.DeviceUtilStatsMonitor.ITimeProvider;
import com.android.tradefed.device.DeviceUtilStatsMonitor.UtilizationDesc;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Simple unit tests for {@link DeviceUtilStatsMonitor}
 */
public class DeviceUtilStatsMonitorTest extends TestCase {

    public void testEmpty() {
        assertEquals(0, new DeviceUtilStatsMonitor().getUtilizationStats().mTotalUtil);
    }

    /**
     * Test case where device has been available but never allocated
     */
    public void testOnlyAvailable() {
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 10 for current time when getUtil call happens
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mockTime);

        DeviceUtilStatsMonitor s = new DeviceUtilStatsMonitor(mockTime);
        final String serial = "serial";
        s.notifyDeviceStateChange(serial, DeviceAllocationState.Unknown,
                DeviceAllocationState.Available);

        UtilizationDesc desc = s.getUtilizationStats();
        assertEquals(0, desc.mTotalUtil);
        assertEquals(1, desc.mDeviceUtil.size());
        assertEquals(0L, (long)desc.mDeviceUtil.get(serial));
    }

    /**
     * Test case where device has been allocated but never available
     */
    public void testOnlyAllocated() {
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 10 for current time when getUtil call happens
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mockTime);

        DeviceUtilStatsMonitor s = new DeviceUtilStatsMonitor(mockTime);
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
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // use a time of 0 for starttime, and available starttime
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // use time of 5 for current time when available end, and alloc start happens
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(5L).times(2);
        // use time of 10 when getUtil time happens
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(10L);
        EasyMock.replay(mockTime);

        DeviceUtilStatsMonitor s = new DeviceUtilStatsMonitor(mockTime);
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
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // this will be available start and starttime- use time of 0
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // this is available end and alloc start
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(50L).times(2);
        // for all other calls use current time which is > window
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andStubReturn(fakeCurrentTime);

        EasyMock.replay(mockTime);

        DeviceUtilStatsMonitor s = new DeviceUtilStatsMonitor(mockTime);
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
}
