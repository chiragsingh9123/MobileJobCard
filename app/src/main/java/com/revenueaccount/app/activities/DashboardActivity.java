package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.BottomNavHelper;
import com.revenueaccount.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 4: Dashboard — quick actions + live stats (static layout, no runtime view-building) */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private SwipeRefreshLayout swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        SessionManager session = new SessionManager(this);
        String name = session.getName();
        ((TextView) findViewById(R.id.tvGreeting)).setText("Hello, " + (name.isEmpty() ? "Owner" : name));
        ((TextView) findViewById(R.id.tvShopName)).setText(session.getShopName());
        ((TextView) findViewById(R.id.tvAvatar)).setText(name.isEmpty() ? "R" : name.substring(0, 1).toUpperCase());

        findViewById(R.id.btnNotification).setOnClickListener(v ->
        AppToast.show(this, "Koi nayi notification nahi"));

        setupTile(R.id.tileNewJob, R.drawable.ic_add, "New Job Card", "#1565C0", NewJobCardActivity.class);
        setupTile(R.id.tileCustomers, R.drawable.ic_people, "Customers", "#4CAF50", CustomersActivity.class);
        setupTile(R.id.tileJobs, R.drawable.ic_wrench, "Repair Jobs", "#FF9800", JobsActivity.class);
        setupTile(R.id.tileCollection, R.drawable.ic_rupee, "Collection", "#7B1FA2", KhataActivity.class);
        setupTile(R.id.tileInventory, R.drawable.ic_box, "Inventory", "#00897B", InventoryActivity.class);
        setupTile(R.id.tileReports, R.drawable.ic_chart, "Reports", "#F44336", ReportsActivity.class);

        setupStatLabels();

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadStats);

        BottomNavHelper.setup(this, R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    /** Static tile: icon + color + label + click target — kabhi crash nahi karta */
    private void setupTile(int includeId, int iconRes, String label, String colorHex, Class<?> target) {
        View tile = findViewById(includeId);
        if (tile == null) return;
        ((ImageView) tile.findViewById(R.id.tileIcon)).setImageResource(iconRes);
        ((TextView) tile.findViewById(R.id.tileLabel)).setText(label);
        ((MaterialCardView) tile).setCardBackgroundColor(Color.parseColor(colorHex));
        tile.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    private void setupStatLabels() {
        setStatLabel(R.id.statTodaysJobs, "Today's Jobs");
        setStatLabel(R.id.statRepairing, "Repairing");
        setStatLabel(R.id.statCompleted, "Completed");
        setStatLabel(R.id.statDelivered, "Delivered");
        setStatLabel(R.id.statIncome, "Today's Income");
        setStatLabel(R.id.statPending, "Pending Khata");
        setStatLabel(R.id.statCustomers, "Total Customers");
        setStatLabel(R.id.statLowStock, "Low Stock Items");
    }

    private void setStatLabel(int includeId, String label) {
        View card = findViewById(includeId);
        if (card == null) return;
        ((TextView) card.findViewById(R.id.statLabel)).setText(label);
    }

    private void setStatValue(int includeId, String value) {
        View card = findViewById(includeId);
        if (card == null) return;
        ((TextView) card.findViewById(R.id.statValue)).setText(value);
    }

    private void loadStats() {
        swipe.setRefreshing(true);
        ApiClient.get(this).dashboard().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (!res.isSuccessful() || res.body() == null) return;
                // Defensive try-catch: agar server ka response unexpected ho to bhi app crash nahi hoga
                try {
                    JsonObject d = res.body();
                    setStatValue(R.id.statTodaysJobs, safeStr(d, "todays_jobs"));
                    setStatValue(R.id.statRepairing, safeStr(d, "repairing"));
                    setStatValue(R.id.statCompleted, safeStr(d, "completed"));
                    setStatValue(R.id.statDelivered, safeStr(d, "delivered_today"));
                    setStatValue(R.id.statIncome, "₹" + safeStr(d, "todays_income"));
                    setStatValue(R.id.statPending, "₹" + safeStr(d, "pending_amount"));
                    setStatValue(R.id.statCustomers, safeStr(d, "total_customers"));
                    setStatValue(R.id.statLowStock, safeStr(d, "low_stock_items"));
                    checkAlerts(d);
                } catch (Exception e) {
                    Log.e(TAG, "Dashboard render error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(DashboardActivity.this, "Server se connect nahi hua");
            }
        });
    }

    private String safeStr(JsonObject d, String key) {
        return (d.has(key) && !d.get(key).isJsonNull()) ? d.get(key).getAsString() : "0";
    }

    /** Notification Settings me jo alerts ON hain, unke conditions check karke local notification dikhao */
    private void checkAlerts(JsonObject d) {
        com.revenueaccount.app.utils.NotificationPrefs prefs =
        new com.revenueaccount.app.utils.NotificationPrefs(this);
        com.revenueaccount.app.utils.NotificationHelper.createChannel(this);

        if (prefs.lowStockAlert()) {
            int low = d.has("low_stock_items") ? d.get("low_stock_items").getAsInt() : 0;
            if (low > 0) {
                com.revenueaccount.app.utils.NotificationHelper.show(this, 1001,
                " Low Stock Alert", low + " product(s) low ya out of stock hain");
            }
        }
        if (prefs.khataAlert()) {
            double pending = d.has("pending_amount") ? d.get("pending_amount").getAsDouble() : 0;
            if (pending > 0) {
                com.revenueaccount.app.utils.NotificationHelper.show(this, 1002,
                " Khata Pending", "₹" + pending + " customers ke khate me baaki hai");
            }
        }
    }
}
