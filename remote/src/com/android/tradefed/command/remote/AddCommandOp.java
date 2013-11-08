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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Remote operation for adding a command to the local tradefed scheduler.
 */
class AddCommandOp extends RemoteOperation {

    private static final String COMMAND_ARGS = "commandArgs";
    private static final String TIME = "time";
    long mTotalTime;
    String[] mCommandArgs;

    AddCommandOp() {
        this(0, new String[]{});
    }

    AddCommandOp(long totalTime, String... commandArgs) {
        mTotalTime = totalTime;
        mCommandArgs = commandArgs;
    }

    @Override
    protected void unpackFromJson(JSONObject json) throws RemoteException, JSONException {
        mTotalTime = json.getLong(TIME);
        JSONArray jsonArgs = json.getJSONArray(COMMAND_ARGS);
        mCommandArgs = new String[jsonArgs.length()];
        for (int i=0; i < mCommandArgs.length; i++) {
            mCommandArgs[i] = jsonArgs.getString(i);
        }
    }


    @Override
    protected OperationType getType() {
        return OperationType.ADD_COMMAND;
    }

    @Override
    protected void packIntoJson(JSONObject j) throws JSONException {
        j.put(TIME, mTotalTime);
        JSONArray jsonArgs = new JSONArray();
        for (String arg : mCommandArgs) {
            jsonArgs.put(arg);
        }
        j.put(COMMAND_ARGS, jsonArgs);
    }
}
