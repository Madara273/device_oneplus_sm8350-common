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

public final class GPUUtils {
    private static final String TAG = "GPUUtils";
    private static final String SAFE_DUMMY_GOV = "msm-adreno-tz";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private GPUUtils() {}

    // Set minimum GPU frequency
    // Apply minimum clock value and derive MHz representation for system properties
    public static void setGPUMinFrequency(final String frequencyHz) {
        if (frequencyHz == null || frequencyHz.isEmpty()) return;

        sExecutor.execute(() -> {
            try {
                // Apply GPU minimum frequency in Hz
                SystemProperties.set("persist.sys.parts.gpu.min_frequency", frequencyHz);

                try {
                    long freqLong = Long.parseLong(frequencyHz);

                    // Convert Hz to MHz and store as GPU min clock value
                    SystemProperties.set(
                            "persist.sys.parts.gpu.min_clock",
                            String.valueOf(freqLong / 1000000)
                    );
                } catch (NumberFormatException nfe) {
                    // Handle invalid numeric format for frequency input
                    Log.w(TAG, "Invalid format for GPU min frequency: " + frequencyHz);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Min Freq", e);
            }
        });
    }

    // Set maximum GPU frequency
    // Apply maximum clock value and derive MHz representation for system properties
    public static void setGPUMaxFrequency(final String frequencyHz) {
        if (frequencyHz == null || frequencyHz.isEmpty()) return;

        sExecutor.execute(() -> {
            try {
                // Apply GPU maximum frequency in Hz
                SystemProperties.set("persist.sys.parts.gpu.max_frequency", frequencyHz);

                try {
                    long freqLong = Long.parseLong(frequencyHz);

                    // Convert Hz to MHz and store as GPU max clock value
                    SystemProperties.set(
                            "persist.sys.parts.gpu.max_clock",
                            String.valueOf(freqLong / 1000000)
                    );
                } catch (NumberFormatException nfe) {
                    // Handle invalid numeric format for frequency input
                    Log.w(TAG, "Invalid format for GPU max frequency: " + frequencyHz);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Max Freq", e);
            }
        });
    }

    // Set GPU governor
    // Apply governor selection and fallback to safe default when needed
    public static void setGPUGovernor(final String governor) {
        if (governor == null || governor.isEmpty()) return;

        sExecutor.execute(() -> {
            try {
                // Reset to safe governor if current value matches default placeholder
                if (governor.equals(SystemProperties.get("persist.sys.parts.gpu.governor", SAFE_DUMMY_GOV))) {
                    SystemProperties.set("persist.sys.parts.gpu.governor", SAFE_DUMMY_GOV);
                }

                // Apply GPU governor selection
                SystemProperties.set("persist.sys.parts.gpu.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Governor", e);
            }
        });
    }
}
