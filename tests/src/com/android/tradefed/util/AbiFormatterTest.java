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

import junit.framework.TestCase;

/**
 * Unit tests for the {@link StringUtil} utility class
 */
public class AbiFormatterTest extends TestCase {
    /**
    * Verify that {@link StringUtil#formatCmdForAbi} works as expected.
    */
   public void testFormatCmdForAbi() throws Exception {
       String a = "test foo|#ABI#| bar|#ABI32#| foobar|#ABI64#|";
       // if abi is null, remove all place holders.
       assertEquals("test foo bar foobar", AbiFormatter.formatCmdForAbi(a, null));
       // if abi is "", remove all place holders.
       assertEquals("test foo bar foobar", AbiFormatter.formatCmdForAbi(a, ""));
       // if abi is 32
       assertEquals("test foo32 bar foobar32", AbiFormatter.formatCmdForAbi(a, "32"));
       // if abi is 64
       assertEquals("test foo64 bar64 foobar", AbiFormatter.formatCmdForAbi(a, "64"));
       // test null input string
       assertNull(AbiFormatter.formatCmdForAbi(null, "32"));
    }
}
