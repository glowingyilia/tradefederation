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

package com.android.performance.tests;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.Assert;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * A harness that launches Geekbench and reports result.
 * Requires Geekbench binary and plar file in device temporary directory.
 */
public class GeekbenchTest implements IDeviceTest, IRemoteTest {

    private static final String RUN_KEY = "geekbench";
    private static final int MAX_ATTEMPTS = 3;
    private static final int TIMEOUT_MS = 10 * 60 * 1000;

    private static final String DEVICE_TEMPORARY_DIR_PATH = "/data/local/tmp/";
    private static final String GEEKBENCH_BINARY_FILENAME = "geekbench_armeabi-v7a_32";
    private static final String GEEKBENCH_BINARY_DEVICE_PATH =
            DEVICE_TEMPORARY_DIR_PATH + GEEKBENCH_BINARY_FILENAME;
    private static final String GEEKBENCH_PLAR_DEVICE_PATH =
            DEVICE_TEMPORARY_DIR_PATH + "geekbench.plar";
    private static final String GEEKBENCH_RESULT_DEVICE_PATH =
            DEVICE_TEMPORARY_DIR_PATH + "result.xml";

    private static final String OVERALL_SCORE_NAME = "Overall Score";
    private static final Map<String, String> METRICS_KEY_MAP = createMetricsKeyMap();

    private ITestDevice mDevice;

    private static Map<String, String> createMetricsKeyMap() {
        Map<String, String> result = new HashMap<String, String>();
        result.put(OVERALL_SCORE_NAME, "overall");
        result.put("Integer", "integer");
        result.put("Floating Point", "floating-point");
        result.put("Memory", "memory");
        result.put("Stream", "stream");
        return Collections.unmodifiableMap(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), RUN_KEY);
        ITestDevice device = getDevice();

        // delete old results
        device.executeShellCommand(String.format("rm %s", GEEKBENCH_RESULT_DEVICE_PATH));

        /*
        // push geekbench binary to device
        device.pushFile(mGeekbenchBinary, GEEKBENCH_BINARY_DEVICE_PATH);
        device.pushFile(mGeekbenchPlarFile, GEEKBENCH_PLAR_DEVICE_PATH);
        device.executeShellCommand(String.format("chmod 755 %s", GEEKBENCH_BINARY_DEVICE_PATH));
        */
        Assert.assertTrue(String.format("Geekbench binary not found on device: %s",
                GEEKBENCH_BINARY_DEVICE_PATH), device.doesFileExist(GEEKBENCH_BINARY_DEVICE_PATH));
        Assert.assertTrue(String.format("Geekbench binary not found on device: %s",
                GEEKBENCH_PLAR_DEVICE_PATH), device.doesFileExist(GEEKBENCH_PLAR_DEVICE_PATH));

        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();
        String errMsg = null;

        // start geekbench and wait for test to complete
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(String.format("%s --no-upload --export-xml %s",
                GEEKBENCH_BINARY_DEVICE_PATH, GEEKBENCH_RESULT_DEVICE_PATH), receiver,
                TIMEOUT_MS, TimeUnit.MILLISECONDS, MAX_ATTEMPTS);
        CLog.v(receiver.getOutput());

        // pull result from device
        File benchmarkReport = device.pullFile(GEEKBENCH_RESULT_DEVICE_PATH);
        if (benchmarkReport != null) {
            // parse result
            CLog.i("== Geekbench result ==");
            Map<String, String> benchmarkResult = parseResultXml(benchmarkReport);
            if (benchmarkResult == null) {
                errMsg = "Failed to parse Geekbench result XML.";
            } else {
                metrics = benchmarkResult;
            }

            // delete results from device and host
            benchmarkReport.delete();
            device.executeShellCommand("rm " + GEEKBENCH_RESULT_DEVICE_PATH);
        } else {
            errMsg = "Geekbench report not found.";
        }

        if (errMsg != null) {
            CLog.e(errMsg);
            listener.testFailed(TestFailure.FAILURE, testId, errMsg);
            listener.testEnded(testId, metrics);
            listener.testRunFailed(errMsg);
        } else {
            long durationMs = System.currentTimeMillis() - testStartTime;
            listener.testEnded(testId, metrics);
            listener.testRunEnded(durationMs, metrics);
        }
    }

    /**
     * Parse Geekbench result XML.
     *
     * @param resultXml Geekbench result XML {@link File}
     * @return a {@link HashMap} that contains metrics key and result; returns
     *         null if failed
     */
    private Map<String, String> parseResultXml(File resultXml) {
        Map<String, String> benchmarkResult = new HashMap<String, String>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        Document doc = null;
        try {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(resultXml);
        } catch (ParserConfigurationException e) {
            return null;
        } catch (IOException e) {
            return null;
        } catch (SAXException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
        doc.getDocumentElement().normalize();
        Node geekbenchNode = doc.getElementsByTagName("geekbench").item(0);
        if (geekbenchNode != null && geekbenchNode.getNodeType() == Node.ELEMENT_NODE) {
            Element geekbench = (Element) geekbenchNode;
            String overallScore = geekbench.getElementsByTagName("score").item(0)
                    .getChildNodes().item(0).getNodeValue();
            CLog.i(String.format("%s: %s", OVERALL_SCORE_NAME, overallScore));
            benchmarkResult.put(METRICS_KEY_MAP.get(OVERALL_SCORE_NAME), overallScore);
            NodeList nodes = doc.getElementsByTagName("section");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element section = (Element) node;
                    String sectionName = section.getAttribute("name");
                    String sectionScore = section.getElementsByTagName("score").item(0)
                            .getChildNodes().item(0).getNodeValue();
                    if (METRICS_KEY_MAP.containsKey(sectionName)) {
                        CLog.i(String.format("%s: %s", sectionName, sectionScore));
                        benchmarkResult.put(METRICS_KEY_MAP.get(sectionName), sectionScore);
                    }
                }
            }
        } else {
            CLog.i("Geekbench node not found in result file.");
            return null;
        }
        return benchmarkResult;
    }
}
