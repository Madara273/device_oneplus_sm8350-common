/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CPUUtils {
    private static final String TAG = "CPUUtils";
    private static final String SAFE_DUMMY_GOV = "schedutil";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private CPUUtils() {}

    // Set CPU LITTLE cluster frequency configuration
    // Validate governor and apply min/max frequency via system properties
    public static void setCPULittleFreq(final String minFreq, final String maxFreq, final String governor) {
        if (governor == null || minFreq == null || maxFreq == null) return;

        sExecutor.execute(() -> {
            try {
                // Reset governor to safe value if it matches current default
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.little.governor", SAFE_DUMMY_GOV))) {
                    SystemProperties.set("persist.sys.parts.cpu.little.governor", SAFE_DUMMY_GOV);
                }

                // Apply minimum and maximum frequency for LITTLE cores
                SystemProperties.set("persist.sys.parts.cpu.little.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.little.max_frequency", maxFreq);

                // Apply governor for LITTLE cluster
                SystemProperties.set("persist.sys.parts.cpu.little.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Little Freq", e);
            }
        });
    }

    // Set CPU BIG cluster frequency configuration
    // Handle performance cores frequency and governor setup
    public static void setCPUBigFreq(final String minFreq, final String maxFreq, final String governor) {
        if (governor == null || minFreq == null || maxFreq == null) return;

        sExecutor.execute(() -> {
            try {
                // Reset governor to safe value if it matches current default
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.big.governor", SAFE_DUMMY_GOV))) {
                    SystemProperties.set("persist.sys.parts.cpu.big.governor", SAFE_DUMMY_GOV);
                }

                // Apply minimum and maximum frequency for BIG cores
                SystemProperties.set("persist.sys.parts.cpu.big.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.big.max_frequency", maxFreq);

                // Apply governor for BIG cluster
                SystemProperties.set("persist.sys.parts.cpu.big.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Big Freq", e);
            }
        });
    }

    // Set CPU PRIME cluster frequency configuration
    // Handle highest performance cores frequency and governor setup
    public static void setCPUPrimeFreq(final String minFreq, final String maxFreq, final String governor) {
        if (governor == null || minFreq == null || maxFreq == null) return;

        sExecutor.execute(() -> {
            try {
                // Reset governor to safe value if it matches current default
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.prime.governor", SAFE_DUMMY_GOV))) {
                    SystemProperties.set("persist.sys.parts.cpu.prime.governor", SAFE_DUMMY_GOV);
                }

                // Apply minimum and maximum frequency for PRIME cores
                SystemProperties.set("persist.sys.parts.cpu.prime.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.prime.max_frequency", maxFreq);

                // Apply governor for PRIME cluster
                SystemProperties.set("persist.sys.parts.cpu.prime.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Prime Freq", e);
            }
        });
    }
}
