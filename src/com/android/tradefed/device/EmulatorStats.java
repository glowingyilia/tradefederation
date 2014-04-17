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

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * A quick and dirty class to calculate emulator utilization statistics
 * <p/>
 * TODO: make this calculate device stats too. Right now its calculation is too simplistic
 * to handle cases like device coming online, becoming unavailable etc
 */
public class EmulatorStats {

    private class AllocationRecord {
        long mAllocTime = -1;
        long mFreeTime = -1;

        AllocationRecord() {
            mAllocTime = mTimeProvider.getCurrentTimeMillis();
        }

        void setFree() {
            mFreeTime = mTimeProvider.getCurrentTimeMillis();
        }
    }

    /**
     * interface for retrieving current time. Used so unit test can mock
     */
    static interface ITimeProvider {
        long getCurrentTimeMillis();
    }

    static class SystemTimeProvider implements ITimeProvider {

        @Override
        public long getCurrentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    // use 24 hour window
    final static long WINDOW_MS = 24L * 60L * 60L * 1000L;

    // TODO: in future, consider keeping per device utilization records
    /** the record of past emulator allocations */
    private LinkedList<AllocationRecord> mAllocationRecords = new LinkedList<AllocationRecord>();

    /** the record of emulator currently allocated */
    private Map<String, AllocationRecord> mCurrentAllocations =
            new Hashtable<String, AllocationRecord>();

    private final ITimeProvider mTimeProvider;
    /** stores the startup time of this stats object */
    private final long mStartTime;

    EmulatorStats(ITimeProvider p) {
        mTimeProvider = p;
        mStartTime = p.getCurrentTimeMillis();
    }

    EmulatorStats() {
        this(new SystemTimeProvider());
    }

    /**
     * Get the total average emulator utilization since this class was initialized, up to the
     * last 24 hours, as a percent
     *
     * @param totalEmulators the total number of emulators available for allocation
     */
    public long getTotalUtilization(int totalEmulators) {
        cleanRecords();
        long currentTime = mTimeProvider.getCurrentTimeMillis();
        long availTime = WINDOW_MS;
        long windowStartTime = currentTime - WINDOW_MS;
        if (availTime > (currentTime - mStartTime)) {
            // device manager has been running less than window time - just use current uptime
            availTime = currentTime - mStartTime;
            if (availTime <= 0) {
                // unusual, but can happen in unit tests
                availTime = 1;
            }
            // also set window start time to device manager start time
            windowStartTime = mStartTime;
        }

        // TODO: this assumes all emulators are available 100% of the time.
        // Not always accurate
        long totalAvailTime = totalEmulators * availTime;
        // TODO: calculate per device util
        long totalAllocTime = 0;
        for (AllocationRecord a : mAllocationRecords) {
            if (a.mAllocTime < windowStartTime) {
                // this device was allocated before window started, just use allocation time at start
                // of window
                totalAllocTime += (a.mFreeTime-windowStartTime);
            } else {
                totalAllocTime += (a.mFreeTime-a.mAllocTime);
            }
        }
        List<AllocationRecord> copy = new ArrayList<AllocationRecord>(mCurrentAllocations.values());
        for (AllocationRecord a : copy) {
            if (a.mAllocTime < windowStartTime) {
                // this device was allocated before window started, just use allocation time at start
                // of window
                totalAllocTime += (currentTime-windowStartTime);
            } else {
                totalAllocTime += (currentTime-a.mAllocTime);
            }
        }
        return (totalAllocTime*100)/totalAvailTime;
    }

    /**
     * Record an emulator allocation event
     * @param serial
     */
    void recordAllocation(String serial) {
        mCurrentAllocations.put(serial, new AllocationRecord());
    }

    /**
     * Record an emulator allocation event
     * @param serial
     */
    void recordFree(String serial) {
        AllocationRecord a = mCurrentAllocations.remove(serial);
        if (a == null) {
            CLog.e("no record found for %s", serial);
            return;
        }
        a.setFree();
        mAllocationRecords.add(a);
    }

    /**
     * Remove all old records outside the moving average window (currently 24 hours)
     */
    private void cleanRecords() {
        long obsoleteTime = mTimeProvider.getCurrentTimeMillis() - WINDOW_MS;
        ListIterator<AllocationRecord> li = mAllocationRecords.listIterator();
        while (li.hasNext()) {
            if (li.next().mFreeTime < obsoleteTime) {
                li.remove();
            }
        }
    }
}
