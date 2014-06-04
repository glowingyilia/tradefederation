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

package com.android.tradefed.utils.wifi;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * A class to monitor wifi connectivity for a period of time.
 * <p/>
 * Once it is started, this will send a HTTP request to the specified URL every interval and record
 * latencies. The latency history can be retrieved by {@link WifiMonitorService#getData(Context)}. This
 * class is used to support "startMonitor" and "stopMonitor" commands.
 */
public class WifiMonitorService extends IntentService {

    private static final String TAG = "WifiUtil." + WifiMonitorService.class.getSimpleName();

    private static final String DATA_FILE = "monitor.dat";
    private static final long MAX_DATA_FILE_SIZE = 1024 * 1024;
    private static final String URL_TO_CHECK = "urlTocheck";

    /**
     * Constructor.
     */
    public WifiMonitorService() {
        super(WifiMonitorService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        FileOutputStream out = null;
        try {
            final File file = getFileStreamPath(DATA_FILE);
            if (MAX_DATA_FILE_SIZE < file.length()) {
                Log.i(TAG, "data file is too big. clearing...");
                clearData(this);
            }

            final String urlToCheck = intent.getStringExtra(URL_TO_CHECK);
            Log.i(TAG, "urlTocheck = " + urlToCheck);
            out = openFileOutput(DATA_FILE,
                    Context.MODE_APPEND);
            final PrintWriter writer = new PrintWriter(out);
            writer.write(String.format("%d,", checkLatency(urlToCheck)));
            writer.flush();
        } catch (final Exception e) {
            // swallow
            Log.e(TAG, e.toString());
        } finally {
            closeSilently(out);
        }
    }

    /**
     * Checks network latency to the given URL.
     *
     * @param urlToCheck a URL to check
     * @return latency of a HTTP request to the URL.
     */
    private static long checkLatency(final String urlToCheck) {
        final long startTime = System.currentTimeMillis();
        final HttpClient httpclient = new DefaultHttpClient();
        try {
            httpclient.execute(new HttpGet(urlToCheck));
        } catch (final IOException e) {
            return -1;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Clears the latency history data.
     *
     * @param context a {@link Context} object.
     */
    private static void clearData(final Context context) {
        context.deleteFile(DATA_FILE);

        FileOutputStream out = null;
        try {
            out = context.openFileOutput(DATA_FILE, 0);
        } catch (final Exception e) {
            Log.e(TAG, e.toString());
        } finally {
            closeSilently(out);
        }

    }

    /**
     * Enables network monitoring. This will also clear the latency history data.
     *
     * @param context a {@link Context} object.
     * @param interval an interval of connectivity checks in milliseconds.
     * @param urlToCheck a URL to check.
     */
    public static void enable(final Context context, final long interval,
            final String urlToCheck) {
        if (interval <= 0 || urlToCheck == null) {
            throw new IllegalArgumentException();
        }

        // Clear the data file.
        clearData(context);

        final Intent intent = new Intent(context, WifiMonitorService.class);
        intent.putExtra(URL_TO_CHECK, urlToCheck);
        final PendingIntent operation = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        final AlarmManager alarm = (AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, interval, interval, operation);
    }

    /**
     * Disables network monitoring.
     *
     * @param context a {@link Context} object.
     */
    public static void disable(final Context context) {
        final Intent intent = new Intent(context, WifiMonitorService.class);
        final PendingIntent operation = PendingIntent.getService(context, 0, intent, 0);
        final AlarmManager alarm = (AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE);
        alarm.cancel(operation);
    }

    /**
     * Returns the latency history data.
     *
     * @param context a {@link Context} object.
     * @returns a comma-separated list of latency history for the given URL in milliseconds.
     */
    public static String getData(final Context context) {
        String output = "";
        FileInputStream in = null;
        try {
            in = context.openFileInput(DATA_FILE);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            output = reader.readLine();
        } catch (final IOException e) {
            // swallow
            Log.e(TAG, e.toString());
        } finally {
            closeSilently(in);
        }
        return output;
    }

    private static void closeSilently(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (final IOException e) {
                // swallow
            }
        }
    }

}
