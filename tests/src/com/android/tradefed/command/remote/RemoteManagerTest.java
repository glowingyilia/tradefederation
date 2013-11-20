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

import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.remote.RemoteManager;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link RemoteManager}.
 */
public class RemoteManagerTest extends TestCase {

    private IDeviceManager mMockDeviceManager;
    private RemoteManager mRemoteMgr;
    private RemoteClient mRemoteClient;
    private ICommandScheduler mMockScheduler;

    @Override
    protected void setUp() throws Exception {

        super.setUp();
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockScheduler = EasyMock.createMock(ICommandScheduler.class);
        mRemoteMgr = new RemoteManager(mMockDeviceManager, mMockScheduler);
        // Extra short timeout for testing.
        mRemoteMgr.setRemoteManagerTimeout(100);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mRemoteClient != null) {
            mRemoteClient.close();
        }
        if (mRemoteMgr != null) {
            mRemoteMgr.cancel();
            // We want to make sure we completely close down the remotemanager before moving on to
            // to the next test.
            mRemoteMgr.join();
        }
        super.tearDown();
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then free a device.
     */
    public void testAllocateFree() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice("serial")).andReturn(device);
        mMockDeviceManager.freeDevice(EasyMock.eq(device),
                EasyMock.eq(FreeDeviceState.AVAILABLE));

        EasyMock.replay(mMockDeviceManager, device);
        mRemoteMgr.connect();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        assertTrue(mRemoteClient.sendAllocateDevice("serial"));
        assertTrue(mRemoteClient.sendFreeDevice("serial"));
        EasyMock.verify(mMockDeviceManager);
    }

    /**
     * An integration test for client-manager interaction, that will add a command
     */
    public void testAddCommand() throws Exception {
        EasyMock.expect(mMockScheduler.addCommand(EasyMock.aryEq(new String[] {
                "arg1", "arg2"
        }), EasyMock.anyInt())).andReturn(true);

        EasyMock.replay(mMockScheduler);
        mRemoteMgr.connect();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        assertTrue(mRemoteClient.sendAddCommand(3, "arg1", "arg2"));
        EasyMock.verify(mMockScheduler);
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then close the
     * connection. Verifies that closing frees all devices.
     */
    public void testAllocateClose() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice("serial")).andReturn(device);
        mMockDeviceManager.freeDevice(EasyMock.eq(device),
                EasyMock.eq(FreeDeviceState.AVAILABLE));

        EasyMock.replay(mMockDeviceManager, device);
        mRemoteMgr.connect();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        assertTrue(mRemoteClient.sendAllocateDevice("serial"));
        assertTrue(mRemoteClient.sendClose());
        mRemoteClient.close();
        mRemoteMgr.join();
        EasyMock.verify(mMockDeviceManager);
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then frees all
     * devices.
     */
    public void testAllocateFreeAll() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice("serial")).andReturn(device);
        mMockDeviceManager.freeDevice(EasyMock.eq(device),
                EasyMock.eq(FreeDeviceState.AVAILABLE));

        EasyMock.replay(mMockDeviceManager, device);
        mRemoteMgr.connect();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        assertTrue(mRemoteClient.sendAllocateDevice("serial"));
        assertTrue(mRemoteClient.sendFreeDevice("*"));
        EasyMock.verify(mMockDeviceManager);
    }

}
