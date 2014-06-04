/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tradefed.utils.wifi;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * An instrumentation class to manipulate Wi-Fi services on device.
 * <p/>
 * adb shell am instrument -e method (method name) -e arg1 val1 -e arg2 val2
 * -w com.android.tradefed.utils.wifi/.WifiUtils
 */
public class WifiUtil extends Instrumentation {
    // FIXME: document exposed API methods and arguments
    private static final String TAG = "WifiUtil";

    private static final long DEFAULT_TIMEOUT = 30 * 1000;
    private static final long POLL_TIME = 1000;
    private static final String DEFAULT_URL_TO_CHECK = "http://www.google.com";

    private Bundle mArguments;
    private WifiManager mWifiManager = null;

    static class MissingArgException extends Exception {
        public MissingArgException(String msg) {
            super(msg);
        }

        public static MissingArgException fromArg(String arg) {
            return new MissingArgException(
                    String.format("Error: missing mandatory argument '%s'", arg));
        }
    }

    /**
     * Thrown when an error occurs while manipulating Wi-Fi services.
     */
    static class WifiException extends Exception {
        public WifiException(String msg) {
            super(msg);
        }
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        start();
    }

    private static String quote(String str) {
        return String.format("\"%s\"", str);
    }

    /**
     * Fails an instrumentation request.
     *
     * @param errMsg an error message
     */
    private void fail(String errMsg) {
        Log.e(TAG, errMsg);
        Bundle result = new Bundle();
        result.putString("error", errMsg);
        finish(Activity.RESULT_CANCELED, result);
    }

    /**
     * Returns the string value of an argument for the specified name, or throws
     * {@link MissingArgException} if the argument is not found or empty.
     *
     * @param arg the name of an argument
     * @return the value of an argument
     * @throws MissingArgException if the argument is not found
     */
    private String expectString(String arg) throws MissingArgException {
        String val = mArguments.getString(arg);
        if (TextUtils.isEmpty(val)) {
            throw MissingArgException.fromArg(arg);
        }

        return val;
    }

    /**
     * Returns the value of a string argument for the specified name, or defaultValue if the
     * argument is not found or empty.
     *
     * @param arg the name of an argument
     * @param defaultValue a value to return if the argument is not found
     * @return the value of an argument
     */
    private String getString(String arg, String defaultValue) {
        String val = mArguments.getString(arg);
        if (TextUtils.isEmpty(val)) {
            return defaultValue;
        }

        return val;
    }

    /**
     * Returns the integer value of an argument for the specified name, or throws
     * {@link MissingArgException} if the argument is not found or cannot be parsed to an integer.
     *
     * @param arg the name of an argument
     * @return the value of an argument
     * @throws MissingArgException if the argument is not found
     */
    private int expectInteger(String arg) throws MissingArgException {
        String val = expectString(arg);
        int intVal;
        try {
            intVal = Integer.parseInt(val);
        } catch (NumberFormatException e) {
            final String msg = String.format("Couldn't parse arg '%s': %s", arg,
                    e.getMessage());
            throw new MissingArgException(msg);
        }

        return intVal;
    }

    /**
     * Waits until an expected condition is satisfied for DEFAULT_TIMEOUT.
     *
     * @param checker a <code>Callable</code> to check the expected condition
     * @throws WifiException if DEFAULT_TIMEOUT expires
     */
    private void waitForCallable(final Callable<Boolean> checker, final String timeoutMsg)
            throws WifiException {

        try {
            long endTime = System.currentTimeMillis() + DEFAULT_TIMEOUT;

            while (System.currentTimeMillis() < endTime) {
                if (checker.call()) {
                    return;
                }
                Thread.sleep(POLL_TIME);
            }
        } catch (final Exception e) {
            // swallow to throw WifiException
        }
        throw new WifiException(timeoutMsg);
    }

    /**
     * Adds a Wi-Fi network configuration.
     *
     * @param ssid SSID of a Wi-Fi network
     * @param psk PSK(Pre-Shared Key) of a Wi-Fi network. This can be null if the given SSID is for
     *            an open network.
     * @return the network ID of a new network configuration
     * @throws WifiException if the operation fails
     */
    private int addNetwork(final String ssid, final String psk) throws WifiException {

        final WifiConfiguration config = new WifiConfiguration();
        // A string SSID _must_ be enclosed in double-quotation marks
        config.SSID = quote(ssid);

        if (psk == null) {
            // KeyMgmt should be NONE only
            final BitSet keymgmt = new BitSet();
            keymgmt.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedKeyManagement = keymgmt;
        } else {
            config.preSharedKey = quote(psk);
        }

        final int networkId = mWifiManager.addNetwork(config);
        if (-1 == networkId) {
            throw new WifiException("failed to add network");
        }

        return networkId;
    }

