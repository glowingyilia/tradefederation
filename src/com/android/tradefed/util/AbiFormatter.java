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

package com.android.tradefed.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for string manipulations.
 */
public class AbiFormatter {

    static final String FORCE_ABI_STRING = "force-abi";

    /**
     * Special marker to be used as a placeholder in strings, that can be then
     * replaced with the help of {@link formatCmdForAbi}.
     */
    static final String ABI_REGEX = "\\|#ABI(\\d*)#\\|";

    /**
     * Helper method that formats a given string to include abi specific
     * values to it by replacing a given marker.
     *
     * @param str {@link String} to format which includes special markers |
     *            {@value #ABI_REGEX} to be replaced
     * @param abi {@link String} of the abi we desire to run on.
     * @return formatted string.
     */
    public static String formatCmdForAbi(String str, String abi) {
        // If the abi is not set or null, do nothing. This is to maintain backward compatibility.
        if (str == null) {
            return null;
        }
        if (abi == null) {
            return str.replaceAll(ABI_REGEX, "");
        }
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile(ABI_REGEX).matcher(str);
        while (m.find()) {
            if (m.group(1).equals(abi)) {
                m.appendReplacement(sb, "");
            } else {
                m.appendReplacement(sb, abi);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}