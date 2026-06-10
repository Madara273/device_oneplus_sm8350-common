/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PowertoolBootReceiver extends BroadcastReceiver {
    private static final String TAG = "PowertoolBootReceiver";

    private static final String PREF_POWER_PROFILE  = "power_profile_mode";
    private static final String PREF_CPU_ENABLE     = "cpu_enable";
    private static final String PREF_GPU_ENABLE     = "gpu_enable";
    private static final String PREF_STORAGE_ENABLE = "storage_enable";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);

        final PendingResult pendingResult = goAsync();

        // Execute boot-time configuration in background thread
        sExecutor.execute(() -> {
            try {
                // Clear any previous power-saving backup state
                BlurUtils.clearPowersaveBackup(appContext);

                // Reset persisted feature toggles to safe defaults
                prefs.edit()
                        .putString(PREF_POWER_PROFILE, String.valueOf(PowerProfileUtil.MODE_BALANCE))
                        .putBoolean(PREF_CPU_ENABLE, false)
                        .putBoolean(PREF_GPU_ENABLE, false)
                        .putBoolean(PREF_STORAGE_ENABLE, false)
                        .apply();

                // Initialize power profile manager
                PowerProfileUtil profileUtil = new PowerProfileUtil(appContext);

                // Apply balanced mode during boot sequence
                profileUtil.setModeOnBoot(PowerProfileUtil.MODE_BALANCE);

                Log.i(TAG, "Boot completed: Balanced mode applied successfully.");

            } catch (Exception e) {
                Log.e(TAG, "Error occurred during background boot processing", e);

            } finally {
                // Signal completion of broadcast processing
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }
}
