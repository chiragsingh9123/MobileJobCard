package com.revenueaccount.app.utils;

import android.app.Activity;
import android.content.Intent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.revenueaccount.app.R;
import com.revenueaccount.app.activities.CustomersActivity;
import com.revenueaccount.app.activities.DashboardActivity;
import com.revenueaccount.app.activities.JobsActivity;
import com.revenueaccount.app.activities.ReportsActivity;
import com.revenueaccount.app.activities.SettingsActivity;

/** Har screen ke bottom navigation ka common handler */
public class BottomNavHelper {

    public static void setup(Activity activity, int selectedItemId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) return;
        nav.setSelectedItemId(selectedItemId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;
            Class<?> target = null;
            if (id == R.id.nav_home) target = DashboardActivity.class;
            else if (id == R.id.nav_jobs) target = JobsActivity.class;
            else if (id == R.id.nav_customers) target = CustomersActivity.class;
            else if (id == R.id.nav_reports) target = ReportsActivity.class;
            else if (id == R.id.nav_more) target = SettingsActivity.class;
            if (target != null) {
                Intent i = new Intent(activity, target);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(i);
                activity.overridePendingTransition(0, 0);
            }
            return true;
        });
    }
}
