package com.revenueaccount.app.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Screen 1: Splash screen.
 * Performs a version check against the server. If the shop owner has enabled
 * force-update in the admin panel and this app build is older than the required
 * version, the user is sent to UpdateRequiredActivity and blocked from proceeding.
 * If the server is unreachable, the app does not block the user - it simply
 * continues to the normal Login/Dashboard flow, so a temporary network issue
 * never locks anyone out of the app.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_DELAY_MS = 1200;

    private boolean delayDone = false;
    private boolean versionCheckDone = false;
    private boolean updateRequired = false;
    private String updateMessage = "";
    private String updateStoreUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            delayDone = true;
            proceedIfReady();
        }, MIN_SPLASH_DELAY_MS);

        checkAppVersion();
    }

    private int getCurrentVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    private void checkAppVersion() {
        ApiClient.get(this).versionCheck(getCurrentVersionCode()).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                try {
                    if (res.isSuccessful() && res.body() != null
                            && res.body().has("update_required")
                            && res.body().get("update_required").getAsBoolean()) {
                        updateRequired = true;
                        updateMessage = res.body().has("message")
                                ? res.body().get("message").getAsString()
                                : "A new version is available. Please update to continue.";
                        updateStoreUrl = res.body().has("play_store_url")
                                ? res.body().get("play_store_url").getAsString() : "";
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Version check parse error", e);
                }
                versionCheckDone = true;
                proceedIfReady();
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Version check failed", t);
                versionCheckDone = true;
                proceedIfReady();
            }
        });
    }

    private synchronized void proceedIfReady() {
        if (!delayDone || !versionCheckDone) return;
        if (isFinishing()) return;

        if (updateRequired) {
            Intent i = new Intent(this, UpdateRequiredActivity.class);
            i.putExtra("message", updateMessage);
            i.putExtra("store_url", updateStoreUrl);
            startActivity(i);
        } else {
            goToNextScreen();
        }
        finish();
    }

    private void goToNextScreen() {
        SessionManager session = new SessionManager(this);
        Class<?> target;
        if (session.isLoggedIn()) {
            target = DashboardActivity.class;
        } else if (!session.hasSeenOnboarding()) {
            target = WelcomeActivity.class;
        } else {
            target = LoginActivity.class;
        }
        startActivity(new Intent(this, target));
    }
}