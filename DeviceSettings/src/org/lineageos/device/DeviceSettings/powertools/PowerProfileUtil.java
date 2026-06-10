/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;
import org.lineageos.device.DeviceSettings.Utils;

public class PowerProfileUtil {

    private static final String TAG = "PowerProfileUtil";
    private static final String SYS_PROP = "sys.perf_mode_active";

    // To declare kernel panel node paths and tracking persistence keys
    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String KEY_LAST_PROFILE = "powertools_last_profile";

    // To define preference keys for core I/O and GPU parameters
    public static final String KEY_IO_SCHEDULER = "io_scheduler";
    public static final String KEY_GPU_MIN_FREQ = "gpu_min_frequency";
    public static final String KEY_GPU_MAX_FREQ = "gpu_max_frequency";
    public static final String KEY_GPU_GOVERNOR = "gpu_governor";

    // To define preference keys for CPU cluster frequencies and governors
    public static final String KEY_CPU_LITTLE_MIN_FREQ = "cpu_little_min_frequency";
    public static final String KEY_CPU_LITTLE_MAX_FREQ = "cpu_little_max_frequency";
    public static final String KEY_CPU_LITTLE_GOVERNOR = "cpu_little_governor";

    public static final String KEY_CPU_BIG_MIN_FREQ = "cpu_big_min_frequency";
    public static final String KEY_CPU_BIG_MAX_FREQ = "cpu_big_max_frequency";
    public static final String KEY_CPU_BIG_GOVERNOR = "cpu_big_governor";

    public static final String KEY_CPU_PRIME_MIN_FREQ = "cpu_prime_min_frequency";
    public static final String KEY_CPU_PRIME_MAX_FREQ = "cpu_prime_max_frequency";
    public static final String KEY_CPU_PRIME_GOVERNOR = "cpu_prime_governor";

    // To expose performance profile indexing flags
    public static final int MODE_BATTERY_SAVER = 0;
    public static final int MODE_BALANCE = 1;
    public static final int MODE_PERFORMANCE = 2;
    public static final int MODE_UNKNOWN = 4;

    private final Context mContext;
    private int mCurrentMode = MODE_BALANCE;
    private final String[] mModes;

    public PowerProfileUtil(Context context) {
        mContext = context;

        // Initialize localized mode labels from resources
        mModes = new String[]{
                mContext.getString(R.string.powerprofile_mode_battery_saver),
                mContext.getString(R.string.powerprofile_mode_balance),
                mContext.getString(R.string.powerprofile_mode_performance),
                "", // Blank placeholder for index 3
                mContext.getString(R.string.powerprofile_mode_unknown)
        };
    }


    // Retrieve current performance mode from system property
    public int getCurrentMode() {
        return SystemProperties.getInt(SYS_PROP, MODE_BALANCE);
    }

    // Apply selected power profile mode
    public boolean setMode(int mode) {
        mCurrentMode = mode;

        // Persist last selected profile
        saveLastProfile(mode);

        // Apply system performance configuration
        boolean success = setPerformanceModeActive(mode);

        // Apply touch panel configuration based on mode
        applyUserTouchPanel();

        // Enable or disable blur effects depending on power state
        BlurUtils.setBlurDisabled(mContext, mode == MODE_BATTERY_SAVER);

        return success;
    }

    /**
     * Same as {@link #setMode(int)} but does NOT touch blur.
     * Used on boot so that Settings.Global.disable_window_blurs
     * is left exactly as the system persisted it across reboot.
     */
    public boolean setModeOnBoot(int mode) {
        mCurrentMode = mode;

        // Persist boot-applied profile
        saveLastProfile(mode);

        // Apply system performance configuration
        boolean success = setPerformanceModeActive(mode);

        // Apply touch panel configuration
        applyUserTouchPanel();

        // Skip blur control on boot to allow system restoration
        return success;
    }

    // Save last selected profile into shared preferences
    private void saveLastProfile(int mode) {
        SharedPreferences prefs = mContext.getSharedPreferences(
                mContext.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );

        prefs.edit()
                .putString(KEY_LAST_PROFILE, String.valueOf(mode))
                .apply();
    }

    // Configure touch panel behavior based on current mode
    private void applyUserTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        boolean isGameEnabled;
        boolean isEdgeEnabled;

        // Force enable/disable features in strict performance modes
        if (mCurrentMode == MODE_PERFORMANCE) {
            isGameEnabled = true;
            isEdgeEnabled = true;
        } else if (mCurrentMode == MODE_BATTERY_SAVER) {
            isGameEnabled = false;
            isEdgeEnabled = false;
        } else {
            // Use user preferences in balanced mode
            isGameEnabled = prefs.getBoolean("game_mode", false);
            isEdgeEnabled = prefs.getBoolean("edge_touch", false);
        }

        // Persist enforced values when in strict modes
        if (mCurrentMode == MODE_PERFORMANCE || mCurrentMode == MODE_BATTERY_SAVER) {
            prefs.edit()
                    .putBoolean("game_mode", isGameEnabled)
                    .putBoolean("edge_touch", isEdgeEnabled)
                    .apply();
        }

        // Apply kernel-level touch panel settings if writable
        if (Utils.fileWritable(FILE_GAME)) {
            Utils.writeValue(FILE_GAME, isGameEnabled ? "1" : "0");
        }

        if (Utils.fileWritable(FILE_EDGE)) {
            Utils.writeValue(FILE_EDGE, isEdgeEnabled ? "1" : "0");
        }
    }

    // Return mode depending on auto-thermal override state
    public int getManagedMode() {
        return getCurrentMode();
    }

    // Return human-readable label for current mode
    public String getModeLabel() {
        int mode = getManagedMode();
        if (mode == MODE_BATTERY_SAVER) return "PowerSave";
        if (mode == MODE_BALANCE) return "Normal";

        return (mode >= 0 && mode < mModes.length)
                ? mModes[mode]
                : mModes[MODE_UNKNOWN];
    }

    // Toggle between balance, performance, and battery saver modes
    public void toggleMode() {
        int currentMode = getManagedMode();

        int newMode =
                (currentMode == MODE_BALANCE) ? MODE_PERFORMANCE :
                (currentMode == MODE_PERFORMANCE) ? MODE_BATTERY_SAVER :
                MODE_BALANCE;

        setMode(newMode);
    }

    // Apply performance mode via system properties with safe "bounce" trigger
    private boolean setPerformanceModeActive(int mode) {
        try {
            // Reset property to force init trigger behavior if required
            SystemProperties.set(SYS_PROP, "-1");

            // Allow system to process intermediate state
            Thread.sleep(50);

            // Apply selected performance mode
            SystemProperties.set(SYS_PROP, String.valueOf(mode));

            // Persist mode for system restore
            SystemProperties.set("persist.sys.perf_mode_saved", String.valueOf(mode));

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Best practice: restore interrupted state
            Log.e(TAG, "Interrupted while bouncing performance mode property", e);
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Failed to set performance mode system properties", e);
            return false;
        }
    }
}
