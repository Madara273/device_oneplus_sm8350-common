/*
 * Copyright (C) 2026 Madara273
 * Copyright (C) 2025-2026 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;

import android.content.res.ColorStateList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SystemMonitorWidget extends AppWidgetProvider {
    private static final String TAG = "SystemMonitorWidget";
    private static final String ACTION_TICK = "org.lineageos.device.DeviceSettings.WIDGET_TICK";
    private static final long UPDATE_INTERVAL_MS = 3000;

    // To define sysfs paths for reading temperature and frequency
    private static final String BATTERY_TEMP    = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP        = "/sys/class/thermal/thermal_zone39/temp";
    private static final String GPU_TEMP        = "/sys/class/thermal/thermal_zone54/temp";
    private static final String GPU_CUR_FREQ    = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    private static final String GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

    private static final String[] MODE_LABELS = {"PowerSave", "Normal", "Performance"};

    private static long sLastCpuTotal = 0;
    private static long sLastCpuIdle  = 0;

    // To initialize background executor for updates
    private static final ExecutorService sBackgroundExecutor = Executors.newSingleThreadExecutor();
    private static ScreenStateReceiver sScreenReceiver;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        if (ctx == null) return;
        // To register screen receiver and process asynchronous update
        ensureReceiverRegistered(ctx);
        processUpdateAsync(ctx, mgr, ids);
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        if (ctx == null) return;
        // To schedule periodic updates when widget is added
        ensureReceiverRegistered(ctx);
        scheduleNextUpdate(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        super.onDisabled(ctx);
        if (ctx == null) return;
        // To cancel updates and unregister receiver when widget is disabled
        cancelUpdates(ctx);
        if (sScreenReceiver != null) {
            try {
                ctx.getApplicationContext().unregisterReceiver(sScreenReceiver);
            } catch (Exception ignored) {}
            sScreenReceiver = null;
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ctx == null || intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        // To process boot completed or system tick intents
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON": // HTC / some OEMs
                ensureReceiverRegistered(ctx);
                scheduleNextUpdate(ctx);
                break;

            case ACTION_TICK:
                AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
                int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
                if (ids != null && ids.length > 0) {
                    processUpdateAsync(ctx, mgr, ids);
                }
                break;
        }
    }

    private static void ensureReceiverRegistered(Context ctx) {
        if (sScreenReceiver != null) return;

        // To create intent filter for tracking screen state
        sScreenReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        // To register receiver according to SDK version
        Context appContext = ctx.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(sScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(sScreenReceiver, filter);
        }
    }

    private static class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null) return;
            String action = intent.getAction();
            if (action == null) return;

            // To manage updates depending on screen state
            switch (action) {
                case Intent.ACTION_SCREEN_OFF:
                    cancelUpdates(context);
                    showSleepingState(context);
                    break;
                case Intent.ACTION_SCREEN_ON:
                case Intent.ACTION_USER_PRESENT:
                    scheduleNextUpdate(context);
                    break;
            }
        }
    }

    private static void scheduleNextUpdate(Context ctx) {
        // To schedule next tick via AlarmManager
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long triggerAt = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS;
            am.setExact(AlarmManager.ELAPSED_REALTIME, triggerAt, getTickPI(ctx));
        }
    }

    private static void cancelUpdates(Context ctx) {
        // To cancel scheduled alarms for the widget
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(getTickPI(ctx));
    }

    private static PendingIntent getTickPI(Context ctx) {
        // To create PendingIntent for tick broadcast action
        Intent i = new Intent(ctx, SystemMonitorWidget.class).setAction(ACTION_TICK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, 0, i, flags);
    }

    private static void showSleepingState(Context ctx) {
        // To switch widget to visual sleep mode
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
        if (ids == null || ids.length == 0) return;

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);
        v.setTextViewText(R.id.widget_mode, "Asleep (\uD83D\uDCA4)");
        v.setTextColor(R.id.widget_mode, 0xFF888888);

        // To reset all progress bars to zero
        int[] bars = { R.id.widget_cpu_bar, R.id.widget_gpu_bar, R.id.widget_mem_bar,
                R.id.widget_swap_bar, R.id.widget_bat_bar, R.id.widget_storage_bar };
        for (int bar : bars) v.setProgressBar(bar, 100, 0, false);

        v.setTextViewText(R.id.widget_cpu_text,     "--%");
        v.setTextViewText(R.id.widget_gpu_text,     "--%");
        v.setTextViewText(R.id.widget_mem_text,     "--%");
        v.setTextViewText(R.id.widget_swap_text,    "--%");
        v.setTextViewText(R.id.widget_bat_text,     "--%");
        v.setTextViewText(R.id.widget_storage_text, "--%");
        v.setTextViewText(R.id.widget_cpu_temp,     "\u2699\uFE0F C: --\u00b0C");
        v.setTextViewText(R.id.widget_gpu_temp,     "\uD83C\uDFAE G: --\u00b0C");
        v.setTextViewText(R.id.widget_bat_temp,     "\uD83D\uDD0B B: --\u00b0C");

        applyClickIntent(ctx, v);
        mgr.updateAppWidget(ids, v);
    }

    private static void applyClickIntent(Context ctx, RemoteViews v) {
        // To configure opening main activity on widget click
        Intent openApp = new Intent(ctx, PowertoolsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
    }

    private void processUpdateAsync(Context ctx, AppWidgetManager mgr, int[] ids) {
        // To trigger data collection and UI updates in background thread
        final PendingResult pendingResult = goAsync();
        final Context appContext = ctx.getApplicationContext();
        sBackgroundExecutor.execute(() -> {
            try {
                buildAndApplyViews(appContext, mgr, ids);
                scheduleNextUpdate(appContext);
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }

    private void buildAndApplyViews(Context ctx, AppWidgetManager mgr, int[] ids) {
        // To collect all system metrics
        int cpuPct  = getCpuUsage();
        int[] gpu   = getGpuInfo();
        int[] mem   = getMemoryInfo(ctx);
        int[] swap  = getSwapInfo();
        int bat     = getBatteryLevel(ctx);
        String[] stor = getStorageInfo();
        String modeLabel = getModeLabel(ctx);
        int modeColor    = getModeColor(modeLabel);

        int storPct = 0;
        try {
            storPct = Integer.parseInt(stor[2]);
        } catch (NumberFormatException ignored) {}

        // To read temperatures from sysfs sensors
        float batTemp = readTempC(BATTERY_TEMP, 10f);
        float cpuTemp = readTempC(CPU_TEMP,     1000f);
        float gpuTemp = readTempC(GPU_TEMP,     1000f);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);

        // To update mode label and text color
        v.setTextViewText(R.id.widget_mode, modeLabel);
        v.setTextColor(R.id.widget_mode, modeColor);

        // To set progress bar and tint tint list for CPU
        v.setProgressBar(R.id.widget_cpu_bar, 100, cpuPct, false);
        v.setTextViewText(R.id.widget_cpu_text, cpuPct + "%");
        v.setProgressTintList(R.id.widget_cpu_bar, ColorStateList.valueOf(barTint(cpuPct, 0xFF6C63FF)));
        v.setTextViewText(R.id.widget_cpu_temp, String.format("\u2699\uFE0F C: %.0f\u00b0C", cpuTemp));

        // To set progress bar and tint list for GPU
        v.setProgressBar(R.id.widget_gpu_bar, 100, gpu[0], false);
        v.setTextViewText(R.id.widget_gpu_text, gpu[0] + "%");
        v.setProgressTintList(R.id.widget_gpu_bar, ColorStateList.valueOf(barTint(gpu[0], 0xFF43E97B)));
        v.setTextViewText(R.id.widget_gpu_temp, String.format("\uD83C\uDFAE G: %.0f\u00b0C", gpuTemp));

        // To set progress bar for RAM
        v.setProgressBar(R.id.widget_mem_bar, 100, mem[0], false);
        v.setTextViewText(R.id.widget_mem_text, mem[0] + "%");
        v.setProgressTintList(R.id.widget_mem_bar, ColorStateList.valueOf(barTint(mem[0], 0xFFFFA726)));

        // To set progress bar for Swap
        v.setProgressBar(R.id.widget_swap_bar, 100, swap[0], false);
        v.setTextViewText(R.id.widget_swap_text, swap[0] + "%");
        v.setProgressTintList(R.id.widget_swap_bar, ColorStateList.valueOf(barTint(swap[0], 0xFFCE93D8)));

        // To set progress bar and stats for battery
        v.setProgressBar(R.id.widget_bat_bar, 100, bat, false);
        v.setTextViewText(R.id.widget_bat_text, bat + "%");
        v.setProgressTintList(R.id.widget_bat_bar, ColorStateList.valueOf(barTint(bat, 0xFF29B6F6)));
        v.setTextViewText(R.id.widget_bat_temp, String.format("\uD83D\uDD0B B: %.0f\u00b0C", batTemp));

        // To set progress bar for internal storage
        v.setProgressBar(R.id.widget_storage_bar, 100, storPct, false);
        v.setTextViewText(R.id.widget_storage_text, storPct + "%");
        v.setProgressTintList(R.id.widget_storage_bar, ColorStateList.valueOf(barTint(storPct, 0xFF26C6DA)));

        applyClickIntent(ctx, v);
        mgr.updateAppWidget(ids, v);
    }

    private int barTint(int pct, int normalColor) {
        // To change bar color to red on high load (from 85%)
        return pct >= 85 ? 0xFFEF5350 : normalColor;
    }

    private int getCpuUsage() {
        // To read /proc/stat for calculating total CPU utilization
        try (RandomAccessFile r = new RandomAccessFile("/proc/stat", "r")) {
            String line = r.readLine();
            if (line == null) return 0;
            String[] p = line.split("\\s+");
            if (p.length < 5) return 0;

            long idle  = Long.parseLong(p[4]) + Long.parseLong(p[5]);
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);

            int pct = 0;
            if (sLastCpuTotal > 0) {
                long dTotal = total - sLastCpuTotal;
                long dIdle  = idle  - sLastCpuIdle;
                pct = dTotal > 0 ? (int) ((dTotal - dIdle) * 100 / dTotal) : 0;
            }
            sLastCpuTotal = total;
            sLastCpuIdle  = idle;
            return Math.max(0, Math.min(100, pct));
        } catch (Exception e) { return 0; }
    }

    private int[] getGpuInfo() {
        // To parse GPU load percentage and maximum frequency
        try {
            String busyStr = readSysfs("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
            int pct = 0;
            if (busyStr != null && !busyStr.isEmpty())
                pct = Integer.parseInt(busyStr.trim().split("\\s+")[0]);

            long max = Long.parseLong(readSysfs(GPU_MAX_FREQ_PATH));
            int maxMhz = (int) (max / 1000000);
            return new int[]{Math.min(100, Math.max(0, pct)), maxMhz};
        } catch (Exception e) { return new int[]{0, 0}; }
    }

    private int[] getMemoryInfo(Context ctx) {
        // To fetch RAM usage info using ActivityManager
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return new int[]{0};
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total = mi.totalMem  / (1024 * 1024);
            if (total == 0) return new int[]{0};
            long used  = total - (mi.availMem / (1024 * 1024));
            return new int[]{(int) (used * 100 / total)};
        } catch (Exception e) { return new int[]{0}; }
    }

    private int[] getSwapInfo() {
        // To read /proc/meminfo to parse Swap metrics
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            long swapTotal = 0, swapFree = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) swapTotal = parseMemLine(line);
                else if (line.startsWith("SwapFree:"))  swapFree  = parseMemLine(line);
            }
            if (swapTotal == 0) return new int[]{0};
            return new int[]{(int) ((swapTotal - swapFree) * 100 / swapTotal)};
        } catch (Exception e) { return new int[]{0}; }
    }

    private long parseMemLine(String line) {
        // To split meminfo line to extract numerical value
        String[] parts = line.split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    private int getBatteryLevel(Context ctx) {
        // To get current battery capacity property percentage
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : 0;
        } catch (Exception e) { return 0; }
    }

    private String[] getStorageInfo() {
        // To evaluate used and total space on external storage directory
        try {
            StatFs stat  = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long total   = stat.getTotalBytes();
            long used    = total - stat.getAvailableBytes();
            int pct      = total > 0 ? (int) (used * 100 / total) : 0;
            String usedGB  = String.format("%.1f", used  / 1073741824.0);
            String totalGB = String.format("%.0f", total / 1073741824.0);
            return new String[]{usedGB, totalGB, String.valueOf(pct)};
        } catch (Exception e) { return new String[]{"?", "?", "0"}; }
    }

    private String getModeLabel(Context ctx) {
        // To obtain active performance profile from shared preferences or system properties
        try {
            int mode = SystemProperties.getInt("sys.perf_mode_active", 1);
            if (mode >= 0 && mode < MODE_LABELS.length) return MODE_LABELS[mode];
        } catch (Exception ignored) {}
        return "Normal";
    }

    private int getModeColor(String label) {
        // To assign matching text color for each individual mode
        switch (label) {
            case "PowerSave":   return 0xFF29B6F6;
            case "Performance": return 0xFF43E97B;
            default:            return 0xFF6C63FF;
        }
    }

    private float readTempC(String path, float divisor) {
        // To read raw temperature from path and convert to Celsius degrees
        try { return Integer.parseInt(readSysfs(path)) / divisor; }
        catch (Exception e) { return 0f; }
    }

    private String readSysfs(String path) {
        // To read first line from target sysfs file node
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            return line != null ? line.trim() : "0";
        } catch (Exception e) { return "0"; }
    }
}
