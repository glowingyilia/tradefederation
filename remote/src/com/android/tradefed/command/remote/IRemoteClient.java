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

package com.android.tradefed.command.remote;

import java.io.IOException;
import java.util.List;

/**
 * Interface for sending remote TradeFederation commands.
 */
public interface IRemoteClient {

    /**
     * Send a 'allocate device' command
     *
     * @param serial
     * @throws IOException
     */
    public boolean sendAllocateDevice(String serial) throws IOException;

    /**
     * Send a 'free previously allocated device' command
     *
     * @param serial
     * @throws IOException
     */
    public boolean sendFreeDevice(String serial) throws IOException;

    /**
     * Send a 'add command' command.
     *
     * @param commandArgs
     * @throws IOException
     */
    public boolean sendAddCommand(long totalTime, String... commandArgs) throws IOException;

    /**
     * Send a 'close connection' command
     *
     * @throws IOException
     */
    public boolean sendClose() throws IOException;

    /**
     * Send a 'handover close connection' command
     *
     * @param port of the remote manager to establish a connection with.
     * @return true if the command was accepted and completed, false otherwise.
     * @throws IOException
     */
    public boolean sendHandoverClose(int port) throws IOException;

    /**
     * Send a 'list devices' request to remote TF
     *
     * @return a list of device serials and their state. Returns <code>null</code> if command failed.
     */
    public List<DeviceDescriptor> sendListDevices();

    /**
     * Close the connection to the {@link RemoteManager}.
     */
    public void close();
}
