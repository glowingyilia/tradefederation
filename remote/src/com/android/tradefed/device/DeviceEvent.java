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
package com.android.tradefed.device;

/**
 * Represents a test device event that can change allocation state
 */
enum DeviceEvent {
        CONNECTED_ONLINE,
        STATE_CHANGE_ONLINE,
        DISCONNECTED,
        FORCE_AVAILABLE,
        AVAILABLE_CHECK_PASSED,
        AVAILABLE_CHECK_FAILED,
        AVAILABLE_CHECK_IGNORED,
        ALLOCATE_REQUEST,
        FORCE_ALLOCATE_REQUEST,
        FREE_AVAILABLE,
        FREE_UNRESPONSIVE,
        FREE_UNAVAILABLE,
        FREE_UNKNOWN;

        /**
         * Helper method to convert from a {@link FreeDeviceState} to a {@link DeviceEvent}
         */
        static DeviceEvent convertFromFree(FreeDeviceState deviceState) {
            switch (deviceState) {
                case UNRESPONSIVE:
                    return DeviceEvent.FREE_UNRESPONSIVE;
                case AVAILABLE:
                    return DeviceEvent.FREE_AVAILABLE;
                case UNAVAILABLE:
                    return DeviceEvent.FREE_UNAVAILABLE;
                case IGNORE:
                    return DeviceEvent.FREE_UNKNOWN;
            }
            throw new IllegalStateException("unknown FreeDeviceState");
        }
}
