package com.revenueaccount.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Login session + tokens SharedPreferences me store */
public class SessionManager {
    private static final String PREFS = "revenue_account_session";
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveTokens(String access, String refresh) {
        prefs.edit().putString("access", access).putString("refresh", refresh).apply();
    }

    public void saveUser(String name, String mobile, String shopName) {
        prefs.edit().putString("name", name).putString("mobile", mobile)
        .putString("shop", shopName).apply();
    }

    /** Role bhi save karo — role-based UI ke liye (OWNER sab dekh sakta hai, STAFF limited) */
    public void saveRole(String role) {
        prefs.edit().putString("role", role).apply();
    }

    public String getAccessToken() { return prefs.getString("access", null); }
    public String getName() { return prefs.getString("name", ""); }
    public String getUserName() { return getName(); }
    public String getMobile() { return prefs.getString("mobile", ""); }
    public String getShopName() { return prefs.getString("shop", ""); }
    public String getRole() { return prefs.getString("role", "OWNER"); }
    public boolean isOwner() { return "OWNER".equals(getRole()); }
    public boolean isStaff() { return "STAFF".equals(getRole()); }
    public boolean isLoggedIn() { return getAccessToken() != null; }
    public void logout() { prefs.edit().clear().apply(); }
    public void clear() { prefs.edit().clear().apply(); }
}
