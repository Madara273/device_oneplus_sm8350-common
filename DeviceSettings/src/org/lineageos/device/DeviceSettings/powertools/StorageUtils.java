/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageUtils {
    private static final String TAG = "StorageUtils";
    private static final String PROP_CLKSCALE = "persist.sys.parts.storage.clkscale";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private StorageUtils() {}

    // Retrieve available I/O schedulers for specified block device
    public static String[] getAvailableIoSchedulers(final String device) {
        String path = "/sys/block/" + device + "/queue/scheduler";
        File file = new File(path);

        // Validate scheduler node existence
        if (!file.exists()) return null;

        try {
            // Read scheduler list from sysfs node
            String content = new String(Files.readAllBytes(file.toPath())).trim();

            // Ignore empty or invalid scheduler entries
            if (content.equals("none") || content.isEmpty()) {
                return null;
            }

            // Remove bracket markers from active scheduler
            String cleaned = content.replaceAll("[\\[\\]]", "");

            // Split available schedulers into array
            String[] parts = cleaned.split(" ");

            // Validate parsed result
            if (parts.length <= 1 && parts[0].equals("none")) {
                return null;
            }

            return parts;

        } catch (IOException e) {
            Log.e(TAG, "Failed to read IO Schedulers for " + device, e);
        }

        return null;
    }

    // Apply I/O scheduler for specific block device
    public static void setIoSchedulerForDevice(final String device, final String scheduler) {
        if (device == null || device.isEmpty() || scheduler == null || scheduler.isEmpty()) return;

        sExecutor.execute(() -> {
            String path = "/sys/block/" + device + "/queue/scheduler";
            File file = new File(path);

            // Validate write access to scheduler node
            if (!file.exists() || !file.canWrite()) {
                Log.e(TAG, "Cannot write to device node: " + path);
                return;
            }

            try (FileWriter writer = new FileWriter(file)) {
                // Write selected scheduler to sysfs node
                writer.write(scheduler);
                writer.flush();

                Log.d(TAG, "Successfully wrote " + scheduler + " to " + path);

            } catch (Exception e) {
                Log.e(TAG, "Failed to write IO Scheduler to " + path, e);
            }
        });
    }

    // Apply I/O scheduler to common storage devices
    public static void setIoScheduler(String scheduler) {
        setIoSchedulerForDevice("sda", scheduler);
        setIoSchedulerForDevice("sdb", scheduler);
    }

    // Enable or disable UFS clock scaling feature
    public static void setUfsClkScale(final boolean enable) {
        sExecutor.execute(() -> {
            try {
                // Apply UFS clock scaling system property
                SystemProperties.set(PROP_CLKSCALE, enable ? "1" : "0");

                Log.d(TAG, "UFS Clock Scaling status updated to: " + enable);

            } catch (Exception e) {
                Log.e(TAG, "Failed to set UFS Clock Scaling to: " + enable, e);
            }
        });
    }
}
