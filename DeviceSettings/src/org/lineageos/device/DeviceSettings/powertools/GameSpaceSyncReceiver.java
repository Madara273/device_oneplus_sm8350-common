/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.GameManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

public class GameSpaceSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "GameSpaceSyncReceiver";

    private static final String ACTION_GAME_START = "io.chaldeaprjkt.gamespace.action.GAME_START";
    private static final String ACTION_GAME_STOP = "io.chaldeaprjkt.gamespace.action.GAME_STOP";
    private static final String EXTRA_PACKAGE_NAME = "package_name";
    private static final String KEY_PRE_GAME_MODE = "pre_game_mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        PowerProfileUtil util = new PowerProfileUtil(appContext);

        if (ACTION_GAME_START.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            int targetMode = PowerProfileUtil.MODE_PERFORMANCE;

            try {
                // Extract package name from game start broadcast
                if (pkg != null) {
                    // Read configured game list from system settings
                    String gamesList = Settings.System.getString(
                            appContext.getContentResolver(), "gamespace_game_list");

                    // Validate game list before processing
                    if (gamesList != null && !gamesList.isEmpty()) {
                        String[] games = gamesList.split(";");

                        // Iterate through configured games to find matching package
                        for (String gameEntry : games) {
                            if (gameEntry.startsWith(pkg + "=")) {
                                String[] parts = gameEntry.split("=");

                                // Parse mapped game performance mode
                                if (parts.length == 2) {
                                    int gameMode = Integer.parseInt(parts[1]);

                                    // Convert GameManager mode to internal power profile mode
                                    targetMode = mapGameMode(gameMode);

                                    Log.i(TAG,
                                            "Game Start: " + pkg +
                                            " gsMode=" + gameMode +
                                            " -> ptMode=" + targetMode);
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback to performance mode if parsing fails
                Log.w(TAG, "Failed to parse GameMode mapping, fallback to Performance mode", e);
            }

            int currentMode = util.getCurrentMode();

            // Backup current power profile mode before applying game profile
            if (!prefs.contains(KEY_PRE_GAME_MODE)) {
                int backupMode = (currentMode == targetMode)
                        ? PowerProfileUtil.MODE_BALANCE
                        : currentMode;

                prefs.edit().putInt(KEY_PRE_GAME_MODE, backupMode).apply();
                Log.d(TAG, "Pre-game profile mode backed up: " + backupMode);
            }

            // Apply selected game performance profile
            util.setMode(targetMode);

        } else if (ACTION_GAME_STOP.equals(action)) {
            Log.i(TAG, "Game Stop broadcast received. Restoring previous profile mode.");

            // Restore previously saved power profile mode
            int preGameMode = prefs.getInt(KEY_PRE_GAME_MODE, PowerProfileUtil.MODE_BALANCE);
            util.setMode(preGameMode);

            // Clear stored backup after restoration
            prefs.edit().remove(KEY_PRE_GAME_MODE).apply();

            Log.d(TAG, "Profile mode restored: " + preGameMode + ", backup cleared.");
        }
    }

    private int mapGameMode(int gameMode) {
        switch (gameMode) {
            case GameManager.GAME_MODE_PERFORMANCE:
                return PowerProfileUtil.MODE_PERFORMANCE;
            case GameManager.GAME_MODE_BATTERY:
                return PowerProfileUtil.MODE_BATTERY_SAVER;
            case GameManager.GAME_MODE_STANDARD:
            default:
                return PowerProfileUtil.MODE_BALANCE;
        }
    }
}
