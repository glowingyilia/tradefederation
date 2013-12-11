package com.android.tradefed.command.remote;

public class DeviceDescriptor {

    private final String mSerial;
    private final DeviceAllocationState mState;

    public DeviceDescriptor(String serial, DeviceAllocationState state) {
        mSerial = serial;
        mState = state;
    }

    public String getSerial() {
        return mSerial;
    }

    public DeviceAllocationState getState() {
        return mState;
    }
}
