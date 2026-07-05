package com.revenueaccount.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Notification Settings — local preferences (SharedPreferences me store) */
public class NotificationPrefs {
    private static final String PREFS = "notification_prefs";
    private final SharedPreferences prefs;

    public NotificationPrefs(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean lowStockAlert() { return prefs.getBoolean("low_stock", true); }
    public boolean khataAlert() { return prefs.getBoolean("khata", true); }
    public boolean subscriptionAlert() { return prefs.getBoolean("subscription", true); }

    public void setLowStockAlert(boolean v) { prefs.edit().putBoolean("low_stock", v).apply(); }
    public void setKhataAlert(boolean v) { prefs.edit().putBoolean("khata", v).apply(); }
    public void setSubscriptionAlert(boolean v) { prefs.edit().putBoolean("subscription", v).apply(); }
}
