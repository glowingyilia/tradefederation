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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulates data for a remote operation sent over the wire.
 */
abstract class RemoteOperation {
    private static final String TYPE = "type";
    private static final String VERSION = "version";
    static final int CURRENT_PROTOCOL_VERSION = 1;
    private static final String TAG = RemoteOperation.class.getSimpleName();

    @SuppressWarnings("serial")
    static class RemoteException extends Exception {
        RemoteException(Throwable t) {
            super(t);
        }

        RemoteException(String msg) {
            super(msg);
        }
    }

    /**
     * Represents all types of remote operations that can be performed
     */
    enum OperationType {
        ALLOCATE_DEVICE, FREE_DEVICE, CLOSE, ADD_COMMAND
    }

    /**
     * Create and populate a {@link RemoteOperation} from given data.
     *
     * @param data the data to parse
     * @throws RemoteException
     */
    final static RemoteOperation createRemoteOpFromString(String data) throws RemoteException {
        try {
            JSONObject jsonData = new JSONObject(data);
            int protocolVersion = jsonData.getInt(VERSION);
            // to keep things simple for now, just barf when protocol version is unknown
            if (protocolVersion != CURRENT_PROTOCOL_VERSION) {
                throw new RemoteException(String.format(
                        "Remote operation has unknown version '%d'. Expected '%d'",
                        protocolVersion, CURRENT_PROTOCOL_VERSION));
            }
            OperationType op = OperationType.valueOf(jsonData.getString(TYPE));
            RemoteOperation rc = null;
            switch (op) {
                case ALLOCATE_DEVICE:
                    rc = new AllocateDeviceOp();
                    break;
                case FREE_DEVICE:
                    rc = new FreeDeviceOp();
                    break;
                case CLOSE:
                    rc = new CloseOp();
                    break;
                case ADD_COMMAND:
                    rc = new AddCommandOp();
                    break;
                default:
                    throw new RemoteException(String.format("unknown remote command '%s'", data));

            }
            rc.unpackFromJson(jsonData);
            return rc;
        } catch (JSONException e) {
            throw new RemoteException(e);
        }
    }

    protected abstract OperationType getType();

    /**
     * Abstract method to allow sub-classes to parse additional data from payload.
     *
     * @param json
     * @throws RemoteException, JSONException
     */
    protected abstract void unpackFromJson(JSONObject json) throws RemoteException, JSONException;

    /**
     * Return the RemoteCommand data in its wire protocol format
     * @return
     */
     String pack() throws RemoteException {
         JSONObject j = new JSONObject();
         try {
             j.put(VERSION, CURRENT_PROTOCOL_VERSION);
             j.put(TYPE, getType().toString());
             packIntoJson(j);
         } catch (JSONException e) {
             Log.e(TAG, "Failed to serialize RemoteOperation");
             Log.e(TAG,  e);
         }
         return j.toString();
     }

     /**
      * Callback to add subclass specific data to the JSON object
      * @param j
      * @throws JSONException
      */
    protected abstract void packIntoJson(JSONObject j) throws JSONException;

}
