package com.revenueaccount.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.revenueaccount.app.R;
import com.revenueaccount.app.utils.NotificationHelper;
import com.revenueaccount.app.utils.NotificationPrefs;

/** Notification Settings — real local alerts on/off (low stock, khata, subscription) */
public class NotificationSettingsActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_REQUEST = 601;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        NotificationPrefs prefs = new NotificationPrefs(this);
        SwitchMaterial swLowStock = findViewById(R.id.swLowStock);
        SwitchMaterial swKhata = findViewById(R.id.swKhata);
        SwitchMaterial swSubscription = findViewById(R.id.swSubscription);

        swLowStock.setChecked(prefs.lowStockAlert());
        swKhata.setChecked(prefs.khataAlert());
        swSubscription.setChecked(prefs.subscriptionAlert());

        swLowStock.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setLowStockAlert(checked);
            if (checked) requestNotifPermissionIfNeeded();
        });
        swKhata.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setKhataAlert(checked);
            if (checked) requestNotifPermissionIfNeeded();
        });
        swSubscription.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setSubscriptionAlert(checked);
            if (checked) requestNotifPermissionIfNeeded();
        });

        NotificationHelper.createChannel(this);
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_REQUEST);
        }
    }
}
