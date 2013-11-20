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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.remote.RemoteOperation.RemoteException;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Class that receives {@link RemoteOperation}s via a socket.
 * <p/>
 * Currently accepts only one remote connection at one time, and processes incoming commands
 * serially.
 * <p/>
 * Usage:
 * <pre>
 * RemoteManager r = new RemoteManager(deviceMgr, scheduler);
 * r.connect();
 * r.start();
 * int port = r.getPort();
 * ... inform client of port to use. Shuts down when instructed by client or on #cancel()
 * </pre>
 */
@OptionClass(alias = "remote-manager")
public class RemoteManager extends Thread {

    private ServerSocket mServerSocket = null;
    private boolean mCancel = false;
    private final IDeviceManager mDeviceManager;
    private final ICommandScheduler mScheduler;

    // choose an arbitrary default port that according to the interweb is not used by another
    // popular program
    public static final int DEFAULT_PORT = 30103;

    @Option(name = "start-remote-mgr",
            description = "Whether or not to start a remote manager on boot.")
    private static boolean mStartRemoteManagerOnBoot = false;

    @Option(name = "auto-handover",
            description = "Whether or not to start handover if there is another instance of " +
                          "Tradefederation running on the machine")
    private static boolean mAutoHandover = false;

    @Option(name = "remote-mgr-port",
            description = "The remote manager port to use.")
    private static int mRemoteManagerPort = DEFAULT_PORT;

    @Option(name = "remote-mgr-socket-timeout-ms",
            description = "Timeout for when accepting connections with the remote manager socket.")
    private static int mSocketTimeout = 5000;

    public boolean getStartRemoteMgrOnBoot() {
        return mStartRemoteManagerOnBoot;
    }

    public int getRemoteManagerPort() {
        return mRemoteManagerPort;
    }

    public void setRemoteManagerPort(int port) {
        mRemoteManagerPort = port;
    }

    public void setRemoteManagerTimeout(int timeout) {
        mSocketTimeout = timeout;
    }

    public boolean getAutoHandover() {
        return mAutoHandover;
    }

    public RemoteManager() {
        mDeviceManager = null;
        mScheduler = null;
    }

    /**
     * Creates a {@link RemoteManager}.
     *
     * @param manager the {@link IDeviceManager} to use to allocate and free devices.
     * @param scheduler the {@link ICommandScheduler} to use to schedule commands.
     */
    public RemoteManager(IDeviceManager manager, ICommandScheduler scheduler) {
        mDeviceManager = manager;
        mScheduler = scheduler;
    }

    /**
     * Attempts to init server and connect it to a port.
     * @return true if we successfully connect the server to the default port.
     */
    public boolean connect() {
        return connect(mRemoteManagerPort);
    }

    /**
     * Attemps to connect to any free port.
     * @return true if we successfully connected to the port, false otherwise.
     */
    public boolean connectAnyPort() {
        return connect(0);
    }

    /**
     * Attempts to connect server to a given port.
     * @return true if we successfully connect to the port, false otherwise.
     */
    protected boolean connect(int port) {
        mServerSocket = openSocket(port);
        return mServerSocket != null;
    }

    /**
     * Attempts to open server socket at given port.
     * @param port to open the socket at.
     * @return the ServerSocket or null if attempt failed.
     */
    private ServerSocket openSocket(int port) {
        try {
            return new ServerSocket(port);
        } catch (IOException e) {
            CLog.e("Failed to open server socket: %s", e);
            return null;
        }
    }


    /**
     * The main thread body of the remote manager.
     * <p/>
     * Creates a server socket, and waits for client connections.
     */
    @Override
    public void run() {
        if (mServerSocket == null) {
            CLog.e("Started remote manager thread without connecting");
            return;
        }
        try {
            // Set a timeout as we don't want to be blocked listening for connections,
            // we could receive a request for cancel().
            mServerSocket.setSoTimeout(mSocketTimeout);
            processClientConnections(mServerSocket);
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            CLog.e("Error when setting socket timeout :%s", e);
        } finally {
            freeAllDevices();
            closeSocket(mServerSocket);
        }
    }

