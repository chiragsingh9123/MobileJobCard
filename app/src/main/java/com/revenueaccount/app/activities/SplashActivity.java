package com.revenueaccount.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;
import com.revenueaccount.app.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(() -> {
            SessionManager session = new SessionManager(this);
            Intent i = session.isLoggedIn()
            ? new Intent(this, DashboardActivity.class)
            : new Intent(this, LoginActivity.class);
            startActivity(i);
            finish();
        }, 1500);
    }
}
