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

import java.util.List;

/**
 * A proxy class to propagate requests to multiple {@link IDeviceMonitor}s.
 */
public class DeviceMonitorMultiplexer implements IDeviceMonitor {

    private List<IDeviceMonitor> mDeviceMonitors;

    /**
     * Creates a proxy instance for given {@link IDeviceMonitor}s.
     *
     * @param deviceMonitors a list of {@link IDeviceMonitor}s
     */
    public DeviceMonitorMultiplexer(List<IDeviceMonitor> deviceMonitors) {
        if (deviceMonitors == null) {
            throw new IllegalArgumentException("deviceMonitors cannot be null");
        }
        mDeviceMonitors = deviceMonitors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        for (IDeviceMonitor monitor : mDeviceMonitors) {
            monitor.run();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        for (IDeviceMonitor monitor : mDeviceMonitors) {
            monitor.setDeviceLister(lister);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDeviceStateChange(String serial, DeviceAllocationState oldState,
            DeviceAllocationState newState) {
        for (IDeviceMonitor monitor : mDeviceMonitors) {
            monitor.notifyDeviceStateChange(serial, oldState, newState);
        }
    }

}