    /**
     * Removes all Wi-Fi network configurations.
     *
     * @param throwIfFail <code>true</code> if a caller wants an exception to be thrown when the
     *            operation fails. Otherwise <code>false</code>.
     * @throws WifiException if the operation fails
     */
    private void removeAllNetworks(boolean throwIfFail) throws WifiException {
        List<WifiConfiguration> netlist = mWifiManager.getConfiguredNetworks();
        if (netlist != null) {
            int failCount = 0;
            for (WifiConfiguration config : netlist) {
                if (!mWifiManager.removeNetwork(config.networkId)) {
                    Log.w(TAG, String.format("failed to remove network id %d (SSID = %s)",
                            config.networkId, config.SSID));
                    failCount++;
                }
            }
            if (0 < failCount && throwIfFail) {
                throw new WifiException("failed to remove all networks.");
            }
        }
    }

    /**
     * Check network connectivity by sending a HTTP request to a given URL.
     *
     * @param urlToCheck URL to send a test request to
     * @return <code>true</code> if the test request succeeds. Otherwise <code>false</code>.
     */
    private boolean checkConnectivity(final String urlToCheck) {
        final HttpClient httpclient = new DefaultHttpClient();
        try {
            httpclient.execute(new HttpGet(urlToCheck));
        } catch (final IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Connects a device to a given Wi-Fi network and check connectivity.
     *
     * @param ssid SSID of a Wi-Fi network
     * @param psk PSK of a Wi-Fi network
     * @param urlToCheck URL to use when checking connectivity
     * @throws WifiException if the operation fails
     */
    private void connectToNetwork(final String ssid, final String psk, final String urlToCheck)
            throws WifiException {
        if (!mWifiManager.setWifiEnabled(true)) {
            throw new WifiException("failed to enable wifi");
        }

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mWifiManager.isWifiEnabled();
                }
            }, "failed to enable wifi");

        removeAllNetworks(false);

