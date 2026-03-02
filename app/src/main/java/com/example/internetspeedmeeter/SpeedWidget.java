package com.example.internetspeedmeeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * Home Screen Widget — shows live download speed.
 * Reads last speed value written to SharedPrefs by SpeedService.
 * Updated every 60 seconds by the OS (via appwidget metadata).
 */
public class SpeedWidget extends AppWidgetProvider {

    static final String PREFS_NAME     = "SpeedPrefs";
    static final String KEY_LAST_SPEED = "widget_last_speed_text";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager mgr, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String speed = prefs.getString(KEY_LAST_SPEED, "0 KB/s");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_speed);
        views.setTextViewText(R.id.widgetSpeed, speed);
        mgr.updateAppWidget(widgetId, views);
    }
}
