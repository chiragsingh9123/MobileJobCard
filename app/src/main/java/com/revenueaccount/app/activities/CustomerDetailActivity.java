package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Customer profile: jobs history + khata ledger + settle payment */
public class CustomerDetailActivity extends AppCompatActivity {

    private long customerId;
    private String mobile = "";
    private double khataBalance = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);
        customerId = getIntent().getLongExtra("customer_id", -1);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCall).setOnClickListener(v ->
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + mobile))));
        findViewById(R.id.btnSettle).setOnClickListener(v -> settleDialog());
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        ApiClient.get(this).customerHistory(customerId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        render(res.body());
                    } catch (Exception e) {
                        android.util.Log.e("CustomerDetailActivity", "Render error", e);
                        AppToast.show(CustomerDetailActivity.this, "Data dikhane me dikkat");
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(CustomerDetailActivity.this, "Network error");
            }
        });
    }

    private void render(JsonObject data) {
        JsonObject c = data.getAsJsonObject("customer");
        mobile = c.get("mobile").getAsString();
        khataBalance = c.get("khata_balance").getAsDouble();
        String name = c.get("name").getAsString();
        ((TextView) findViewById(R.id.tvAvatar))
        .setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        ((TextView) findViewById(R.id.tvName)).setText(name);
        ((TextView) findViewById(R.id.tvInfo)).setText(mobile
        + " • " + c.get("customer_type").getAsString()
        + "\nTotal Jobs: " + c.get("total_jobs").getAsInt()
        + " • Total Spent: ₹" + c.get("total_spent").getAsDouble());

        TextView tvKhata = findViewById(R.id.tvKhata);
        View btnSettle = findViewById(R.id.btnSettle);
        if (khataBalance > 0) {
            tvKhata.setText(" Khata Baaki: ₹" + khataBalance);
            btnSettle.setVisibility(View.VISIBLE);
        } else {
            tvKhata.setText("No outstanding balance");
            tvKhata.setTextColor(Color.parseColor("#2A6B45"));
            btnSettle.setVisibility(View.GONE);
        }

        LinearLayout jobs = findViewById(R.id.jobsContainer);
        jobs.removeAllViews();
        JsonArray jArr = data.getAsJsonArray("jobs");
        if (jArr.size() == 0) addLine(jobs, "Koi job nahi", "#757575");
        for (int i = 0; i < jArr.size(); i++) {
            JsonObject j = jArr.get(i).getAsJsonObject();
            addLine(jobs, j.get("job_id").getAsString() + " - "
            + j.get("device_model").getAsString()
            + " - Rs. " + j.get("estimated_cost").getAsDouble()
            + " - " + j.get("status").getAsString(), "#212121");
        }

        LinearLayout khata = findViewById(R.id.khataContainer);
        khata.removeAllViews();
        JsonArray kArr = data.getAsJsonArray("khata");
        if (kArr.size() == 0) addLine(khata, "Koi khata entry nahi", "#757575");
        for (int i = 0; i < kArr.size(); i++) {
            JsonObject e = kArr.get(i).getAsJsonObject();
            boolean debit = e.get("entry_type").getAsString().equals("DEBIT");
            addLine(khata, (debit ? " Udhaar +₹" : " Jama -₹") + e.get("amount").getAsDouble()
            + " " + e.get("note").getAsString(), debit ? "#A83E3E" : "#2A6B45");
        }
    }

    private void addLine(LinearLayout parent, String text, String color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(Color.parseColor(color));
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(32, 26, 32, 26);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void settleDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Kitna payment mila? (Baaki: ₹" + khataBalance + ")");
        new AlertDialog.Builder(this)
        .setTitle(" Khata Payment")
        .setView(input)
        .setPositiveButton("Save", (d, w) -> {
            double amt = com.revenueaccount.app.utils.NumUtils.parseDouble(input.getText().toString());
            if (amt <= 0) {
                AppToast.show(this, "Enter a valid amount");
                return;
            }
            JsonObject p = new JsonObject();
            p.addProperty("amount", amt);
            p.addProperty("customer_id", customerId);
            p.addProperty("payment_type", "KHATA_SETTLE");
            p.addProperty("method", "CASH");
            ApiClient.get(this).createPayment(p).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    AppToast.show(CustomerDetailActivity.this, "Payment saved");
                    load();
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    AppToast.show(CustomerDetailActivity.this, "Network error");
                }
            });
        })
        .setNegativeButton("Cancel", null)
        .show();
    }
}
