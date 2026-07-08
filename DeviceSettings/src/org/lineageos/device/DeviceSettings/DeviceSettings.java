/*
 * Copyright (C) 2018-2022 crDroid Android Project
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

package org.lineageos.device.DeviceSettings;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import androidx.preference.SeekBarPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.Arrays;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.internal.util.FileUtils;

public class DeviceSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = DeviceSettings.class.getSimpleName();

    private static final String KEY_GAME_SWITCH = "game_mode";
    private static final String KEY_EDGE_TOUCH = "edge_touch";

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";

    private static final String KEY_USB2_SWITCH = "usb2_fast_charge";
    private static final String KEY_VIBSTRENGTH = "vib_strength";
    private static final String KEY_SAKURA_LIST = "sakura_version_list";

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Madara273/Sakura.MTX-OP9-Pro/releases";

    private static final String FILE_FAST_CHARGE = "/sys/module/oplus_chg/parameters/force_fast_charge";
    private static final String FILE_LEVEL = "/sys/devices/platform/soc/88c000.i2c/i2c-6/6-005a/leds/vibrator/level";
    private static final long testVibrationPattern[] = {0,5};
    private static final String DEFAULT = "3";

    private ListPreference mTopKeyPref;
    private ListPreference mMiddleKeyPref;
    private ListPreference mBottomKeyPref;

    private SwitchPreferenceCompat mGameModeSwitch;
    private SwitchPreferenceCompat mEdgeTouchSwitch;
    private SwitchPreferenceCompat mUSB2FastChargeModeSwitch;

    private SeekBarPreference mVibratorStrengthPreference;

    private Vibrator mVibrator;
    private ListPreference mSakuraList;
    private List<String> mDownloadUrls = new ArrayList<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mGameModeSwitch = (SwitchPreferenceCompat) findPreference(KEY_GAME_SWITCH);
        if (Utils.fileWritable(FILE_GAME)) {
            mGameModeSwitch.setEnabled(true);
            mGameModeSwitch.setChecked(sharedPrefs.getBoolean(KEY_GAME_SWITCH,
                Utils.getFileValueAsBoolean(FILE_GAME, false)));
            mGameModeSwitch.setOnPreferenceChangeListener(this);
        } else {
            mGameModeSwitch.setEnabled(false);
        }

        mEdgeTouchSwitch = (SwitchPreferenceCompat) findPreference(KEY_EDGE_TOUCH);
        if (Utils.fileWritable(FILE_EDGE)) {
            mEdgeTouchSwitch.setEnabled(true);
            mEdgeTouchSwitch.setChecked(sharedPrefs.getBoolean(KEY_EDGE_TOUCH,
                Utils.getFileValueAsBoolean(FILE_EDGE, false)));
            mEdgeTouchSwitch.setOnPreferenceChangeListener(this);
        } else {
            mEdgeTouchSwitch.setEnabled(false);
        }

        mUSB2FastChargeModeSwitch = (SwitchPreferenceCompat) findPreference(KEY_USB2_SWITCH);
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            mUSB2FastChargeModeSwitch.setEnabled(true);
            mUSB2FastChargeModeSwitch.setChecked(sharedPrefs.getBoolean(KEY_USB2_SWITCH,
                Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false)));
            mUSB2FastChargeModeSwitch.setOnPreferenceChangeListener(this);
        } else {
            mUSB2FastChargeModeSwitch.setEnabled(false);
        }

        mVibratorStrengthPreference = (SeekBarPreference) findPreference(KEY_VIBSTRENGTH);
        if (Utils.fileWritable(FILE_LEVEL)) {
            mVibratorStrengthPreference.setValue(sharedPrefs.getInt(KEY_VIBSTRENGTH,
                Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT))));
            mVibratorStrengthPreference.setOnPreferenceChangeListener(this);
        } else {
            mVibratorStrengthPreference.setEnabled(false);
        }

        mSakuraList = (ListPreference) findPreference(KEY_SAKURA_LIST);
        if (mSakuraList != null) {
            mSakuraList.setOnPreferenceChangeListener(this);
            new FetchSakuraReleasesTask().execute();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGameModeSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_GAME_SWITCH, enabled).commit();
            Utils.writeValue(FILE_GAME, enabled ? "1" : "0");
            if (enabled) {
                showCustomToast(getContext(), getString(R.string.game_mode_warning), 2000);
            }
            return true;
        } else if (preference == mEdgeTouchSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_EDGE_TOUCH, enabled).commit();
            Utils.writeValue(FILE_EDGE, enabled ? "1" : "0");
            if (enabled) {
                showCustomToast(getContext(), getString(R.string.edge_touch_warning), 2000);
            }
            return true;
        } else if (preference == mUSB2FastChargeModeSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_USB2_SWITCH, enabled).commit();
            Utils.writeValue(FILE_FAST_CHARGE, enabled ? "1" : "0");
            return true;
        } else if (preference == mSakuraList) {
            int index = mSakuraList.findIndexOfValue((String) newValue);
            if (index >= 0 && index < mDownloadUrls.size()) {
                String downloadUrl = mDownloadUrls.get(index);
                String versionName = mSakuraList.getEntries()[index].toString();
                downloadKernel(versionName, downloadUrl);
            }
            return true;
        } else if (preference == mVibratorStrengthPreference) {
            int value = Integer.parseInt(newValue.toString());
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putInt(KEY_VIBSTRENGTH, value).commit();
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
            mVibrator.vibrate(testVibrationPattern, -1);
            return true;
        }

        String key = preference.getKey();

        String node = Constants.sBooleanNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            Boolean value = (Boolean) newValue;
            FileUtils.writeLine(node, value ? "1" : "0");
            return true;
        }
        node = Constants.sStringNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (String) newValue);
            return true;
        }

        return false;
    }

    private void showCustomToast(Context context, String message, int durationMs) {
        if (context == null) return;
        final Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        toast.show();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, durationMs);
    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        super.setPreferencesFromResource(preferencesResId, rootKey);
        // Initialize node preferences
        for (String pref : Constants.sBooleanNodePreferenceMap.keySet()) {
            SwitchPreferenceCompat b = (SwitchPreferenceCompat) findPreference(pref);
            if (b == null) continue;
            String node = Constants.sBooleanNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                String curNodeValue = FileUtils.readOneLine(node);
                b.setChecked(curNodeValue.equals("1"));
                b.setOnPreferenceChangeListener(this);
            } else {
                removePref(b);
            }
        }
        for (String pref : Constants.sStringNodePreferenceMap.keySet()) {
            ListPreference l = (ListPreference) findPreference(pref);
            if (l == null) continue;
            String node = Constants.sStringNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                l.setValue(FileUtils.readOneLine(node));
                l.setOnPreferenceChangeListener(this);
            } else {
                removePref(l);
            }
        }
    }

    private void removePref(Preference pref) {
        PreferenceGroup parent = pref.getParent();
        if (parent == null) {
            return;
        }
        parent.removePreference(pref);
        if (parent.getPreferenceCount() == 0) {
            removePref(parent);
        }
    }

    public static void restoreFastChargeSetting(Context context) {
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean value = sharedPrefs.getBoolean(KEY_USB2_SWITCH,
                Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false));
            Utils.writeValue(FILE_FAST_CHARGE, value ? "1" : "0");
        }
    }

    public static void restoreVibStrengthSetting(Context context) {
        if (Utils.fileWritable(FILE_LEVEL)) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            int value = sharedPrefs.getInt(KEY_VIBSTRENGTH,
                Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT)));
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
        }
    }

    private class FetchSakuraReleasesTask extends AsyncTask<Void, Void, Boolean> {
        private List<String> versions = new ArrayList<>();
        private List<String> urls = new ArrayList<>();

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Sakura-OS-Agent");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.connect();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "GitHub API Response Code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray releases = new JSONArray(sb.toString());
                    for (int i = 0; i < releases.length(); i++) {
                        JSONObject release = releases.getJSONObject(i);
                        String tagName = release.getString("tag_name");

                        JSONArray assets = release.getJSONArray("assets");
                        if (assets.length() > 0) {
                            JSONObject asset = assets.getJSONObject(0);
                            String downloadUrl = asset.getString("browser_download_url");

                            versions.add(tagName);
                            urls.add(downloadUrl);
                        }
                    }
                    return true;
                } else {
                    Log.e(TAG, "Server returned HTTP error status: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing updates structure", e);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && !versions.isEmpty() && mSakuraList != null) {
                mDownloadUrls = urls;

                CharSequence[] entries = versions.toArray(new CharSequence[0]);
                CharSequence[] entryValues = new CharSequence[versions.size()];
                for (int i = 0; i < versions.size(); i++) {
                    entryValues[i] = String.valueOf(i);
                }

                mSakuraList.setEntries(entries);
                mSakuraList.setEntryValues(entryValues);
                mSakuraList.setSummary("Found releases: " + versions.size());
            } else {
                if (mSakuraList != null) {
                    mSakuraList.setSummary("Failed to fetch available update manifests");
                }
            }
        }
    }

    private void downloadKernel(String versionName, String url) {
        Toast.makeText(getContext(), "Downloading Sakura Kernel " + versionName + "...", Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Sakura Kernel " + versionName);
        request.setDescription("Downloading flashable AnyKernel3 deployment archive");

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Sakura-" + versionName + ".zip");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
        }
    }
}
