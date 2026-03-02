package com.example.internetspeedmeeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Foreground service that measures network speed every second (or per settings).
 * Shows live total speed in a floating overlay and ↓↑ detail in the notification.
 * Fires a separate "slow speed" alert notification when speed drops below threshold.
 */
public class SpeedService extends Service {

    private static final String CHANNEL_ID        = "speed_monitor_channel";
    private static final String ALERT_CHANNEL_ID  = "slow_speed_alert_channel";
    private static final int    NOTIFICATION_ID   = 1;
    private static final int    ALERT_NOTIF_ID    = 2;
    private static final String PREFS_NAME        = "SpeedPrefs";

    private NotificationManager notificationManager;
    private WindowManager       windowManager;
    private TextView            dockOverlay;

    private long lastRxBytes = 0;
    private long lastTxBytes = 0;
    private int  slowCount   = 0; // consecutive slow-speed ticks
    private boolean alertFired = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannels();
        initLastBytes();
        if (Settings.canDrawOverlays(this)) showDockOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("↓ 0 KB/s  ↑ 0 KB/s");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        startTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        removeDockOverlay();
    }

    // ── Notification Channels ─────────────────────────────────────────────────
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Main speed monitor channel (silent, persistent)
            NotificationChannel main = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            main.setShowBadge(false);
            main.setSound(null, null);
            main.setVibrationPattern(new long[]{0});
            main.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            // Slow speed alert channel (audible)
            NotificationChannel alert = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    getString(R.string.slow_speed_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            alert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(main);
                notificationManager.createNotificationChannel(alert);
            }
        }
    }

    // ── Floating Overlay ──────────────────────────────────────────────────────
    private void showDockOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int textSizeSp  = prefs.getInt(SettingsActivity.KEY_OVERLAY_SIZE, 12);
        int position    = prefs.getInt(SettingsActivity.KEY_OVERLAY_POSITION, 0);

        dockOverlay = new TextView(this);
        dockOverlay.setText("0 KB/s");
        dockOverlay.setTextSize(textSizeSp);
        dockOverlay.setPadding(dp(8), dp(4), dp(8), dp(4));
        updateDockOverlayColor();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int gravity = Gravity.TOP | (position == 0 ? Gravity.END : Gravity.START);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        params.x = dp(8);
        params.y = getStatusBarHeight() + dp(2);

        windowManager.addView(dockOverlay, params);
    }

    private void removeDockOverlay() {
        if (windowManager != null && dockOverlay != null) {
            try { windowManager.removeView(dockOverlay); } catch (Exception ignored) {}
            dockOverlay = null;
        }
    }

    private void updateDockOverlayColor() {
        if (dockOverlay != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int color = prefs.getInt(SettingsActivity.KEY_METER_COLOR,
                    ContextCompat.getColor(this, R.color.accent_color));
            dockOverlay.setTextColor(color);
        }
    }

    // ── Tracking Loop ─────────────────────────────────────────────────────────
    private void initLastBytes() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        lastRxBytes = (rx == TrafficStats.UNSUPPORTED) ? 0 : rx;
        lastTxBytes = (tx == TrafficStats.UNSUPPORTED) ? 0 : tx;
    }

    private void startTracking() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long intervalMs = prefs.getInt(SettingsActivity.KEY_UPDATE_INTERVAL, 1000);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long rx = TrafficStats.getTotalRxBytes();
                long tx = TrafficStats.getTotalTxBytes();

                if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED) {
                    long deltaRx = rx - lastRxBytes;
                    long deltaTx = tx - lastTxBytes;
                    if (deltaRx < 0) deltaRx = 0;
                    if (deltaTx < 0) deltaTx = 0;

                    // Notification — full ↓ ↑ detail
                    String notifText = "↓ " + SpeedUtils.formatSpeed(deltaRx)
                            + "  ↑ " + SpeedUtils.formatSpeed(deltaTx);
                    if (notificationManager != null) {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(notifText));
                    }

                    // Overlay — clean total speed only
                    String speedText = SpeedUtils.formatSpeed(deltaRx + deltaTx);
                    if (dockOverlay != null) {
                        dockOverlay.setText(speedText);
                    }

                    // Update home screen widget via SharedPrefs
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(SpeedWidget.KEY_LAST_SPEED, speedText)
                            .apply();
                    android.appwidget.AppWidgetManager mgr =
                            android.appwidget.AppWidgetManager.getInstance(SpeedService.this);
                    int[] ids = mgr.getAppWidgetIds(
                            new android.content.ComponentName(SpeedService.this, SpeedWidget.class));
                    for (int id : ids) SpeedWidget.updateWidget(SpeedService.this, mgr, id);

                    checkSlowSpeedAlert(deltaRx + deltaTx);

                    lastRxBytes = rx;
                    lastTxBytes = tx;
                }

                SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                long interval = p.getInt(SettingsActivity.KEY_UPDATE_INTERVAL, 1000);
                handler.postDelayed(this, interval);
            }
        }, intervalMs);
    }

    // ── Slow Speed Alert ──────────────────────────────────────────────────────
    private void checkSlowSpeedAlert(long totalBytesPerSec) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long threshold = prefs.getLong(SettingsActivity.KEY_SLOW_THRESHOLD, 0);
        if (threshold <= 0) { slowCount = 0; alertFired = false; return; }

        if (totalBytesPerSec < threshold) {
            slowCount++;
            if (slowCount >= 3 && !alertFired) {
                alertFired = true;
                fireSlowSpeedAlert();
            }
        } else {
            slowCount = 0;
            alertFired = false;
        }
    }

    private void fireSlowSpeedAlert() {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 10, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification alert = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(getString(R.string.slow_speed_notif_title))
                .setContentText(getString(R.string.slow_speed_notif_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(ALERT_NOTIF_ID, alert);
        }
    }

    // ── Notification Builder ──────────────────────────────────────────────────
    private Notification buildNotification(String speedText) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(speedText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
