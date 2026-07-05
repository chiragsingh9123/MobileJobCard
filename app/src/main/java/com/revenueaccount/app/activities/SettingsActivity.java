package com.revenueaccount.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.BottomNavHelper;
import com.revenueaccount.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 12: Settings ("More" tab) — profile, subscription, saare real menu items, logout */
public class SettingsActivity extends AppCompatActivity {

    /** icon drawable, label, target activity class */
    private final Object[][] MENU = {
        {R.drawable.ic_store, "Shop Profile", ShopProfileActivity.class},
        {R.drawable.ic_people, "Staff Management", StaffActivity.class},
        {R.drawable.ic_key, "Roles and Permissions", RolesInfoActivity.class},
        {R.drawable.ic_receipt, "Khata / Udhaar Ledger", KhataActivity.class},
        {R.drawable.ic_print, "Print Settings", PrintSettingsActivity.class},
        {R.drawable.ic_chat, "Message Templates", TemplatesActivity.class},
        {R.drawable.ic_notification, "Notification Settings", NotificationSettingsActivity.class},
        {R.drawable.ic_backup_cloud, "Backup and Restore", BackupActivity.class},
        {R.drawable.ic_security, "Security", ChangePasswordActivity.class},
        {R.drawable.ic_history, "Activity Log", ActivityLogActivity.class},
        {R.drawable.ic_privacy, "Privacy Policy", PrivacyPolicyActivity.class},
        {R.drawable.ic_developer, "Developer Info", DeveloperInfoActivity.class},
        {R.drawable.ic_info, "About App", AboutActivity.class}};

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        session = new SessionManager(this);

        String name = session.getName();
        ((TextView) findViewById(R.id.tvAvatar)).setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        ((TextView) findViewById(R.id.tvName)).setText(name);
        ((TextView) findViewById(R.id.tvShop)).setText(session.getShopName());
        ((TextView) findViewById(R.id.tvMobile)).setText(session.getMobile());

        findViewById(R.id.btnBuyPlan).setOnClickListener(v ->
        startActivity(new Intent(this, SubscriptionActivity.class)));
        findViewById(R.id.btnRedeem).setOnClickListener(v ->
        startActivity(new Intent(this, RedeemVoucherActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        buildMenu();
        BottomNavHelper.setup(this, R.id.nav_more);
    }

    @Override
    protected void onResume() { super.onResume(); loadSubscription(); }

    private void loadSubscription() {
        ApiClient.get(this).me().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                try {
                    if (res.isSuccessful() && res.body() != null
                    && res.body().has("subscription") && !res.body().get("subscription").isJsonNull()) {
                        JsonObject sub = res.body().getAsJsonObject("subscription");
                        String plan = sub.getAsJsonObject("plan").get("name").getAsString();
                        int days = sub.get("days_remaining").getAsInt();
                        ((TextView) findViewById(R.id.tvPlan)).setText(plan + " Plan");
                        TextView tvDays = findViewById(R.id.tvDaysLeft);
                        if (days <= 0) {
                            tvDays.setText("Expired - please renew your plan");
                            tvDays.setTextColor(getColor(R.color.error));
                        } else {
                            tvDays.setText(days + " days remaining");
                            tvDays.setTextColor(getColor(days <= 3 ? R.color.error : R.color.text_secondary));
                        }
                    } else {
                        ((TextView) findViewById(R.id.tvPlan)).setText("No active plan");
                        ((TextView) findViewById(R.id.tvDaysLeft)).setText("Purchase a plan or redeem a voucher");
                    }
                } catch (Exception e) {
                    android.util.Log.e("SettingsActivity", "Subscription render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void buildMenu() {
        LinearLayout container = findViewById(R.id.menuContainer);
        container.removeAllViews();
        boolean isOwner = session.isOwner();
        for (int i = 0; i < MENU.length; i++) {
            int icon = (int) MENU[i][0];
            String label = (String) MENU[i][1];
            Class<?> target = (Class<?>) MENU[i][2];

            // Staff Management and Roles are visible to the Owner only
            boolean ownerOnly = target == StaffActivity.class || target == RolesInfoActivity.class
            || target == ShopProfileActivity.class;
            if (ownerOnly && !isOwner) continue;

            View row = LayoutInflater.from(this).inflate(R.layout.item_menu_row, container, false);
            ((ImageView) row.findViewById(R.id.ivIcon)).setImageResource(icon);
            ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
            row.setOnClickListener(v -> startActivity(new Intent(this, target)));
            container.addView(row);
            if (i < MENU.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
                divider.setBackgroundColor(getColor(R.color.divider));
                container.addView(divider);
            }
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
        .setTitle("Log Out")
        .setMessage("You will need to sign in again to access your account. Do you want to continue?")
        .setPositiveButton("Log Out", (d, w) -> {
            session.clear();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        })
        .setNegativeButton("Cancel", null)
        .show();
    }
}
