/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * Adapted for OnePlus 9 (SD888 / SM8350)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PowerProfileTileService extends TileService {

    private PowerProfileUtil mManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private static final int[] TILE_STATES = {
        Tile.STATE_INACTIVE, // 0: Saver
        Tile.STATE_INACTIVE, // 1: Balance
        Tile.STATE_ACTIVE,   // 2: Performance
        Tile.STATE_INACTIVE, // 3: Unused
        Tile.STATE_INACTIVE  // 4: Unknown
    };

    private static final int[] TILE_ICONS = {
        R.drawable.ic_thermal_battery_saver, // 0: Saver
        R.drawable.ic_thermal_balance,       // 1: Balance
        R.drawable.ic_thermal_performance,   // 2: Performance
        R.drawable.ic_thermal_balance,       // 3: Unused
        R.drawable.ic_thermal_balance        // 4: Unknown
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize power profile manager with application context
        mManager = new PowerProfileUtil(getApplicationContext());
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Refresh tile state when QS becomes visible
        updateTile();
    }

    @Override
    public void onClick() {
        if (mManager == null) {
            return;
        }

        // Immediately grey out the tile to block rapid re-clicks
        final Tile tile = getQsTile();
        if (tile != null) {
            // Temporary UI state while applying changes
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle("Applying...");
            tile.updateTile();
        }

        mExecutor.execute(() -> {
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            // Toggle power profile mode
            mManager.toggleMode();

            // Retrieve newly applied mode
            int nextMode = mManager.getCurrentMode();

            // Persist selected mode for future restoration
            prefs.edit()
                    .putString("power_profile_mode", String.valueOf(nextMode))
                    .apply();

            // Refresh tile after short delay to reflect applied state
            mMainHandler.postDelayed(this::updateTile, 1200);
        });
    }

    private void updateTile() {
        mMainHandler.post(() -> {
            Tile tile = getQsTile();
            if (tile == null || mManager == null) return;

            // Get current managed power mode
            int mode = mManager.getManagedMode();

            if (mode < 0 || mode >= TILE_STATES.length) {
                mode = PowerProfileUtil.MODE_UNKNOWN;
            }

            // Update tile state based on current mode
            tile.setState(TILE_STATES[mode]);
            // Update icon based on mode
            tile.setIcon(Icon.createWithResource(this, TILE_ICONS[mode]));
            // Set tile label from resources
            tile.setLabel(getString(R.string.powerprofile_tile_label));
            // Show current mode label
            tile.setSubtitle(mManager.getModeLabel());

            tile.updateTile();
        });
    }

    @Override
    public void onDestroy() {
        // Shutdown background executor to avoid leaks
        mExecutor.shutdown();

        // Clear pending UI updates
        mMainHandler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}
