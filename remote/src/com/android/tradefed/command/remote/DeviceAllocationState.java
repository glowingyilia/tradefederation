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
package com.android.tradefed.command.remote;

/**
 * Represents the allocation state of the device from the IDeviceManager perspective
 */
public enum DeviceAllocationState {
    /** device is available to be allocated to a test */
    Available,
    /** device is visible via adb but is in an error state that prevents it from running tests */
    Unavailable,
    /** device is currently allocated to a test */
    Allocated
}
