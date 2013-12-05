/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.framework.tests;

import com.android.tradefed.log.LogUtil.CLog;

/**
 * Simple container class used to store network Stats.
 */
public class BandwidthStats {
    private long mRxBytes = 0;
    private long mRxPackets = 0;
    private long mTxBytes = 0;
    private long mTxPackets = 0;

    public BandwidthStats (long rxBytes, long rxPackets, long txBytes, long txPackets) {
        mRxBytes = rxBytes;
        mRxPackets = rxPackets;
        mTxBytes = txBytes;
        mTxPackets = txPackets;
    }

    public BandwidthStats() {
    }

    /**
     * Compute percent difference between a and b.
     * @param a
     * @param b
     * @return % difference of a and b.
     */
    static float computePercentDifference(float a, float b) {
        if (a == b) {
            return 0;
        }
        if (a == 0) {
            CLog.d("Invalid value for a: %f", a);
            return Float.MIN_VALUE;
        }
        return ( a - b) / a * 100;
    }

    public long getRxBytes() {
        return mRxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.mRxBytes = rxBytes;
    }

    public long getRxPackets() {
        return mRxPackets;
    }

    public void setRxPackets(long rxPackets) {
        this.mRxPackets = rxPackets;
    }

    public long getTxBytes() {
        return mTxBytes;
    }

    public void setTxBytes(long txBytes) {
        this.mTxBytes = txBytes;
    }

    public long getTxPackets() {
        return mTxPackets;
    }

    public void setTxPackets(long txPackets) {
        this.mTxPackets = txPackets;
    }

    public boolean compareAll(BandwidthStats stats, float mDifferenceThreshold) {
        return this.compareRb(stats, mDifferenceThreshold) &&
                this.compareRp(stats, mDifferenceThreshold) &&
                this.compareTb(stats, mDifferenceThreshold) &&
                this.compareTp(stats, mDifferenceThreshold);
    }

    private boolean compareTp(BandwidthStats stats, float mDifferenceThreshold) {
        return BandwidthStats.computePercentDifference(
                this.mTxPackets, stats.mTxPackets) < mDifferenceThreshold;
    }

    private boolean compareTb(BandwidthStats stats, float mDifferenceThreshold) {
        return BandwidthStats.computePercentDifference(
                this.mTxBytes, stats.mTxBytes) < mDifferenceThreshold;
    }

    private boolean compareRp(BandwidthStats stats, float mDifferenceThreshold) {
        return BandwidthStats.computePercentDifference(
                this.mRxPackets, stats.mRxPackets) < mDifferenceThreshold;
    }

    private boolean compareRb(BandwidthStats stats, float mDifferenceThreshold) {
        return BandwidthStats.computePercentDifference(
                this.mTxBytes, stats.mTxBytes) < mDifferenceThreshold;
    }
}
