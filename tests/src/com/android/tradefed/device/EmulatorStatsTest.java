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

import com.android.tradefed.device.EmulatorStats.ITimeProvider;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Simple unit tests for {@link EmulatorStats}
 */
public class EmulatorStatsTest extends TestCase {

    public void testEmpty() {
        assertEquals(0, new EmulatorStats().getTotalUtilization(1));
    }

    public void testHalf() {
        // simulate simple case where one emulator has been
        // allocated for the entire window time
        long fakeCurrentTime = EmulatorStats.WINDOW_MS;
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // this will be allocationtime and starttime- use time of 0
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // for all future calls use a window time
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andStubReturn(fakeCurrentTime);

        EasyMock.replay(mockTime);

        EmulatorStats s = new EmulatorStats(mockTime);
        final String serial = "serial";
        s.recordAllocation(serial);
        s.recordFree(serial);
        // with two emulators, expect 50% util
        assertEquals(50, s.getTotalUtilization(2));
        // with 4 emulators, expect 25% util
        assertEquals(25, s.getTotalUtilization(4));
    }

    public void testFullyAllocOnStart() {
        // simulate simple case where one emulator has been
        // allocated for the entire time device manager is running, which is < window time
        long fakeCurrentTime = 100;
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // this will be allocationtime and starttime- use time of 0
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // for all future calls an arbritrary future time < window time
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andStubReturn(fakeCurrentTime);

        EasyMock.replay(mockTime);

        EmulatorStats s = new EmulatorStats(mockTime);
        final String serial = "serial";
        s.recordAllocation(serial);
        s.recordFree(serial);
        // with one emulators, expect 100% util
        assertEquals(100, s.getTotalUtilization(1));
    }

    /**
     * ensure allocation time from longer than window time is discarded
     */
    public void testCleanRecords() {
        long freeTime = 50;
        long fakeCurrentTime = EmulatorStats.WINDOW_MS + 100;
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // this will be allocationtime and starttime- use time of 0
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(0L).times(2);
        // this is free time
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(freeTime);
        // for all other calls use current time which is > window
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andStubReturn(fakeCurrentTime);

        EasyMock.replay(mockTime);

        EmulatorStats s = new EmulatorStats(mockTime);
        final String serial = "serial";
        s.recordAllocation(serial);
        s.recordFree(serial);
        // expect 0 util since allocation record is from > window time
        assertEquals(0, s.getTotalUtilization(1));
    }

    /**
     * Test scenario where allocation occurred before window started
     */
    public void testStartTimeBeforeWindow() {
        // create scenario where emulator has been allocated since beginning of time,
        // but tf has been running for 48 hours eg 2 * window time already
        long allocTime = 0;
        long currentTime = EmulatorStats.WINDOW_MS * 2;
        ITimeProvider mockTime = EasyMock.createMock(ITimeProvider.class);
        // this will be allocationtime and starttime- use time of 0
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andReturn(allocTime).times(2);
        // all other calls use current time
        EasyMock.expect(mockTime.getCurrentTimeMillis()).andStubReturn(currentTime);
        EasyMock.replay(mockTime);

        EmulatorStats s = new EmulatorStats(mockTime);
        final String serial = "serial";
        s.recordAllocation(serial);
        s.recordFree(serial);
        // expect 50% util from two emulators
        assertEquals(50, s.getTotalUtilization(2));
    }
}
