/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.build;

import java.util.Map;

/**
 * Holds information about the build under test.
 */
public interface IBuildInfo {

    /**
     * Default value when build ID is unknown.
     */
    public final static String UNKNOWN_BUILD_ID = "-1";

    /**
     * @return the unique identifier of build under test. Should never be null.
     *         Defaults to {@link #UNKOWN_BUILD_ID}
     */
    public String getBuildId();

    /**
     * Return a unique name for the tests being run.
     */
    public String getTestTag();

    /**
     * Return complete name for the build being tested.
     * <p/>
     * A common implementation is to construct the build target name from a combination of
     * the build flavor and branch name. (ie <branch name>-<build flavor>)
     */
    public String getBuildTargetName();

    /**
     * Optional method to return the type of build being tested.
     * <p/>
     * A common implementation for Android platform builds is to return
     * <build product>-<build os>-<build variant>.
     * ie generic-linux-userdebug
     *
     * @return the build flavor or <code>null</code> if unset/not applicable
     */
    public String getBuildFlavor();

    /**
     * Set the build flavor.
     *
     * @param buildFlavor
     */
    public void setBuildFlavor(String buildFlavor);

    /**
     * Optional method to return the source control branch that the build being tested was
     * produced from.
     *
     * @return the build branch or <code>null</code> if unset/not applicable
     */
    public String getBuildBranch();

    /**
     * Set the build branch
     *
     * @param branch
     */
    public void setBuildBranch(String branch);

    /**
     * Get a set of name-value pairs of additional attributes describing the build.
     *
     * @return a {@link Map} of build attributes. Will not be <code>null</code>, but may be empty.
     */
    public Map<String, String> getBuildAttributes();

    /**
     * Add a build attribute
     *
     * @param attributeName the unique attribute name
     * @param attributeValue the attribute value
     */
    public void addBuildAttribute(String attributeName, String attributeValue);

    /**
     * Clean up any temporary build files
     */
    public void cleanUp();

    /**
     * @return
     */
    public IBuildInfo clone();

}
