/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.device.DeviceSettings.R;

import java.util.ArrayList;
import java.util.List;

public class PowertoolsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_POWER_PROFILE_MODE = "power_profile_mode";
    private static final String KEY_MODE_STATUS = "mode_status_info";

    private static final String KEY_STORAGE_ENABLE = "storage_enable";
    private static final String KEY_IO_SCHEDULER = PowerProfileUtil.KEY_IO_SCHEDULER;

    private static final String KEY_GPU_ENABLE = "gpu_enable";
    private static final String KEY_GPU_MIN_FREQ = PowerProfileUtil.KEY_GPU_MIN_FREQ;
    private static final String KEY_GPU_MAX_FREQ = PowerProfileUtil.KEY_GPU_MAX_FREQ;
    private static final String KEY_GPU_GOVERNOR = PowerProfileUtil.KEY_GPU_GOVERNOR;
    private static final String GPU_DEFAULT_MIN = "315000000";
    private static final String GPU_DEFAULT_MAX = "840000000";
    private static final String GPU_DEFAULT_GOV = "msm-adreno-tz";

    private static final String KEY_CPU_ENABLE = "cpu_enable";
    private static final String KEY_CPU_LITTLE_MIN_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MIN_FREQ;
    private static final String KEY_CPU_LITTLE_MAX_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MAX_FREQ;
    private static final String KEY_CPU_LITTLE_GOVERNOR = PowerProfileUtil.KEY_CPU_LITTLE_GOVERNOR;
    private static final String KEY_CPU_BIG_MIN_FREQ = PowerProfileUtil.KEY_CPU_BIG_MIN_FREQ;
    private static final String KEY_CPU_BIG_MAX_FREQ = PowerProfileUtil.KEY_CPU_BIG_MAX_FREQ;
    private static final String KEY_CPU_BIG_GOVERNOR = PowerProfileUtil.KEY_CPU_BIG_GOVERNOR;
    private static final String KEY_CPU_PRIME_MIN_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MIN_FREQ;
    private static final String KEY_CPU_PRIME_MAX_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MAX_FREQ;
    private static final String KEY_CPU_PRIME_GOVERNOR = PowerProfileUtil.KEY_CPU_PRIME_GOVERNOR;

    // To define hardware default values for fallback
    private static final String CPU_LITTLE_DEFAULT_MIN = "300000";
    private static final String CPU_LITTLE_DEFAULT_MAX = "1804800";
    private static final String CPU_LITTLE_DEFAULT_GOV = "schedutil";
    private static final String CPU_BIG_DEFAULT_MIN = "710400";
    private static final String CPU_BIG_DEFAULT_MAX = "2419200";
    private static final String CPU_BIG_DEFAULT_GOV = "schedutil";
    private static final String CPU_PRIME_DEFAULT_MIN = "844800";
    private static final String CPU_PRIME_DEFAULT_MAX = "2841600";
    private static final String CPU_PRIME_DEFAULT_GOV = "schedutil";

    // To declare structural preference components
    private SwitchPreferenceCompat mStorageEnablePref, mGpuEnablePref, mCpuEnablePref;
    private Preference mModeStatusPref;
    private ListPreference mPowerProfilePref, mIoSchedulerPref;
    private ListPreference mGpuMinFreqPref, mGpuMaxFreqPref, mGpuGovernorPref;
    private ListPreference mCpuLittleMinFreqPref, mCpuLittleMaxFreqPref, mCpuLittleGovernorPref;
    private ListPreference mCpuBigMinFreqPref, mCpuBigMaxFreqPref, mCpuBigGovernorPref;
    private ListPreference mCpuPrimeMinFreqPref, mCpuPrimeMaxFreqPref, mCpuPrimeGovernorPref;

    private PowerProfileUtil mPowerProfileUtil;

    // To setup main UI thread tools and status flags
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<Preference> mAllControlPrefs = new ArrayList<>();
    private boolean mApplying = false;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // To inflate preference layout from resource xml file
        setPreferencesFromResource(R.xml.powertools_settings, rootKey);
        mPowerProfileUtil = new PowerProfileUtil(requireContext());

        mPowerProfilePref = bindPref(KEY_POWER_PROFILE_MODE);
        mModeStatusPref = findPreference(KEY_MODE_STATUS);

        mStorageEnablePref = bindPref(KEY_STORAGE_ENABLE);
        mIoSchedulerPref = bindPref(KEY_IO_SCHEDULER);

        mGpuEnablePref = bindPref(KEY_GPU_ENABLE);
        mGpuMinFreqPref = bindPref(KEY_GPU_MIN_FREQ);
        mGpuMaxFreqPref = bindPref(KEY_GPU_MAX_FREQ);
        mGpuGovernorPref = bindPref(KEY_GPU_GOVERNOR);

        mCpuEnablePref = bindPref(KEY_CPU_ENABLE);
        mCpuLittleMinFreqPref = bindPref(KEY_CPU_LITTLE_MIN_FREQ);
        mCpuLittleMaxFreqPref = bindPref(KEY_CPU_LITTLE_MAX_FREQ);
        mCpuLittleGovernorPref = bindPref(KEY_CPU_LITTLE_GOVERNOR);
        mCpuBigMinFreqPref = bindPref(KEY_CPU_BIG_MIN_FREQ);
        mCpuBigMaxFreqPref = bindPref(KEY_CPU_BIG_MAX_FREQ);
        mCpuBigGovernorPref = bindPref(KEY_CPU_BIG_GOVERNOR);
        mCpuPrimeMinFreqPref = bindPref(KEY_CPU_PRIME_MIN_FREQ);
        mCpuPrimeMaxFreqPref = bindPref(KEY_CPU_PRIME_MAX_FREQ);
        mCpuPrimeGovernorPref = bindPref(KEY_CPU_PRIME_GOVERNOR);

        initializeControlGroups();

        // Pre-populate mode card and summaries immediately so there is no blank flash
        // when the fragment is first drawn. onResume will do a full sync afterwards.
        syncActiveModeUI();
        int mode = getCurrentProfileMode();
        if (mPowerProfilePref != null) {
            CharSequence entry = mPowerProfilePref.getEntry();
            if (entry != null) mPowerProfilePref.setSummary(entry);
        }
        updateModeDisplays(mode);
    }

    @SuppressWarnings("unchecked")
    private <T extends Preference> T bindPref(String key) {
        // To attach modification listeners to specific elements securely
        T pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceChangeListener(this);
        }
        return pref;
    }

    private void initializeControlGroups() {
        // To collect all dependency tracking keys into list structure
        String[] controlKeys = {
            KEY_POWER_PROFILE_MODE, "power_profile_category", "power_profile_footer", KEY_MODE_STATUS,
            KEY_STORAGE_ENABLE, "storage_category", KEY_IO_SCHEDULER,
            KEY_GPU_ENABLE, "gpu_freq_category", KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
            KEY_CPU_ENABLE, "cpu_little_category", KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
            "cpu_big_category", KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
            "cpu_prime_category", KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR
        };

        for (String key : controlKeys) {
            Preference p = findPreference(key);
            if (p != null) mAllControlPrefs.add(p);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // To refresh user interface state maps upon fragment resume step
        syncActiveModeUI();
        refreshUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        // To terminate outstanding update handler runnables from queue
        mMainHandler.removeCallbacksAndMessages(null);
    }

    private void syncActiveModeUI() {
        // To synchronize selection state of performance menu with file content
        if (mPowerProfilePref == null || mPowerProfileUtil == null) return;
        String activeMode = String.valueOf(mPowerProfileUtil.getCurrentMode());
        if (!activeMode.equals(mPowerProfilePref.getValue())) {
            mPowerProfilePref.setValue(activeMode);
        }
    }

    private void refreshUI() {
        setControlsEnabled(mAllControlPrefs, true);
        if (mPowerProfilePref != null) mPowerProfilePref.setVisible(true);

        refreshModeState();
    }

    private void refreshModeState() {
        // To fetch updated user configurations and evaluate view trees
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        syncAllListPrefsToData(prefs);
        configurePresetModeUI();
    }

    private void configurePresetModeUI() {
        int mode = getCurrentProfileMode();

        if (mPowerProfilePref != null) {
            mPowerProfilePref.setEnabled(true);
            mPowerProfilePref.setSummary(mPowerProfilePref.getEntry());
        }

        updateModeDisplays(mode);

        boolean cpuEnabled = isChecked(mCpuEnablePref);
        boolean gpuEnabled = isChecked(mGpuEnablePref);
        boolean storageEnabled = isChecked(mStorageEnablePref);

        safeSetEnabled(mCpuEnablePref, true);
        safeSetEnabled(mGpuEnablePref, true);
        safeSetEnabled(mStorageEnablePref, true);

        updateGovernorDropdowns();

        // To toggle sub-category options states mapping user switches
        updateCpuSubPrefsEnabled(cpuEnabled);
        safeSetEnabled(mGpuMinFreqPref, gpuEnabled);
        safeSetEnabled(mGpuMaxFreqPref, gpuEnabled);
        safeSetEnabled(mGpuGovernorPref, gpuEnabled);
        safeSetEnabled(mIoSchedulerPref, storageEnabled);

        if (!cpuEnabled) resetHardwareCategoryToDefaults(KEY_CPU_ENABLE);
        if (!gpuEnabled) resetHardwareCategoryToDefaults(KEY_GPU_ENABLE);
        if (!storageEnabled) resetHardwareCategoryToDefaults(KEY_STORAGE_ENABLE);
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String newValStr = newValue.toString();

        // To process and delegate settings events to proper handlers
        switch (key) {

            case KEY_POWER_PROFILE_MODE:
                handleProfileModeChange(newValStr);
                return true;
            case KEY_STORAGE_ENABLE:
            case KEY_GPU_ENABLE:
            case KEY_CPU_ENABLE:
                handleHardwareToggleChange(key, (Boolean) newValue);
                return true;
            default:
                return handleHardwareValueChange(preference, key, newValStr);
        }
    }


    private void handleProfileModeChange(String newValue) {
        if (mApplying) return;
        lockForApply("Applying...");
        int mode = Integer.parseInt(newValue);

        // To reset manual category switch status flags prior to mode change
        if (mCpuEnablePref != null) mCpuEnablePref.setChecked(false);
        if (mGpuEnablePref != null) mGpuEnablePref.setChecked(false);
        if (mStorageEnablePref != null) mStorageEnablePref.setChecked(false);

        mPowerProfileUtil.setMode(mode);

        if (mPowerProfilePref != null) {
            mPowerProfilePref.setValue(newValue);
            mMainHandler.post(() -> mPowerProfilePref.setSummary(mPowerProfilePref.getEntry()));
        }

        refreshUI();
        mMainHandler.postDelayed(() -> {
            refreshModeState();
            String label = mPowerProfilePref != null ? mPowerProfilePref.getEntry().toString() : "Mode";
            unlockAfterApply(label + " applied");
        }, 1500);
    }

    private void handleHardwareToggleChange(String key, boolean enabled) {
        // To clear customized option groups if tuning gets switched off
        if (!enabled) {
            resetHardwareCategoryToDefaults(key);
            pushHardwareSettingsCategory(key);
            showToast("Restored default parameters");
        } else {
            showToast("Freestyle tweaking unlocked");
        }
        mMainHandler.postDelayed(this::refreshUI, 150);
    }

    private boolean handleHardwareValueChange(Preference preference, String key, String newValue) {
        // To validate limits constraints before submitting data changes
        String restriction = checkRestrictions(preference, newValue);
        if (restriction != null && !(preference instanceof ListPreference && preference.getKey() != null && preference.getKey().contains("_freq"))) {
            updateListPreferenceSafely(preference, newValue);
            getPreferenceManager().getSharedPreferences().edit().putString(key, newValue).apply();
            applyHardwareSetting(preference, key, newValue);
            return false;
        } else if (restriction != null) {
             showToast(restriction);
             return false;
        }

        applyHardwareSetting(preference, key, newValue);
        updateListPreferenceSafely(preference, newValue);

        getPreferenceManager().getSharedPreferences().edit().putString(key, newValue).apply();
        return false;
    }

    private void applyHardwareSetting(Preference preference, String key, String newValue) {
        // To commit custom user tunings directly to respective core helper calls
        if (preference == mIoSchedulerPref) {
            StorageUtils.setIoScheduler(newValue);
        } else if (preference == mGpuMinFreqPref) {
            GPUUtils.setGPUMinFrequency(newValue);
        } else if (preference == mGpuMaxFreqPref) {
            GPUUtils.setGPUMaxFrequency(newValue);
        } else if (preference == mGpuGovernorPref) {
            GPUUtils.setGPUGovernor(newValue);
        } else if (isCpuLittlePref(preference)) {
            CPUUtils.setCPULittleFreq(
                resolveVal(key, KEY_CPU_LITTLE_MIN_FREQ, newValue, mCpuLittleMinFreqPref, CPU_LITTLE_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_LITTLE_MAX_FREQ, newValue, mCpuLittleMaxFreqPref, CPU_LITTLE_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_LITTLE_GOVERNOR, newValue, mCpuLittleGovernorPref, CPU_LITTLE_DEFAULT_GOV)
            );
        } else if (isCpuBigPref(preference)) {
            CPUUtils.setCPUBigFreq(
                resolveVal(key, KEY_CPU_BIG_MIN_FREQ, newValue, mCpuBigMinFreqPref, CPU_BIG_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_BIG_MAX_FREQ, newValue, mCpuBigMaxFreqPref, CPU_BIG_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_BIG_GOVERNOR, newValue, mCpuBigGovernorPref, CPU_BIG_DEFAULT_GOV)
            );
        } else if (isCpuPrimePref(preference)) {
            CPUUtils.setCPUPrimeFreq(
                resolveVal(key, KEY_CPU_PRIME_MIN_FREQ, newValue, mCpuPrimeMinFreqPref, CPU_PRIME_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_PRIME_MAX_FREQ, newValue, mCpuPrimeMaxFreqPref, CPU_PRIME_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_PRIME_GOVERNOR, newValue, mCpuPrimeGovernorPref, CPU_PRIME_DEFAULT_GOV)
            );
        }
    }

    private void pushHardwareSettingsCategory(String categoryKey) {
        // To write complete parameter lists down to physical node layout files
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        switch (categoryKey) {
            case KEY_STORAGE_ENABLE:
                StorageUtils.setIoScheduler(prefs.getString(KEY_IO_SCHEDULER, SysfsUtils.getActiveIoScheduler()));
                break;
            case KEY_GPU_ENABLE:
                GPUUtils.setGPUMinFrequency(prefs.getString(KEY_GPU_MIN_FREQ, GPU_DEFAULT_MIN));
                GPUUtils.setGPUMaxFrequency(prefs.getString(KEY_GPU_MAX_FREQ, GPU_DEFAULT_MAX));
                GPUUtils.setGPUGovernor(prefs.getString(KEY_GPU_GOVERNOR, GPU_DEFAULT_GOV));
                break;
            case KEY_CPU_ENABLE:
                CPUUtils.setCPULittleFreq(
                    prefs.getString(KEY_CPU_LITTLE_MIN_FREQ, CPU_LITTLE_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_LITTLE_MAX_FREQ, CPU_LITTLE_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_LITTLE_GOVERNOR, CPU_LITTLE_DEFAULT_GOV)
                );
                CPUUtils.setCPUBigFreq(
                    prefs.getString(KEY_CPU_BIG_MIN_FREQ, CPU_BIG_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_BIG_MAX_FREQ, CPU_BIG_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_BIG_GOVERNOR, CPU_BIG_DEFAULT_GOV)
                );
                CPUUtils.setCPUPrimeFreq(
                    prefs.getString(KEY_CPU_PRIME_MIN_FREQ, CPU_PRIME_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_PRIME_MAX_FREQ, CPU_PRIME_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_PRIME_GOVERNOR, CPU_PRIME_DEFAULT_GOV)
                );
                break;
        }
    }

    private void resetHardwareCategoryToDefaults(String categoryKey) {
        // To reset user keys and force storage updates back to default thresholds
        SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
        List<String[]> dataToReset = new ArrayList<>();

        switch (categoryKey) {
            case KEY_STORAGE_ENABLE:
                dataToReset.add(new String[]{KEY_IO_SCHEDULER, SysfsUtils.getActiveIoScheduler()});
                break;
            case KEY_GPU_ENABLE:
                dataToReset.add(new String[]{KEY_GPU_MIN_FREQ, GPU_DEFAULT_MIN});
                dataToReset.add(new String[]{KEY_GPU_MAX_FREQ, GPU_DEFAULT_MAX});
                dataToReset.add(new String[]{KEY_GPU_GOVERNOR, GPU_DEFAULT_GOV});
                break;
            case KEY_CPU_ENABLE:
                dataToReset.add(new String[]{KEY_CPU_LITTLE_MIN_FREQ, CPU_LITTLE_DEFAULT_MIN});
                dataToReset.add(new String[]{KEY_CPU_LITTLE_MAX_FREQ, CPU_LITTLE_DEFAULT_MAX});
                dataToReset.add(new String[]{KEY_CPU_LITTLE_GOVERNOR, CPU_LITTLE_DEFAULT_GOV});
                dataToReset.add(new String[]{KEY_CPU_BIG_MIN_FREQ, CPU_BIG_DEFAULT_MIN});
                dataToReset.add(new String[]{KEY_CPU_BIG_MAX_FREQ, CPU_BIG_DEFAULT_MAX});
                dataToReset.add(new String[]{KEY_CPU_BIG_GOVERNOR, CPU_BIG_DEFAULT_GOV});
                dataToReset.add(new String[]{KEY_CPU_PRIME_MIN_FREQ, CPU_PRIME_DEFAULT_MIN});
                dataToReset.add(new String[]{KEY_CPU_PRIME_MAX_FREQ, CPU_PRIME_DEFAULT_MAX});
                dataToReset.add(new String[]{KEY_CPU_PRIME_GOVERNOR, CPU_PRIME_DEFAULT_GOV});
                break;
        }

        for (String[] pair : dataToReset) {
            String key = pair[0];
            String fallback = pair[1];

            editor.putString(key, fallback);
            Preference p = findPreference(key);
            if (p instanceof ListPreference) {
                ListPreference lp = (ListPreference) p;
                lp.setValue(fallback);
                CharSequence entry = lp.getEntry();
                lp.setSummary(entry != null ? entry : fallback);
            }
        }
        editor.apply();
    }

    private void lockForApply(String status) {
        // To temporarily disable click triggers while setup procedures process
        mApplying = true;
        setControlsEnabled(mAllControlPrefs, false);
        if (mModeStatusPref != null) mModeStatusPref.setSummary(status);
    }

    private void unlockAfterApply(String toast) {
        // To restore components interactions and notify execution done
        mApplying = false;
        refreshUI();
        showToast(toast);
    }

    private String resolveVal(String targetKey, String matchKey, String newValue, ListPreference pref, String fallback) {
        // To evaluate fallback options during active manual values assignment
        if (targetKey.equals(matchKey)) return newValue;
        return (pref != null && pref.getValue() != null) ? pref.getValue() : fallback;
    }

    private void updateModeDisplays(int mode) {
        if (mModeStatusPref != null) {
            mModeStatusPref.setSummary(getStatusSummaryForMode(mode));
        }
        updateModeCard(mode);
    }

    private String getStatusSummaryForMode(int mode) {
        // To build a clear descriptive status line mapping specific profile indices
        String activeIo = SysfsUtils.formatName(SysfsUtils.getActiveIoScheduler());
        switch (mode) {
            case PowerProfileUtil.MODE_PERFORMANCE:
                return "Max CPU/GPU \u2022 " + activeIo + " I/O \u2022 Game mode on";
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                return "Conservative scaling \u2022 " + activeIo + " I/O \u2022 Thermal throttle active";
            default:
                return "Balanced mode \u2022 " + activeIo + " I/O \u2022 Thermal throttle enabled";
        }
    }

    private void updateModeCard(int mode) {
        Preference card = findPreference("mode_card_header");
        if (card == null) return;

        String activeIo = SysfsUtils.formatName(SysfsUtils.getActiveIoScheduler());

        switch (mode) {
            case PowerProfileUtil.MODE_PERFORMANCE:
                card.setTitle("Performance");
                card.setSummary("Max CPU/GPU \u2022 " + activeIo + " I/O \u2022 Game mode on");
                card.setIcon(R.drawable.ic_thermal_performance);
                break;
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                card.setTitle("Powersave");
                card.setSummary("Conservative scaling \u2022 " + activeIo + " I/O \u2022 Thermal throttle active");
                card.setIcon(R.drawable.ic_thermal_battery_saver);
                break;
            default:
                card.setTitle("Normal");
                card.setSummary("Balanced mode \u2022 " + activeIo + " I/O \u2022 Thermal throttle enabled");
                card.setIcon(R.drawable.ic_thermal_balance);
                break;
        }
    }

    private String checkRestrictions(Preference preference, String value) {
        int mode = getCurrentProfileMode();
        boolean isCpuGov = isCpuGovernorPref(preference);
        boolean isGpuGov = (preference == mGpuGovernorPref);
        boolean isIoSched = (preference == mIoSchedulerPref);

        // To check hardware constraints or evaluate specific tuning parameters against safety rules
        if (preference instanceof ListPreference && preference.getKey() != null && preference.getKey().contains("_freq")) {
            try {
                long freqVal = Long.parseLong(value);
                if (mode == PowerProfileUtil.MODE_BATTERY_SAVER && preference.getKey().contains("max_freq")) {
                    return null;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (isCpuGov || isGpuGov || isIoSched) {
            return SysfsUtils.formatName(value);
        }

        return null;
    }

    private void syncAllListPrefsToData(SharedPreferences prefs) {
        // To synchronize every available dropdown component selection state with cached user configurations
        syncListPrefToData(mIoSchedulerPref, prefs, KEY_IO_SCHEDULER);
        syncListPrefToData(mGpuMinFreqPref, prefs, KEY_GPU_MIN_FREQ);
        syncListPrefToData(mGpuMaxFreqPref, prefs, KEY_GPU_MAX_FREQ);
        syncListPrefToData(mGpuGovernorPref, prefs, KEY_GPU_GOVERNOR);
        syncListPrefToData(mCpuLittleMinFreqPref, prefs, KEY_CPU_LITTLE_MIN_FREQ);
        syncListPrefToData(mCpuLittleMaxFreqPref, prefs, KEY_CPU_LITTLE_MAX_FREQ);
        syncListPrefToData(mCpuLittleGovernorPref, prefs, KEY_CPU_LITTLE_GOVERNOR);
        syncListPrefToData(mCpuBigMinFreqPref, prefs, KEY_CPU_BIG_MIN_FREQ);
        syncListPrefToData(mCpuBigMaxFreqPref, prefs, KEY_CPU_BIG_MAX_FREQ);
        syncListPrefToData(mCpuBigGovernorPref, prefs, KEY_CPU_BIG_GOVERNOR);
        syncListPrefToData(mCpuPrimeMinFreqPref, prefs, KEY_CPU_PRIME_MIN_FREQ);
        syncListPrefToData(mCpuPrimeMaxFreqPref, prefs, KEY_CPU_PRIME_MAX_FREQ);
        syncListPrefToData(mCpuPrimeGovernorPref, prefs, KEY_CPU_PRIME_GOVERNOR);
    }

    private void syncListPrefToData(ListPreference pref, SharedPreferences prefs, String key) {
        if (pref == null) return;
        String val = prefs.getString(key, "");
        if (!val.isEmpty()) {
            // To pull configuration variables from storage and assign active text formatting models
            pref.setValue(val);
            if (key.equals(KEY_IO_SCHEDULER)) {
                pref.setSummary(SysfsUtils.formatName(val));
            } else {
                pref.setSummary(SysfsUtils.formatFrequency(val));
            }
        }
    }

    private void updateListPreferenceSafely(Preference preference, String newValue) {
        if (preference instanceof ListPreference) {
            // To commit newly selected dropdown options safely and show a short toast confirmation
            ListPreference lp = (ListPreference) preference;
            lp.setValue(newValue);
            CharSequence entry = lp.getEntry();
            String displayText = (entry != null) ? entry.toString() : newValue;
            lp.setSummary(displayText);
            showToast(displayText + " applied");
        }
    }

    private void updateGovernorDropdowns() {
        // To query sysfs node arrays and populate available frequency tables or governor structures
        SysfsUtils.populateListFromSysfs(mCpuLittleGovernorPref, SysfsUtils.PATH_LITTLE_GOV_AVAIL, false);
        SysfsUtils.populateListFromSysfs(mCpuBigGovernorPref, SysfsUtils.PATH_BIG_GOV_AVAIL, false);
        SysfsUtils.populateListFromSysfs(mCpuPrimeGovernorPref, SysfsUtils.PATH_PRIME_GOV_AVAIL, false);
        SysfsUtils.populateListFromSysfs(mGpuGovernorPref, SysfsUtils.PATH_GPU_GOV_AVAIL, false);

        SysfsUtils.populateFrequenciesFromSysfs(mCpuLittleMinFreqPref, SysfsUtils.PATH_LITTLE_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mCpuLittleMaxFreqPref, SysfsUtils.PATH_LITTLE_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mCpuBigMinFreqPref, SysfsUtils.PATH_BIG_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mCpuBigMaxFreqPref, SysfsUtils.PATH_BIG_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mCpuPrimeMinFreqPref, SysfsUtils.PATH_PRIME_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mCpuPrimeMaxFreqPref, SysfsUtils.PATH_PRIME_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mGpuMinFreqPref, SysfsUtils.PATH_GPU_FREQ_AVAIL);
        SysfsUtils.populateFrequenciesFromSysfs(mGpuMaxFreqPref, SysfsUtils.PATH_GPU_FREQ_AVAIL);

        SysfsUtils.setupIoSchedulerPref(mIoSchedulerPref);
    }

    private void updateCpuSubPrefsEnabled(boolean enabled) {
        // To switch sub-category controls availability state mapping specific cluster switches
        safeSetEnabled(mCpuLittleMinFreqPref, enabled);
        safeSetEnabled(mCpuLittleMaxFreqPref, enabled);
        safeSetEnabled(mCpuLittleGovernorPref, enabled);
        safeSetEnabled(mCpuBigMinFreqPref, enabled);
        safeSetEnabled(mCpuBigMaxFreqPref, enabled);
        safeSetEnabled(mCpuBigGovernorPref, enabled);
        safeSetEnabled(mCpuPrimeMinFreqPref, enabled);
        safeSetEnabled(mCpuPrimeMaxFreqPref, enabled);
        safeSetEnabled(mCpuPrimeGovernorPref, enabled);
    }

    private void setControlsEnabled(List<Preference> prefs, boolean enabled) {
        // To iterate over tracking arrays and toggle preference component selection flags
        for (Preference p : prefs) safeSetEnabled(p, enabled);
    }

    private void safeSetEnabled(Preference pref, boolean enabled) {
        // To evaluate preference objects safety parameters before changing interaction states
        if (pref != null) pref.setEnabled(enabled);
    }

    private boolean isChecked(SwitchPreferenceCompat pref) {
        // To verify active selection flags on toggle switch elements
        return pref != null && pref.isChecked();
    }

    private int getCurrentProfileMode() {
        if (mPowerProfilePref == null) return PowerProfileUtil.MODE_BALANCE;
        try {
            // To parse active processing configuration indices or fall back to baseline balance levels
            return Integer.parseInt(mPowerProfilePref.getValue());
        } catch (NumberFormatException ignored) {
            return PowerProfileUtil.MODE_BALANCE;
        }
    }

    private boolean isCpuGovernorPref(Preference p) {
        // To identify if targeting preference elements handle core governor choices
        return p == mCpuLittleGovernorPref || p == mCpuBigGovernorPref || p == mCpuPrimeGovernorPref;
    }

    // To evaluate cluster tracking indices for target preference components
    private boolean isCpuLittlePref(Preference p) { return p == mCpuLittleMinFreqPref || p == mCpuLittleMaxFreqPref || p == mCpuLittleGovernorPref; }
    private boolean isCpuBigPref(Preference p) { return p == mCpuBigMinFreqPref || p == mCpuBigMaxFreqPref || p == mCpuBigGovernorPref; }
    private boolean isCpuPrimePref(Preference p) { return p == mCpuPrimeMinFreqPref || p == mCpuPrimeMaxFreqPref || p == mCpuPrimeGovernorPref; }

    private void showToast(String message) {
        // To display a safe short context toast message on the primary window framework
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException ignored) {}
    }
}
