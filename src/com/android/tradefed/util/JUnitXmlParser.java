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

package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.xml.AbstractXmlParser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for the XML format JUnit test results.
 */
public class JUnitXmlParser extends AbstractXmlParser {
    Map<String, String> mTestMetrics;

    private class JUnitXmlHandler extends DefaultHandler {

        private static final String TESTSUITE_TAG = "testsuite";

        @Override
        /**
         * {@inheritDoc}
         */
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (TESTSUITE_TAG.equalsIgnoreCase(name)) {
                String errors = attributes.getValue("errors");
                String failures = attributes.getValue("failures");
                String tests = attributes.getValue("tests");
                int numErrors = 0;
                int numFailures = 0;
                int totalNumTests = 0;
                if (errors != null && failures != null && tests != null) {
                    numErrors = Integer.parseInt(attributes.getValue("errors"));
                    numFailures = Integer.parseInt(attributes.getValue("failures"));
                    totalNumTests = Integer.parseInt(attributes.getValue("tests"));
                } else {
                    CLog.e("Failed to parse test result, got a null string.");
                }
                int numPass = totalNumTests - numErrors - numFailures;
                mTestMetrics.put("Pass", Integer.toString(numPass));
                mTestMetrics.put("Fail", Integer.toString(numFailures));
                mTestMetrics.put("Error", Integer.toString(numErrors));
            }
        }
    }

    public JUnitXmlParser() {
        mTestMetrics = new HashMap<String, String>(3);
    }

    /**
     * Return the test metrics.
     * @return the test metrics
     */
    public Map<String, String> getTestMetrics() {
        return mTestMetrics;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    protected DefaultHandler createXmlHandler() {
        return new JUnitXmlHandler();
    }
}
