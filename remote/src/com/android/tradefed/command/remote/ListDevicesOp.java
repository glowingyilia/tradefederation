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

import com.android.ddmlib.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote operation for listing all known devices and their state.
 */
class ListDevicesOp extends RemoteOperation {

    private static final String STATE = "state";
    private static final String SERIAL = "serial";
    private static final String SERIALS = "serials";
    private List<DeviceDescriptor> mDeviceList = new ArrayList<DeviceDescriptor>();

    ListDevicesOp() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unpackFromJson(JSONObject json) throws RemoteException, JSONException {
        // ignore, nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected OperationType getType() {
        return OperationType.LIST_DEVICES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void packIntoJson(JSONObject j) throws JSONException {
        // ignore, nothing to do
    }

    /**
     * Return the list of devices returned by remote TF. Will only be valid if {@link #hasError()}
     * is false.
     */
    public List<DeviceDescriptor> getDeviceStateMap() {
        return mDeviceList;
    }

    /**
     * Unpacks the response from remote TF manager into this object.
     */
    @Override
    protected void unpackResponseFromJson(JSONObject j) throws JSONException {
        JSONArray jsonDeviceStateArray = j.getJSONArray(SERIALS);
        for (int i = 0; i < jsonDeviceStateArray.length(); i++) {
            JSONObject deviceStateJson = jsonDeviceStateArray.getJSONObject(i);
            final String serial = deviceStateJson.getString(SERIAL);
            final String stateString = deviceStateJson.getString(STATE);
            try {

                mDeviceList.add(new DeviceDescriptor(serial, DeviceAllocationState
                        .valueOf(stateString)));
            } catch (IllegalArgumentException e) {
                String msg = String.format("unrecognized state %s for device %s", stateString,
                        serial);
                Log.e("ListDevicesOp", msg);
                throw new JSONException(msg);
            }
        }
    }

    /**
     * Packs the result from DeviceManager into the json response to send to remote client.
     */
    protected void packResponseIntoJson(List<DeviceDescriptor> devices,
            JSONObject result) throws JSONException {
        JSONArray jsonDeviceStateArray = new JSONArray();
        for (DeviceDescriptor descriptor : devices) {
            JSONObject deviceStateJson = new JSONObject();
            deviceStateJson.put(SERIAL, descriptor.getSerial());
            deviceStateJson.put(STATE, descriptor.getState().toString());
            jsonDeviceStateArray.put(deviceStateJson);
        }
        result.put(SERIALS, jsonDeviceStateArray);
    }
}