        final int networkId = addNetwork(ssid, psk);
        if (!mWifiManager.enableNetwork(networkId, true)) {
            throw new WifiException(String.format("failed to enable network %s", ssid));
        }
        if (!mWifiManager.saveConfiguration()) {
            throw new WifiException(String.format("failed to save configuration", ssid));
        }

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final SupplicantState state = mWifiManager.getConnectionInfo()
                            .getSupplicantState();
                    return SupplicantState.COMPLETED == state;
                }
            }, String.format("failed to associate to network %s", ssid));

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final WifiInfo info = mWifiManager.getConnectionInfo();
                    return 0 != info.getIpAddress();
                }
            }, String.format("dhcp timeout when connecting to wifi network %s", ssid));

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return checkConnectivity(urlToCheck);
                }
            }, String.format("request to %s failed after connecting to wifi network %s",
                    urlToCheck, ssid));
    }

    /**
     * Disconnects a device from Wi-Fi network and disable Wi-Fi.
     *
     * @throws WifiException if the operation fails
     */
    private void disconnectFromNetwork() throws WifiException {
        if (mWifiManager.isWifiEnabled()) {
            removeAllNetworks(false);
            if (!mWifiManager.setWifiEnabled(false)) {
                throw new WifiException("failed to disable wifi");
            }
            waitForCallable(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return !mWifiManager.isWifiEnabled();
                    }
                }, "failed to disable wifi");
        }
    }

    /**
     * Returns Wi-Fi information of a device.
     *
     * @return a {@link JSONObject} containing the current Wi-Fi status
     * @throws WifiException if the operation fails
     */
    private JSONObject getWifiInfo() throws WifiException {
        final JSONObject json = new JSONObject();

        try {
            final WifiInfo info = mWifiManager.getConnectionInfo();
            json.put("ssid", info.getSSID());
            json.put("bssid", info.getBSSID());
            final int addr = info.getIpAddress();
            // IP address is stored with the first octet in the lowest byte
            final int a = (addr >> 0) & 0xff;
            final int b = (addr >> 8) & 0xff;
            final int c = (addr >> 16) & 0xff;
            final int d = (addr >> 24) & 0xff;
            json.put("ipAddress", String.format("%s.%s.%s.%s", a, b, c, d));
            json.put("linkSpeed", info.getLinkSpeed());
            json.put("rssi", info.getRssi());
            json.put("macAddress", info.getMacAddress());
        } catch (final JSONException e) {
            throw new WifiException(e.toString());
        }

        return json;
    }

    @Override
    public void onStart() {
        super.onStart();
        final Bundle result = new Bundle();

        try {
            final String method = expectString("method");

            mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
            if (mWifiManager == null) {
                fail("Couldn't get WifiManager reference; goodbye!");
                return;
            }

            // As a pattern, method implementations below should gather arguments _first_, and then
            // use those arguments so that the system is not left in an inconsistent state if an
            // argument is missing in the middle of an implementation.
            if ("enableWifi".equals(method)) {
                result.putBoolean("result", mWifiManager.setWifiEnabled(true));
            } else if ("disableWifi".equals(method)) {
                result.putBoolean("result", mWifiManager.setWifiEnabled(false));
            } else if ("addOpenNetwork".equals(method)) {
                final String ssid = expectString("ssid");

                result.putInt("result", addNetwork(ssid, null));

            } else if ("addWpaPskNetwork".equals(method)) {
                final String ssid = expectString("ssid");
                final String psk = expectString("psk");

                result.putInt("result", addNetwork(ssid, psk));

            } else if ("associateNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result",
                        mWifiManager.enableNetwork(id, true /* disable other networks */));

            } else if ("disconnect".equals(method)) {
                result.putBoolean("result", mWifiManager.disconnect());

            } else if ("disableNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result", mWifiManager.disableNetwork(id));

            } else if ("isWifiEnabled".equals(method)) {
                result.putBoolean("result", mWifiManager.isWifiEnabled());

            } else if ("getIpAddress".equals(method)) {
                final WifiInfo info = mWifiManager.getConnectionInfo();
                final int addr = info.getIpAddress();

                // IP address is stored with the first octet in the lowest byte
                final int a = (addr >> 0) & 0xff;
                final int b = (addr >> 8) & 0xff;
                final int c = (addr >> 16) & 0xff;
                final int d = (addr >> 24) & 0xff;

                result.putString("result", String.format("%s.%s.%s.%s", a, b, c, d));

            } else if ("getSSID".equals(method)) {
                final WifiInfo info = mWifiManager.getConnectionInfo();

                result.putString("result", info.getSSID());

            } else if ("getBSSID".equals(method)) {
                final WifiInfo info = mWifiManager.getConnectionInfo();

                result.putString("result", info.getBSSID());

            } else if ("removeAllNetworks".equals(method)) {
                removeAllNetworks(true);

                result.putBoolean("result", true);

            } else if ("removeNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result", mWifiManager.removeNetwork(id));

            } else if ("saveConfiguration".equals(method)) {
                result.putBoolean("result", mWifiManager.saveConfiguration());

            } else if ("getSupplicantState".equals(method)) {
                String state = mWifiManager.getConnectionInfo().getSupplicantState().name();
                result.putString("result", state);

            } else if ("checkConnectivity".equals(method)) {
                final String url = getString("urlToCheck", DEFAULT_URL_TO_CHECK);

                result.putBoolean("result", checkConnectivity(url));

            } else if ("connectToNetwork".equals(method)) {
                final String ssid = expectString("ssid");
                final String psk = getString("psk", null);
                final String pingUrl = getString("urlToCheck", DEFAULT_URL_TO_CHECK);

                connectToNetwork(ssid, psk, pingUrl);

                result.putBoolean("result", true);

            } else if ("disconnectFromNetwork".equals(method)) {
                disconnectFromNetwork();

                result.putBoolean("result", true);

            } else if ("getWifiInfo".equals(method)) {
                result.putString("result", getWifiInfo().toString());

            } else if ("startMonitor".equals(method)) {
                final int interval = expectInteger("interval");
                final String urlToCheck = getString("urlToCheck", DEFAULT_URL_TO_CHECK);

                WifiMonitorService.enable(getContext(), interval, urlToCheck);

                result.putBoolean("result", true);

            } else if ("stopMonitor".equals(method)) {
                final Context context = getContext();
                WifiMonitorService.disable(context);

                result.putString("result", WifiMonitorService.getData(context));

            } else {
                fail(String.format("Didn't recognize method '%s'", method));
                return;
            }
        } catch (WifiException | MissingArgException e) {
            fail(e.getMessage());
            return;
        }

        finish(Activity.RESULT_OK, result);
    }
}
