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
package com.android.tradefed.command.remote;

import com.android.ddmlib.Log;
import com.android.tradefed.command.remote.RemoteOperation.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Class for sending remote commands to another TF process.
 * <p/>
 * Currently uses JSON-encoded data sent via sockets.
 */
public class RemoteClient {

    private static final String TAG = RemoteClient.class.getSimpleName();
    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;

    /**
     * Initialize the {@RemoteClient}, and instruct it to connect to the given port on
     * localhost.
     *
     * @param port the tcp/ip port number
     * @throws IOException
     * @throws UnknownHostException
     */
    RemoteClient(int port) throws UnknownHostException, IOException {
        this(InetAddress.getLocalHost().getHostName(), port);
    }

    /**
     * Initialize the {@RemoteClient}, and instruct it to connect to the given hostname and port.
     *
     * @param hostname to connect to
     * @param port the tcp/ip port number
     * @throws IOException
     * @throws UnknownHostException
     */
    RemoteClient(String hostName, int port) throws UnknownHostException, IOException {
        mSocket = new Socket(hostName, port);
        mWriter = new PrintWriter(mSocket.getOutputStream(), true);
        mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
    }

    /**
     * Send the given command to the remote TF.
     *
     * @param cmd the {@link RemoteOperation} to send
     * @return true if command was sent and processed successfully by remote TF
     */
    private synchronized boolean sendCommand(RemoteOperation cmd) {
       try {
           mWriter.println(cmd.pack());
           String response = mReader.readLine();
           return response != null && Boolean.parseBoolean(response);
       } catch (RemoteException e) {
           // TODO: convert to CLog once we have tf-common
          Log.e(TAG, "Failed to send remote commmand");
          Log.e(TAG, e);
       } catch (IOException e) {
           Log.e(TAG, "Failed to send remote commmand");
           Log.e(TAG, e);
       }
       return false;
    }

    /**
     * Helper method to create a {@link RemoteClient} connected to given port
     *
     * @param port the tcp/ip port
     * @return the {@link RemoteClient}
     * @throws UnknownHostException
     * @throws IOException
     */
    public static RemoteClient connect(int port) throws UnknownHostException, IOException {
        return new RemoteClient(port);
    }

    /**
     * Send a 'allocate device' command
     *
     * @param serial
     * @throws IOException
     */
    public boolean sendAllocateDevice(String serial) throws IOException {
        return sendCommand(new AllocateDeviceOp(serial));
    }

    /**
     * Send a 'free previously allocated device' command
     * @param serial
     * @throws IOException
     */
    public boolean sendFreeDevice(String serial) throws IOException {
        return sendCommand(new FreeDeviceOp(serial));
    }

    /**
     * Send a 'add command' command.
     *
     * @param commandArgs
     */
    public boolean sendAddCommand(long totalTime, String... commandArgs) throws IOException {
        return sendCommand(new AddCommandOp(totalTime, commandArgs));
    }

    /**
     * Send a 'close connection' command
     *
     * @throws IOException
     */
    public boolean sendClose() throws IOException {
        return sendCommand(new CloseOp());
    }

    /**
     * Send a 'handover close connection' command
     *
     * @param port of the remote manager to establish a connection with.
     * @return true if the command was accepted and completed, false otherwise.
     * @throws IOException
     */
    public boolean sendHandoverClose(int port) throws IOException {
        return sendCommand(new HandoverCloseOp(port));
    }

    /**
     * Close the connection to the {@link RemoteManager}.
     */
    public synchronized void close() {
        if (mSocket != null) {
             try {
                mSocket.close();
            } catch (IOException e) {
                Log.w(TAG, String.format("exception closing socket: %s", e.toString()));
            }
        }
        if (mWriter != null) {
            mWriter.close();
        }
    }
}

