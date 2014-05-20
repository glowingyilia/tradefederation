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
import com.android.tradefed.util.TimeUtil;

import junit.framework.TestCase;

/**
 * Load test for {@link DeviceUtilStatsMonitor} Used to ensure memory used by monitor under heavy
 * load is reasonable
 */
public class DeviceUtilStatsMonitorLoadTest extends TestCase {

    private static final int NUM_DEVICES = 100;
    private static final long ALLOC_TIME_MS = 60 * 1000;

    /**
     * Simulate a heavy load by generating constant allocation events of ALLOC_TIME_MS length for
     * all NUM_DEVICES devices.
     * <p/>
     * Intended to be run under a profiler.
     */
    public void testManyRecords() {
        MockTimeProvider timeProvider = new MockTimeProvider();
        DeviceUtilStatsMonitor monitor = new DeviceUtilStatsMonitor(timeProvider);
        for (int i = 0; i < NUM_DEVICES; i++) {
            monitor.notifyDeviceStateChange(Integer.toString(i), DeviceAllocationState.Unknown,
                    DeviceAllocationState.Available);
        }
        while (timeProvider.mCurrentTime < DeviceUtilStatsMonitor.WINDOW_MS) {
            for (int i = 0; i < NUM_DEVICES; i++) {
                monitor.notifyDeviceStateChange(Integer.toString(i),
                        DeviceAllocationState.Available, DeviceAllocationState.Allocated);
            }
            timeProvider.incrementTime();
            for (int i = 0; i < NUM_DEVICES; i++) {
                monitor.notifyDeviceStateChange(Integer.toString(i),
                        DeviceAllocationState.Allocated, DeviceAllocationState.Available);
            }
        }
        long startTime = System.currentTimeMillis();
        UtilizationDesc d = monitor.getUtilizationStats();
        System.out.println(TimeUtil.formatElapsedTime(System.currentTimeMillis() - startTime));
    }

    private static class MockTimeProvider implements ITimeProvider {

        long mCurrentTime = 0;

        void incrementTime() {
            mCurrentTime += ALLOC_TIME_MS;
        }

        @Override
        public long getCurrentTimeMillis() {
            return mCurrentTime;
        }
    }

    public static void main(String[] args) {
        new DeviceUtilStatsMonitorLoadTest().testManyRecords();
    }
}
