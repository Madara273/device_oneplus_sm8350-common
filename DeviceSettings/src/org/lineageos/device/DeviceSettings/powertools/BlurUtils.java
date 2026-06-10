/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

public final class BlurUtils {
    private static final String TAG = "PowertoolsBlurUtils";

    /**
     * Stores the value of disable_window_blurs that was active BEFORE powersave
     * forced blur off. Only present in prefs when powersave is/was active.
     * Cleared as soon as we restore from it.
     */
    private static final String PREF_POWERSAVE_BLUR_BACKUP = "powersave_blur_backup";
    private static final String SETTING_BLUR = "disable_window_blurs";

    private BlurUtils() {}

    /**
     * Called by the profile system.
     *
     * disable=true  → powersave is active: back up current blur state, force blur OFF.
     * disable=false → leaving powersave: restore the backed-up value IF one exists.
     *                 If no backup exists (user was never in powersave), do NOTHING —
     *                 Settings.Global already persists across reboots on its own.
     */
    public static void setBlurDisabled(Context context, boolean disable) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);

        try {
            if (disable) {
                // Save whatever the user/system currently has before stomping it
                int current = Settings.Global.getInt(
                        appContext.getContentResolver(), SETTING_BLUR, 0);
                prefs.edit().putInt(PREF_POWERSAVE_BLUR_BACKUP, current).apply();
                Settings.Global.putInt(appContext.getContentResolver(), SETTING_BLUR, 1);
            } else {
                // Only restore if powersave actually backed something up
                if (prefs.contains(PREF_POWERSAVE_BLUR_BACKUP)) {
                    int backup = prefs.getInt(PREF_POWERSAVE_BLUR_BACKUP, 0);
                    Settings.Global.putInt(appContext.getContentResolver(), SETTING_BLUR, backup);
                    // Clear the backup so we don't accidentally re-apply it later
                    prefs.edit().remove(PREF_POWERSAVE_BLUR_BACKUP).apply();
                }
                // No backup → blur was never touched by us → leave it alone
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set blur disabled status: " + disable, e);
        }
    }

    /**
     * Discards any stale powersave blur backup without restoring it.
     * Called on boot so that Settings.Global.disable_window_blurs
     * is left exactly as the system persisted it across reboot.
     */
    public static void clearPowersaveBackup(Context context) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        prefs.edit().remove(PREF_POWERSAVE_BLUR_BACKUP).apply();
    }
}