    /**
     * Gets the socket port the remote manager is listening on, blocking for a short time if
     * necessary.
     * <p/>
     * {@link #start()} should be called before this method.
     * @return
     */
    public synchronized int getPort() {
        if (mServerSocket == null) {
            try {
                wait(10*1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (mServerSocket == null) {
            return -1;
        }
        return mServerSocket.getLocalPort();
    }

    private void processClientConnections(ServerSocket serverSocket) {
        while (!mCancel) {
            Socket clientSocket = null;
            BufferedReader in = null;
            PrintWriter out = null;
            try {
                clientSocket = serverSocket.accept();
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                processClientOperations(in, out);
            } catch (SocketTimeoutException e) {
                // ignore.
            } catch (IOException e) {
                CLog.e("Failed to accept connection: %s", e);
            } finally {
                closeReader(in);
                closeWriter(out);
                closeSocket(clientSocket);
            }
        }
    }

    private void processClientOperations(BufferedReader in, PrintWriter out) throws IOException {
        String line = null;
        while ((line = in.readLine()) != null && !mCancel) {
            boolean result = false;
            RemoteOperation rc;
            try {
                rc = RemoteOperation.createRemoteOpFromString(line);
                switch (rc.getType()) {
                    case ADD_COMMAND:
                        result = processAdd((AddCommandOp)rc);
                        break;
                    case CLOSE:
                        result = processClose((CloseOp)rc);
                        break;
                    case ALLOCATE_DEVICE:
                        result = processAllocate((AllocateDeviceOp)rc);
                        break;
                    case FREE_DEVICE:
                        result = processFree((FreeDeviceOp)rc);
                        break;
                    case HANDOVER_CLOSE:
                        result = processHandoverClose((HandoverCloseOp)rc);
                        break;
                }
            } catch (RemoteException e) {
                CLog.e("Failed to handle remote command", e);
            }
            sendAck(result, out);
        }
    }

    private boolean processHandoverClose(HandoverCloseOp c) {
        int port = c.mPort;
        CLog.logAndDisplay(LogLevel.INFO, "Handling Handover Close OP with port %d", port);
        boolean success = false;
        if (port > 0) {
            success = mScheduler.handoverShutdown(port);
        }
        return success;
    }


    private boolean processAllocate(AllocateDeviceOp c) {
        ITestDevice allocatedDevice = mDeviceManager.forceAllocateDevice(c.mDeviceSerial);
        if (allocatedDevice != null) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Allocating device %s that is still in use by remote tradefed",
                            c.mDeviceSerial);
            DeviceTracker.getInstance().allocateDevice(allocatedDevice);
            return true;
        }
        CLog.e("Failed to allocate device %s", c.mDeviceSerial);
        return false;
    }

    private boolean processFree(FreeDeviceOp c) {
        if (FreeDeviceOp.ALL_DEVICES.equals(c.mDeviceSerial)) {
            freeAllDevices();
            return true;
        } else {
            ITestDevice d = DeviceTracker.getInstance().freeDevice(c.mDeviceSerial);
            if (d != null) {
                CLog.logAndDisplay(LogLevel.INFO,
                        "Freeing device %s no longer in use by remote tradefed",
                                c.mDeviceSerial);
                mDeviceManager.freeDevice(d, FreeDeviceState.AVAILABLE);
                return true;
            } else {
                CLog.w("Could not find device to free %s", c.mDeviceSerial);
            }
        }
        return false;
    }

    boolean processAdd(AddCommandOp c) {
        CLog.logAndDisplay(LogLevel.INFO, "Adding command '%s'", ArrayUtil.join(" ",
                c.mCommandArgs));
        try {
            return mScheduler.addCommand(c.mCommandArgs, c.mTotalTime);
        } catch (ConfigurationException e) {
            CLog.e("Failed to add command");
            CLog.e(e);
            return false;
        }
    }

    private boolean processClose(CloseOp rc) {
        cancel();
        return true;
    }

    private void freeAllDevices() {
        for (ITestDevice d : DeviceTracker.getInstance().freeAll()) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Freeing device %s no longer in use by remote tradefed",
                            d.getSerialNumber());
            mDeviceManager.freeDevice(d, FreeDeviceState.AVAILABLE);
        }
    }

    private void sendAck(boolean result, PrintWriter out) {
        out.println(result);
    }

    /**
     * Cancel the remote manager.
     */
    public synchronized void cancel() {
        if (!mCancel) {
            mCancel  = true;
            CLog.logAndDisplay(LogLevel.INFO, "Closing remote manager at port %d", getPort());
        }
    }

    private void closeSocket(ServerSocket serverSocket) {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                CLog.e("Failed to close socket: %s", e);
            }
        }
    }

    private void closeSocket(Socket clientSocket) {
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
                e.printStackTrace();
            }
        }
    }

    private void closeReader(BufferedReader in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeWriter(PrintWriter out) {
        if (out != null) {
            out.close();
        }
    }

    /**
     * @return <code>true</code> if a cancel has been requested
     */
    public boolean isCanceled() {
        return mCancel;
    }
}
