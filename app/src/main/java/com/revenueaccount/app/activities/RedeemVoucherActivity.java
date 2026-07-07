package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** SUBSCRIPTION: Admin ka diya voucher code redeem karke plan activate karo */
public class RedeemVoucherActivity extends AppCompatActivity {

    private static final String TAG = "RedeemVoucherActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redeem_voucher);

        TextView tvCurrentPlan = findViewById(R.id.tvCurrentPlan);
        EditText etCode = findViewById(R.id.etVoucherCode);
        Button btnRedeem = findViewById(R.id.btnRedeem);

        // Current subscription dikhao
        ApiClient.get(this).me().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                try {
                    if (res.isSuccessful() && res.body() != null
                    && res.body().has("subscription") && !res.body().get("subscription").isJsonNull()) {
                        JsonObject sub = res.body().getAsJsonObject("subscription");
                        String plan = sub.getAsJsonObject("plan").get("name").getAsString();
                        int days = sub.get("days_remaining").getAsInt();
                        tvCurrentPlan.setText("Current Plan: " + plan + "\n" + days + " days remaining");
                    } else {
                        tvCurrentPlan.setText("No active plan");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Plan render error", e);
                    tvCurrentPlan.setText(" Plan dikhane me dikkat aayi");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                tvCurrentPlan.setText("Could not connect to the server");
            }
        });

        btnRedeem.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim().toUpperCase();
            if (code.isEmpty()) { etCode.setError("Voucher code daalein"); return; }

            btnRedeem.setEnabled(false);
            Map<String, String> body = new HashMap<>();
            body.put("code", code);
            ApiClient.get(this).redeemVoucher(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    btnRedeem.setEnabled(true);
                    if (res.isSuccessful() && res.body() != null && res.body().has("message")) {
                        AppToast.show(RedeemVoucherActivity.this, res.body().get("message").getAsString());
                        finish();
                    } else {
                        AppToast.show(RedeemVoucherActivity.this, "This voucher is invalid or has already been used");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    btnRedeem.setEnabled(true);
                    AppToast.show(RedeemVoucherActivity.this, "Network error");
                }
            });
        });
    }
}
