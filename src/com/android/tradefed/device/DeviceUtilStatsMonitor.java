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

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@link IDeviceMonitor} that calculates device utilization stats.
 * <p/>
 * Currently measures allocation time % out of allocation + avail time, over a 24 hour window.
 * <p/>
 * If used, {@link #getUtilizationStats()} must be called periodically to clear accumulated memory.
 */
public class DeviceUtilStatsMonitor implements IDeviceMonitor {

    /**
     * Enum for configuring treatment of stub devices when calculating average host utilization
     */
    public enum StubDeviceUtil {
        /** never include stub device data */
        IGNORE,
        /**
         * include stub device data only if any stub device of same type is allocated at least
         * once
         */
        INCLUDE_IF_USED,
        /** always include stub device data */
        ALWAYS_INCLUDE
    }

    @Option(name = "collect-null-device", description =
            "controls if null device data should be used when calculating avg host utilization")
    private StubDeviceUtil mCollectNullDevice = StubDeviceUtil.INCLUDE_IF_USED;

    @Option(name = "collect-emulator", description =
            "controls if emulator data should be used when calculating avg host utilization")
    private StubDeviceUtil mCollectEmulator = StubDeviceUtil.INCLUDE_IF_USED;

    private boolean mNullDeviceAllocated = false;
    private boolean mEmulatorAllocated = false;
    /**
     * A record of the start and end time a device spent in one of the measured states (either
     * available or allocated).
     */
    private class StateRecord {
        long mStartTime = -1;
        long mEndTime = -1;

        StateRecord() {
            mStartTime = mTimeProvider.getCurrentTimeMillis();
        }

        void setEnd() {
            mEndTime = mTimeProvider.getCurrentTimeMillis();
        }
    }

    /**
     * Holds the total accounting of time spent in available and allocated state, over the
     * {@link #WINDOW_MS}
     */
    private static class DeviceStateRecords {
        LinkedList<StateRecord> mAvailableRecords = new LinkedList<>();
        LinkedList<StateRecord> mAllocatedRecords = new LinkedList<>();
    }

    /**
     * Container for utilization stats.
     */
    public static class UtilizationDesc {
        final int mTotalUtil;
        final Map<String, Integer> mDeviceUtil;

        public UtilizationDesc(int totalUtil, Map<String, Integer> deviceUtil) {
            mTotalUtil = totalUtil;
            mDeviceUtil = deviceUtil;
        }

        /**
         * Return the total utilization for all devices in TF process, measured as total allocation
         * time for all devices vs total available time.
         *
         * @return percentage utilization
         */
        public int getTotalUtil() {
            return mTotalUtil;
        }

        /**
         * Helper method to return percent utilization for a device. Returns 0 if no utilization
         * data exists for device
         */
        public Integer getUtilForDevice(String serial) {
            Integer util = mDeviceUtil.get(serial);
            if (util == null) {
                return 0;
            }
            return util;
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

    /** a map of device serial to state records */
    private Map<String, DeviceStateRecords> mDeviceUtilMap = new Hashtable<>();

    private final ITimeProvider mTimeProvider;
    /** stores the startup time of this stats object */
    private final long mStartTime;

    DeviceUtilStatsMonitor(ITimeProvider p) {
        mTimeProvider = p;
        mStartTime = p.getCurrentTimeMillis();
    }

    public DeviceUtilStatsMonitor() {
        this(new SystemTimeProvider());
    }

    /**
     * Get the device utilization up to the last 24 hours
     */
    public synchronized UtilizationDesc getUtilizationStats() {
        CLog.d("Calculating device util");
        long currentTime = mTimeProvider.getCurrentTimeMillis();
        cleanAllRecords(currentTime);

        long totalAvailTime = 0;
        long totalAllocTime = 0;
        long windowStartTime = currentTime - WINDOW_MS;
        if (windowStartTime < mStartTime) {
            // this class has been running less than window time - use start time as start of
            // window
            windowStartTime = mStartTime;
        }
        Map<String, Integer> deviceUtilMap = new HashMap<>(mDeviceUtilMap.size());
        Map<String, DeviceStateRecords> mapCopy = new HashMap<>(mDeviceUtilMap);
        for (Map.Entry<String, DeviceStateRecords> deviceRecordEntry : mapCopy.entrySet()) {
            if (shouldTrackDevice(deviceRecordEntry)) {
                long availTime = countTime(windowStartTime, currentTime,
                        deviceRecordEntry.getValue().mAvailableRecords);
                long allocTime = countTime(windowStartTime, currentTime,
                        deviceRecordEntry.getValue().mAllocatedRecords);
                totalAvailTime += availTime;
                totalAllocTime += allocTime;
                deviceUtilMap.put(deviceRecordEntry.getKey(), getUtil(availTime, allocTime));
            }
        }
        return new UtilizationDesc(getUtil(totalAvailTime, totalAllocTime), deviceUtilMap);
    }

    /**
     * Return the total time in ms spent in state in window
     */
    private long countTime(long windowStartTime, long currentTime, LinkedList<StateRecord> records) {
        long totalTime = 0;
        for (StateRecord r : records) {
            long startTime = r.mStartTime;
            // started before window - truncate to window start time
            if (startTime < windowStartTime) {
                startTime = windowStartTime;
            }
            long endTime = r.mEndTime;
            // hasn't ended yet - there should only be one of these. Truncate to current time
            if (endTime < 0) {
                endTime = currentTime;
            }
            if (endTime < startTime) {
                CLog.w("endtime %d is less than start time %d", endTime, startTime);
                continue;
            }
            totalTime += (endTime - startTime);
        }
        return totalTime;
    }

    /**
     * Get device utilization as a percent
     */
    private int getUtil(long availTime, long allocTime) {
        long totalTime = availTime + allocTime;
        if (totalTime <= 0) {
            return 0;
        }
        return (int)((allocTime * 100) / totalTime);
    }

    /**
     * Remove all old records outside the moving average window (currently 24 hours)
     */
    private void cleanAllRecords(long currentTime) {
        long obsoleteTime = currentTime - WINDOW_MS;
        for (DeviceStateRecords r : mDeviceUtilMap.values()) {
            cleanRecordList(obsoleteTime, r.mAllocatedRecords);
            cleanRecordList(obsoleteTime, r.mAvailableRecords);
        }
    }

    private void cleanRecordList(long obsoleteTime, LinkedList<StateRecord> records) {
        ListIterator<StateRecord> li = records.listIterator();
        while (li.hasNext()) {
            StateRecord r = li.next();
            if (r.mEndTime > 0 && r.mEndTime < obsoleteTime) {
                li.remove();
            } else {
                // since records are sorted, just end now
                return;
            }
        }
    }

    @Override
    public void run() {
        // ignore
    }

    @Override
    public void setDeviceLister(DeviceLister lister) {
        // ignore
    }

    /**
     * Listens to device state changes and records time that device transitions from or to
     * available or allocated state.
     */
    @Override
    public synchronized void notifyDeviceStateChange(String serial, DeviceAllocationState oldState,
            DeviceAllocationState newState) {
        // record the 'state ended' time
        DeviceStateRecords stateRecord = getDeviceRecords(serial);
        if (DeviceAllocationState.Available.equals(oldState)) {
            recordStateEnd(stateRecord.mAvailableRecords);
        } else if (DeviceAllocationState.Allocated.equals(oldState)) {
            recordStateEnd(stateRecord.mAllocatedRecords);
        }

        // record the 'state started' time
        if (DeviceAllocationState.Available.equals(newState)) {
            recordStateStart(stateRecord.mAvailableRecords);
        } else if (DeviceAllocationState.Allocated.equals(newState)) {
            IDeviceManager dvcMgr = getDeviceManager();
            if (dvcMgr.isNullDevice(serial)) {
                mNullDeviceAllocated = true;
            } else if (dvcMgr.isEmulator(serial)) {
                mEmulatorAllocated = true;
            }
            recordStateStart(stateRecord.mAllocatedRecords);
        }
    }

    private void recordStateEnd(LinkedList<StateRecord> records) {
        if (records.isEmpty()) {
            CLog.e("error, no records exist");
            return;
        }
        StateRecord r = records.getLast();
        if (r.mEndTime != -1) {
            CLog.e("error, last state already marked as ended");
            return;
        }
        r.setEnd();
    }

    private void recordStateStart(LinkedList<StateRecord> records) {
        // TODO: do some correctness checks
        StateRecord r = new StateRecord();
        records.add(r);
    }

    /**
     * Get the device state records for given serial, creating if necessary.
     */
    private DeviceStateRecords getDeviceRecords(String serial) {
        DeviceStateRecords r = mDeviceUtilMap.get(serial);
        if (r == null) {
            r = new DeviceStateRecords();
            mDeviceUtilMap.put(serial, r);
        }
        return r;
    }

    private boolean shouldTrackDevice(Entry<String, DeviceStateRecords> deviceRecordEntry) {
        IDeviceManager dvcMgr = getDeviceManager();
        String serial = deviceRecordEntry.getKey();
        if (dvcMgr.isNullDevice(serial)) {
            switch (mCollectNullDevice) {
                case ALWAYS_INCLUDE:
                    return true;
                case IGNORE:
                    return false;
                case INCLUDE_IF_USED:
                    return mNullDeviceAllocated;
            }
        } else if (dvcMgr.isEmulator(serial)) {
            switch (mCollectEmulator) {
                case ALWAYS_INCLUDE:
                    return true;
                case IGNORE:
                    return false;
                case INCLUDE_IF_USED:
                    return mEmulatorAllocated;
            }
        }
        return true;
    }

    IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }
}
