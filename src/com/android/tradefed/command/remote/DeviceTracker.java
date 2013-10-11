/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tradefed.command.remote;

import com.android.tradefed.device.ITestDevice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

/**
 * Singleton class that tracks devices that have been remotely allocated.
 */
class DeviceTracker {

    /**
    * Use on demand holder idiom
    * @see http://en.wikipedia.org/wiki/Singleton_pattern#The_solution_of_Bill_Pugh
    */
    private static class SingletonHolder {
        public static final DeviceTracker cInstance = new DeviceTracker();
    }

    public static DeviceTracker getInstance() {
        return SingletonHolder.cInstance;
    }

    // private constructor - don't allow instantiation
    private DeviceTracker() {
    }

    // use Hashtable since its thread-safe
    private Map<String, ITestDevice> mAllocatedDeviceMap = new Hashtable<String, ITestDevice>();

    /**
     * Mark given device as remotely allocated.
     */
    public void allocateDevice(ITestDevice d) {
        mAllocatedDeviceMap.put(d.getSerialNumber(), d);
    }

    /**
     * Mark given device serial as freed.
     *
     * @return the corresponding {@link ITestDevice} or <code>null</code> if device with given
     *         serial cannot be found
     */
    public ITestDevice freeDevice(String serial) {
        return mAllocatedDeviceMap.remove(serial);
    }

    /**
     * Mark all remotely allocated devices as freed.
     *
     * @return a {@link Collection} of all remotely allocated devices
     */
    public Collection<ITestDevice> freeAll() {
        Collection<ITestDevice> devices = new ArrayList<ITestDevice>(mAllocatedDeviceMap.values());
        mAllocatedDeviceMap.clear();
        return devices;
    }
}
