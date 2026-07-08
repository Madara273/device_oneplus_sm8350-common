/*
 * Copyright (C) 2018-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import android.util.ArrayMap;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lineageos.device.DeviceSettings.powertools.PowerProfileUtil;
import org.lineageos.internal.util.FileUtils;

public class DeviceSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String FILE_FAST_CHARGE = "/sys/module/oplus_chg/parameters/force_fast_charge";
    private static final String FILE_LEVEL = "/sys/devices/platform/soc/88c000.i2c/i2c-6/6-005a/leds/vibrator/level";

    private static final String KEY_GAME_SWITCH = "game_mode";
    private static final String KEY_EDGE_TOUCH = "edge_touch";
    private static final String KEY_USB2_SWITCH = "usb2_fast_charge";
    private static final String KEY_VIBSTRENGTH = "vib_strength";
    private static final String KEY_SAKURA_LIST = "sakura_version_list";

    private static final String TAG = "SakuraUpdate";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Madara273/Sakura.MTX-OP9-Pro/releases";

    private static final long[] TEST_VIB_PATTERN = { 0, 5 };
    private static final String DEFAULT_VIB_LEVEL = "3";

    private static final Map<String, String> sBooleanNodePreferenceMap = new ArrayMap<>();
    private static final Map<String, String> sStringNodePreferenceMap = new ArrayMap<>();

    private SwitchPreferenceCompat mGameModeSwitch;
    private SwitchPreferenceCompat mEdgeTouchSwitch;
    private SwitchPreferenceCompat mUSB2FastChargeModeSwitch;
    private CustomSeekBarPreference mVibratorStrengthPreference;
    private Vibrator mVibrator;

    private ListPreference mSakuraList;
    private List<String> mDownloadUrls = new ArrayList<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mVibrator = getContext().getSystemService(Vibrator.class);

        mGameModeSwitch = bindSwitchPref(KEY_GAME_SWITCH, FILE_GAME);
        mEdgeTouchSwitch = bindSwitchPref(KEY_EDGE_TOUCH, FILE_EDGE);
        mUSB2FastChargeModeSwitch = bindSwitchPref(KEY_USB2_SWITCH, FILE_FAST_CHARGE);

        mVibratorStrengthPreference = (CustomSeekBarPreference) findPreference(KEY_VIBSTRENGTH);
        if (Utils.fileWritable(FILE_LEVEL)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            mVibratorStrengthPreference.setValue(prefs.getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL))));
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

    private SwitchPreferenceCompat bindSwitchPref(String key, String sysfsPath) {
        SwitchPreferenceCompat pref = (SwitchPreferenceCompat) findPreference(key);
        if (pref != null) {
            if (Utils.fileWritable(sysfsPath)) {
                pref.setEnabled(true);
                pref.setChecked(PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(key, Utils.getFileValueAsBoolean(sysfsPath, false)));
                pref.setOnPreferenceChangeListener(this);
            } else {
                pref.setEnabled(false);
            }
        }
        return pref;
    }

    @Override
    public void onResume() {
        super.onResume();
        enforceTouchPanelPolicy();
        enforceVibPowersaveCap();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.device_title);
        }
    }

    private void enforceVibPowersaveCap() {
        if (mVibratorStrengthPreference == null || !mVibratorStrengthPreference.isEnabled()) return;

        boolean isPowersave = SystemProperties.getInt("persist.sys.perf_mode_saved", 1) == 0;
        int currentMax = isPowersave ? 2 : 3;
        mVibratorStrengthPreference.setMaxValue(currentMax);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int currentVal = sharedPrefs.getInt(KEY_VIBSTRENGTH, 3);

        if (isPowersave && currentVal > 2) {
            mVibratorStrengthPreference.setValue(2);
            sharedPrefs.edit().putInt(KEY_VIBSTRENGTH, 2).apply();
            Utils.writeValue(FILE_LEVEL, "2");
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        if (preference == mSakuraList) {
            int index = mSakuraList.findIndexOfValue((String) newValue);
            if (index >= 0 && index < mDownloadUrls.size()) {
                String downloadUrl = mDownloadUrls.get(index);
                String versionName = mSakuraList.getEntries()[index].toString();
                downloadKernel(versionName, downloadUrl);
            }
            return true;
        }

        if (preference == mGameModeSwitch) return applySwitch(editor, KEY_GAME_SWITCH, FILE_GAME, (Boolean) newValue);
        if (preference == mEdgeTouchSwitch) return applySwitch(editor, KEY_EDGE_TOUCH, FILE_EDGE, (Boolean) newValue);
        if (preference == mUSB2FastChargeModeSwitch) return applySwitch(editor, KEY_USB2_SWITCH, FILE_FAST_CHARGE, (Boolean) newValue);

        if (preference == mVibratorStrengthPreference) {
            int value = Integer.parseInt(newValue.toString());
            if (SystemProperties.getInt("persist.sys.perf_mode_saved", 1) == 0 && value > 2) {
                Toast.makeText(getContext(), "Vibration capped at level 2 in Powersave mode", Toast.LENGTH_SHORT).show();
                return false;
            }
            editor.putInt(KEY_VIBSTRENGTH, value).apply();
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
            if (mVibrator != null) mVibrator.vibrate(TEST_VIB_PATTERN, -1);
            return true;
        }

        String node = sBooleanNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (Boolean) newValue ? "1" : "0");
            return true;
        }

        node = sStringNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (String) newValue);
            return true;
        }

        return false;
    }

    private boolean applySwitch(SharedPreferences.Editor editor, String prefKey, String sysfsPath, boolean enabled) {
        editor.putBoolean(prefKey, enabled).apply();
        Utils.writeValue(sysfsPath, enabled ? "1" : "0");
        return true;
    }

    private void enforceTouchPanelPolicy() {
        if (mGameModeSwitch == null || mEdgeTouchSwitch == null) return;

        int profile = SystemProperties.getInt("sys.perf_mode_active", PowerProfileUtil.MODE_BALANCE);

        if (profile == PowerProfileUtil.MODE_PERFORMANCE) {
            mGameModeSwitch.setChecked(true);
            mEdgeTouchSwitch.setChecked(true);
            mGameModeSwitch.setEnabled(false);
            mEdgeTouchSwitch.setEnabled(false);
        } else if (profile == PowerProfileUtil.MODE_BATTERY_SAVER) {
            mGameModeSwitch.setChecked(false);
            mEdgeTouchSwitch.setChecked(false);
            mGameModeSwitch.setEnabled(false);
            mEdgeTouchSwitch.setEnabled(false);
        } else {
            mGameModeSwitch.setEnabled(true);
            mEdgeTouchSwitch.setEnabled(true);
        }
    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        super.setPreferencesFromResource(preferencesResId, rootKey);

        for (String pref : sBooleanNodePreferenceMap.keySet()) {
            SwitchPreferenceCompat b = (SwitchPreferenceCompat) findPreference(pref);
            if (b == null) continue;
            String node = sBooleanNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                b.setChecked("1".equals(FileUtils.readOneLine(node)));
                b.setOnPreferenceChangeListener(this);
            } else {
                removePref(b);
            }
        }

        for (String pref : sStringNodePreferenceMap.keySet()) {
            ListPreference l = (ListPreference) findPreference(pref);
            if (l == null) continue;
            String node = sStringNodePreferenceMap.get(pref);
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
        if (parent != null) {
            parent.removePreference(pref);
            if (parent.getPreferenceCount() == 0) removePref(parent);
        }
    }

    public static void restoreFastChargeSetting(Context context) {
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            boolean value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(KEY_USB2_SWITCH, Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false));
            Utils.writeValue(FILE_FAST_CHARGE, value ? "1" : "0");
        }
    }

    public static void restoreVibStrengthSetting(Context context) {
        if (Utils.fileWritable(FILE_LEVEL)) {
            int value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL)));
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
