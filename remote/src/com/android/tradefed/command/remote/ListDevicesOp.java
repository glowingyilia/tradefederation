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
import com.android.tradefed.device.DeviceAllocationState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote operation for listing all known devices and their state.
 */
class ListDevicesOp extends RemoteOperation<List<DeviceDescriptor>> {

    private static final String STATE = "state";
    private static final String SERIAL = "serial";
    private static final String SERIALS = "serials";
    private static final String PRODUCT_VARIANT = "variant";

    ListDevicesOp() {
    }

    /**
     * Factory method for creating a {@link ListDevicesOp} from JSON data.
     *
     * @param json the data as a {@link JSONObject}
     * @return a {@link ListDevicesOp}
     * @throws JSONException if failed to extract out data
     */
    static ListDevicesOp createFromJson(JSONObject json) throws JSONException {
        return new ListDevicesOp();
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
     * Unpacks the response from remote TF manager into this object.
     */
    @Override
    protected List<DeviceDescriptor> unpackResponseFromJson(JSONObject j) throws JSONException {
        List<DeviceDescriptor> deviceList = new ArrayList<DeviceDescriptor>();
        JSONArray jsonDeviceStateArray = j.getJSONArray(SERIALS);
        for (int i = 0; i < jsonDeviceStateArray.length(); i++) {
            JSONObject deviceStateJson = jsonDeviceStateArray.getJSONObject(i);
            final String serial = deviceStateJson.getString(SERIAL);
            final String stateString = deviceStateJson.getString(STATE);
            final String productVariant= deviceStateJson.getString(PRODUCT_VARIANT);
            try {
                deviceList.add(new DeviceDescriptor(serial, DeviceAllocationState
                        .valueOf(stateString), productVariant));
            } catch (IllegalArgumentException e) {
                String msg = String.format("unrecognized state %s for device %s", stateString,
                        serial);
                Log.e("ListDevicesOp", msg);
                throw new JSONException(msg);
            }
        }
        return deviceList;
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
            deviceStateJson.put(PRODUCT_VARIANT, descriptor.getProductVariant());
            jsonDeviceStateArray.put(deviceStateJson);
        }
        result.put(SERIALS, jsonDeviceStateArray);
    }
}
