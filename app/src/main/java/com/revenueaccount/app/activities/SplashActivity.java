//package com.revenueaccount.app.activities;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.os.Handler;
//import androidx.appcompat.app.AppCompatActivity;
//import com.revenueaccount.app.R;
//import com.revenueaccount.app.utils.SessionManager;
//
//public class SplashActivity extends AppCompatActivity {
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_splash);
//        new Handler().postDelayed(() -> {
//            SessionManager session = new SessionManager(this);
//            Intent i = session.isLoggedIn()
//            ? new Intent(this, DashboardActivity.class)
//            : new Intent(this, LoginActivity.class);
//            startActivity(i);
//            finish();
//        }, 1500);
//    }
//}
package com.revenueaccount.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.BuildConfig;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Screen 1: Splash — thoda dikhne ke baad version-check karta hai.
 * Agar force-update ON hai aur app purani hai, to UpdateRequiredActivity par bhej deta hai.
 * Warna login-state check karke Dashboard ya Login par navigate karta hai.
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

        // Splash kam se kam itni der dikhe (branding ke liye), chahe version-check
        // turant hi wapas aa jaaye
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            delayDone = true;
            proceedIfReady();
        }, MIN_SPLASH_DELAY_MS);

        checkAppVersion();
    }

    /**
     * Backend se check karta hai ki app ka current version chalega ya update zaroori hai.
     * Agar server se koi response na aaye (network error, server down, etc.), to bhi
     * user ko block nahi karte - normal flow continue hota hai. Yeh ek soft-fail design hai
     * taaki backend down hone par bhi purane users app use kar sakein.
     */
    private void checkAppVersion() {
        ApiClient.get(this).versionCheck(BuildConfig.VERSION_CODE).enqueue(new Callback<JsonObject>() {
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
                    // parse fail ho to bhi block mat karo
                }
                versionCheckDone = true;
                proceedIfReady();
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Version check failed", t);
                // Network/server issue - user ko block mat karo, normal flow continue karo
                versionCheckDone = true;
                proceedIfReady();
            }
        });
    }

    /** Dono conditions (min delay + version check) poori hone par hi aage badho */
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

    /** App ke normal entry flow - login state ke hisaab se Dashboard ya Login */
    private void goToNextScreen() {
        SessionManager session = new SessionManager(this);
        Class<?> target = session.isLoggedIn() ? DashboardActivity.class : LoginActivity.class;
        startActivity(new Intent(this, target));
    }
}