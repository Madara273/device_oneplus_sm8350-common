/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;
import androidx.preference.ListPreference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SysfsUtils {
    private static final String TAG = "SysfsUtils";

    // To declare sysfs paths for CPU scaling governors
    public static final String PATH_LITTLE_GOV_AVAIL = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors";
    public static final String PATH_LITTLE_GOV_ACTIVE = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";

    public static final String PATH_BIG_GOV_AVAIL = "/sys/devices/system/cpu/cpufreq/policy4/scaling_available_governors";
    public static final String PATH_BIG_GOV_ACTIVE = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";

    public static final String PATH_PRIME_GOV_AVAIL = "/sys/devices/system/cpu/cpufreq/policy7/scaling_available_governors";
    public static final String PATH_PRIME_GOV_ACTIVE = "/sys/devices/system/cpu/cpufreq/policy7/scaling_governor";

    // To declare sysfs path for available GPU governors
    public static final String PATH_GPU_GOV_AVAIL = "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors";

    // To declare sysfs paths for block device I/O schedulers
    public static final String PATH_IO_SCHED_SDA_AVAIL = "/sys/block/sda/queue/scheduler";
    public static final String PATH_IO_SCHED_SDB_AVAIL = "/sys/block/sdb/queue/scheduler";
    public static final String PATH_IO_SCHED_SDC_AVAIL = "/sys/block/sdc/queue/scheduler";
    public static final String PATH_IO_SCHED_SDD_AVAIL = "/sys/block/sdd/queue/scheduler";
    public static final String PATH_IO_SCHED_SDE_AVAIL = "/sys/block/sde/queue/scheduler";
    public static final String PATH_IO_SCHED_SDF_AVAIL = "/sys/block/sdf/queue/scheduler";

    // To declare sysfs paths for available scaling frequencies
    public static final String PATH_LITTLE_FREQ_AVAIL = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies";
    public static final String PATH_BIG_FREQ_AVAIL = "/sys/devices/system/cpu/cpufreq/policy4/scaling_available_frequencies";
    public static final String PATH_PRIME_FREQ_AVAIL = "/sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies";
    public static final String PATH_GPU_FREQ_AVAIL = "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies";

    private SysfsUtils() {}

    // To check if a specific node path is readable
    public static boolean isReadable(String path) {
        return path != null && new File(path).canRead();
    }

    // To read the first line from a specified sysfs file path
    public static String readLine(String path) {
        if (!isReadable(path)) return null;

        try (FileInputStream fis = new FileInputStream(path);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {

            String line = br.readLine();
            return line != null ? line.trim() : null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to read line from path: " + path);
            return null;
        }
    }

    // To parse an integer value from a sysfs path or return default
    public static int readInt(String path, int def) {
        String line = readLine(path);
        if (line == null) return def;
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // To write a system property key-value pair securely
    public static void writeProperty(String key, String value) {
        try {
            SystemProperties.set(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set property " + key + " to " + value, e);
        }
    }

    // To retrieve the active CPU scaling governor name
    public static String getActiveCpuGovernor(String path) {
        String gov = readLine(path);
        return gov != null ? gov : "schedutil";
    }

    // To parse out the active I/O scheduler from the selection bracket
    public static String getActiveIoScheduler() {
        String raw = readLine(PATH_IO_SCHED_SDA_AVAIL);
        if (raw == null || raw.isEmpty()) return "none";

        int start = raw.indexOf('[');
        int end = raw.indexOf(']');
        if (start != -1 && end != -1 && start < end) {
            return raw.substring(start + 1, end).trim();
        }

        String[] parts = raw.split("\\s+");
        return parts.length > 0 ? parts[0] : "none";
    }

    // To populate ListPreference entries and values from a sysfs node layout
    public static void populateListFromSysfs(ListPreference pref, String path, boolean isIo) {
        if (pref == null) return;

        String raw = readLine(path);
        if (raw == null || raw.isEmpty()) {
            pref.setEnabled(false);
            return;
        }

        String[] items;
        if (isIo) {
            // To remove brackets from raw scheduler layout string
            String clean = raw.replace("[", "").replace("]", "");
            items = clean.split("\\s+");
        } else {
            items = raw.split("\\s+");
        }

        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                entries.add(formatName(trimmed));
                values.add(trimmed);
            }
        }

        // To assign generated lists to targeting ListPreference element
        if (!entries.isEmpty()) {
            pref.setEntries(entries.toArray(new CharSequence[0]));
            pref.setEntryValues(values.toArray(new CharSequence[0]));
            pref.setEnabled(true);
        } else {
            pref.setEnabled(false);
        }
    }

    // To parse and populate available frequency listings into the ListPreference
    public static void populateFrequenciesFromSysfs(ListPreference pref, String path) {
        if (pref == null) return;

        String raw = readLine(path);
        if (raw == null || raw.isEmpty()) {
            pref.setEnabled(false);
            return;
        }

        String[] rawFreqs = raw.split("\\s+");
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (String freq : rawFreqs) {
            String trimmed = freq.trim();
            if (!trimmed.isEmpty()) {
                entries.add(formatFrequency(trimmed));
                values.add(trimmed);
            }
        }

        if (!entries.isEmpty()) {
            pref.setEntries(entries.toArray(new CharSequence[0]));
            pref.setEntryValues(values.toArray(new CharSequence[0]));
            pref.setEnabled(true);
        } else {
            pref.setEnabled(false);
        }
    }

    // To configure I/O scheduler preference options and select active value
    public static void setupIoSchedulerPref(ListPreference pref) {
        populateListFromSysfs(pref, PATH_IO_SCHED_SDA_AVAIL, true);
        String active = getActiveIoScheduler();
        if (pref.isEnabled() && !active.isEmpty()) {
            pref.setValue(active);
            pref.setSummary(formatName(active));
        }
    }

    // To format raw Hz string into user-friendly MHz values
    public static String formatFrequency(String hzStr) {
        if (hzStr == null || hzStr.isEmpty()) return "N/A";
        try {
            long hz = Long.parseLong(hzStr);

            // To convert Hz based on value range thresholds
            if (hz > 10000000) {
                return (hz / 1000000) + " MHz";
            }
            else {
                return (hz / 1000) + " MHz";
            }
        } catch (NumberFormatException e) {
            return hzStr;
        }
    }

    // To format raw underscores/hyphens into Capitalized Title Text names
    public static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "N/A";
        String clean = raw.replace("_", " ").replace("-", " ").trim();
        if (clean.isEmpty()) return raw;

        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : clean.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
