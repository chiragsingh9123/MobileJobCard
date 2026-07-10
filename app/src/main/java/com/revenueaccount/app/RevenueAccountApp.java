package com.revenueaccount.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import com.revenueaccount.app.activities.DashboardActivity;
import com.revenueaccount.app.utils.SessionManager;

/**
 * SAFETY NET: agar kahin bhi koi unexpected crash ho, to poora app force-close
 * hokar "App has stopped" dikhane ke bajaye, hum use pakad kar Dashboard par
 * wapas bhej dete hain. Isse user ka kaam kabhi achanak band nahi hota.
 */
public class RevenueAccountApp extends Application {

    private static final String TAG = "RevenueAccountApp";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception — recovering gracefully", throwable);
            try {
                boolean loggedIn = new SessionManager(getApplicationContext()).isLoggedIn();
                Intent intent = new Intent(getApplicationContext(),
                        loggedIn ? DashboardActivity.class
                                : com.revenueaccount.app.activities.LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } catch (Throwable ignored) {
                // agar recovery bhi fail ho jaye, to default handler ko chalne dein
                if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
                return;
            }
            // App process ko restart karne ke liye khatam karo (crash dialog nahi dikhega)
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });

        // FIX: keyboard covering the focused input field. Setting this per-Activity
        // in the manifest would mean remembering it on every one of ~34 activities
        // (and every new one added later); doing it once here, for every Activity
        // as it's created, is the reliable way to guarantee it's never missed.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}